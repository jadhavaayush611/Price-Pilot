import unittest
from unittest.mock import patch, MagicMock
import http.server
import threading
import time
import json
import socket

from app.assistant.providers.gemini import GeminiProvider, HttpResponse
from app.assistant.providers.mock import MockFixtureProvider
from app.assistant.providers.factory import ProviderFactory
from app.assistant.providers.contracts import (
    LlmRole,
    LlmMessage,
    LlmGenerationConfig,
    LlmCompletionRequest,
    LlmCompletionResponse
)
from app.assistant.providers.exceptions import (
    ProviderError,
    ProviderAuthError,
    ProviderRateLimitError,
    ProviderTimeoutError,
    ProviderInvalidRequestError,
    ProviderContentFilterError,
    ProviderUnavailableError,
    StructuredOutputValidationError
)
from app.assistant.providers.schemas import (
    BuyConfidenceSchema,
    ProductComparisonItem,
    ProductComparisonSchema
)


class MockUpstreamHttpServer:
    """Local controlled HTTP server for deterministic cumulative deadline verification."""

    def __init__(self):
        self.server = None
        self.port = None
        self.thread = None
        self.active_connections = 0
        self.lock = threading.Lock()

    def start(self):
        parent = self

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                with parent.lock:
                    parent.active_connections += 1
                try:
                    if self.path.endswith(":generateContent") or self.path == "/normal":
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.end_headers()
                        res = {
                            "candidates": [
                                {
                                    "content": {
                                        "parts": [{"text": "Normal fast AI response"}],
                                        "role": "model"
                                    },
                                    "finishReason": "STOP"
                                }
                            ],
                            "usageMetadata": {"promptTokenCount": 10, "candidatesTokenCount": 5, "totalTokenCount": 15}
                        }
                        self.wfile.write(json.dumps(res).encode("utf-8"))

                    elif self.path == "/silent":
                        # Deliberately sleep without sending headers to simulate hanging upstream
                        time.sleep(3.0)
                        try:
                            self.send_response(200)
                            self.end_headers()
                        except Exception:
                            pass

                    elif self.path == "/trickle":
                        # Sends headers immediately, then trickles body chunks slowly
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.end_headers()
                        # Trickle 20 bytes with 200ms pauses = 4.0s total stream duration
                        for _ in range(20):
                            try:
                                self.wfile.write(b"x")
                                self.wfile.flush()
                                time.sleep(0.2)
                            except Exception:
                                break

                    elif self.path == "/slow_body":
                        # Sends headers, sends initial chunk, then pauses for 3 seconds before next chunk
                        self.send_response(200)
                        self.send_header("Content-Type", "application/json")
                        self.end_headers()
                        try:
                            self.wfile.write(b'{"candidates": [')
                            self.wfile.flush()
                            time.sleep(3.0)
                            self.wfile.write(b']}')
                        except Exception:
                            pass
                    else:
                        self.send_response(404)
                        self.end_headers()
                finally:
                    with parent.lock:
                        parent.active_connections -= 1

            def log_message(self, *args):
                pass  # Suppress console logging during tests

        self.server = http.server.HTTPServer(("127.0.0.1", 0), Handler)
        self.port = self.server.server_port
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def stop(self):
        if self.server:
            self.server.shutdown()
            self.server.server_close()


class TestCumulativeTimeoutWithRealSocketServer(unittest.TestCase):
    """Deterministic verification of the 3.5s cumulative deadline using a controlled local socket server."""

    @classmethod
    def setUpClass(cls):
        cls.server = MockUpstreamHttpServer()
        cls.server.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.stop()

    def test_1_normal_response_succeeds_quickly(self):
        """Verifies normal response completes successfully well within the deadline."""
        provider = GeminiProvider(
            api_key="valid-key",
            primary_model="gemini-3.8-flash",
            timeout_seconds=1.0,
            base_url=f"http://127.0.0.1:{self.server.port}"
        )
        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Hello")])
        start = time.monotonic()
        res = provider.generate_text(req)
        elapsed = time.monotonic() - start

        self.assertEqual(res.content, "Normal fast AI response")
        self.assertLess(elapsed, 0.5)

    def test_2_silent_server_times_out_at_deadline(self):
        """Verifies silent server (hanging on headers) times out exactly at the configured budget."""
        provider = GeminiProvider(
            api_key="valid-key",
            primary_model="gemini-3.8-flash",
            timeout_seconds=0.6,
            base_url=f"http://127.0.0.1:{self.server.port}"
        )
        provider._base_url = f"http://127.0.0.1:{self.server.port}"
        # Override endpoint path in execute_http_call to hit /silent
        start = time.monotonic()
        with self.assertRaises(ProviderTimeoutError) as ctx:
            provider._execute_http_call(f"http://127.0.0.1:{self.server.port}/silent", payload={})
        elapsed = time.monotonic() - start

        self.assertEqual(ctx.exception.status_code, 504)
        self.assertGreaterEqual(elapsed, 0.55)
        self.assertLessEqual(elapsed, 0.90)

    def test_3_slow_trickle_server_interrupted_at_cumulative_deadline(self):
        """Verifies slow-trickle server (which circumvents read inactivity timeouts)

        is strictly interrupted at the cumulative deadline.
        """
        provider = GeminiProvider(
            api_key="valid-key",
            primary_model="gemini-3.8-flash",
            timeout_seconds=0.7,
            base_url=f"http://127.0.0.1:{self.server.port}"
        )
        start = time.monotonic()
        with self.assertRaises(ProviderTimeoutError) as ctx:
            provider._execute_http_call(f"http://127.0.0.1:{self.server.port}/trickle", payload={})
        elapsed = time.monotonic() - start

        self.assertEqual(ctx.exception.status_code, 504)
        # Without cumulative timeout, /trickle takes 4.0s. It MUST terminate around 0.7s.
        self.assertGreaterEqual(elapsed, 0.65)
        self.assertLess(elapsed, 1.20)
        self.assertIn("timed out", str(ctx.exception).lower())

    def test_4_response_body_consumption_included_in_deadline(self):
        """Verifies deadline covers body consumption when server stalls midway through the body."""
        provider = GeminiProvider(
            api_key="valid-key",
            primary_model="gemini-3.8-flash",
            timeout_seconds=0.5,
            base_url=f"http://127.0.0.1:{self.server.port}"
        )
        start = time.monotonic()
        with self.assertRaises(ProviderTimeoutError) as ctx:
            provider._execute_http_call(f"http://127.0.0.1:{self.server.port}/slow_body", payload={})
        elapsed = time.monotonic() - start

        self.assertEqual(ctx.exception.status_code, 504)
        self.assertGreaterEqual(elapsed, 0.45)
        self.assertLess(elapsed, 0.95)

    def test_5_timeout_normalized_as_provider_timeout_error(self):
        """Verifies all timeout events are normalized into ProviderTimeoutError with status 504 and elapsed seconds."""
        provider = GeminiProvider(
            api_key="valid-key",
            primary_model="gemini-3.8-flash",
            timeout_seconds=0.4,
            base_url=f"http://127.0.0.1:{self.server.port}"
        )
        with self.assertRaises(ProviderTimeoutError) as ctx:
            provider._execute_http_call(f"http://127.0.0.1:{self.server.port}/silent", payload={})
        err = ctx.exception
        self.assertIsInstance(err, ProviderTimeoutError)
        self.assertEqual(err.status_code, 504)
        self.assertEqual(err.provider, "gemini")
        self.assertGreater(err.elapsed_seconds, 0.3)

    def test_6_no_background_request_continues_after_timeout(self):
        """Verifies connection socket is immediately closed on timeout and no client background threads are leaked."""
        provider = GeminiProvider(
            api_key="valid-key",
            primary_model="gemini-3.8-flash",
            timeout_seconds=0.3,
            base_url=f"http://127.0.0.1:{self.server.port}"
        )
        before_threads = threading.active_count()
        with self.assertRaises(ProviderTimeoutError):
            provider._execute_http_call(f"http://127.0.0.1:{self.server.port}/trickle", payload={})

        after_threads = threading.active_count()
        # Verify no background request threads or executors continue running after timeout
        self.assertEqual(before_threads, after_threads)


class TestStructuredOutputVerification(unittest.TestCase):
    """Detailed verification for BuyConfidenceSchema and ProductComparisonSchema."""

    def setUp(self):
        self.provider = GeminiProvider(
            api_key="test-key",
            primary_model="gemini-3.8-flash",
            fallback_model="gemini-2.5-flash",
            timeout_seconds=3.5
        )

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_buy_confidence_schema_success(self, mock_http):
        """Verifies BuyConfidenceSchema parsing and native JSON schema configuration."""
        mock_http.return_value = HttpResponse(
            status_code=200,
            headers={"Content-Type": "application/json"},
            body_bytes=json.dumps({
                "candidates": [
                    {
                        "content": {
                            "parts": [{"text": '{"score": 88, "reason": "Current price is 12% below 60-day moving average."}'}],
                            "role": "model"
                        },
                        "finishReason": "STOP"
                    }
                ],
                "usageMetadata": {"promptTokenCount": 20, "candidatesTokenCount": 15, "totalTokenCount": 35}
            }).encode("utf-8")
        )

        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Evaluate MacBook Air")])
        obj, res = self.provider.generate_structured(req, BuyConfidenceSchema)

        self.assertIsInstance(obj, BuyConfidenceSchema)
        self.assertEqual(obj.score, 88)
        self.assertEqual(obj.reason, "Current price is 12% below 60-day moving average.")
        self.assertEqual(res.structured_data["score"], 88)

        # Verify responseSchema was generated from Pydantic schema
        sent_payload = mock_http.call_args[0][1]
        self.assertEqual(sent_payload["generationConfig"]["responseMimeType"], "application/json")
        self.assertEqual(sent_payload["generationConfig"]["responseSchema"], BuyConfidenceSchema.model_json_schema())

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_buy_confidence_schema_invalid_rejected(self, mock_http):
        """Verifies invalid BuyConfidence output (score > 100 or missing reason) raises StructuredOutputValidationError."""
        mock_http.return_value = HttpResponse(
            status_code=200,
            headers={"Content-Type": "application/json"},
            body_bytes=json.dumps({
                "candidates": [
                    {
                        "content": {
                            "parts": [{"text": '{"score": 150}'}],  # Score > 100 and missing required 'reason'
                            "role": "model"
                        },
                        "finishReason": "STOP"
                    }
                ]
            }).encode("utf-8")
        )

        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Evaluate")])
        with self.assertRaises(StructuredOutputValidationError) as ctx:
            self.provider.generate_structured(req, BuyConfidenceSchema)

        self.assertEqual(ctx.exception.status_code, 502)
        self.assertEqual(ctx.exception.provider, "gemini")
        # Assert post was called exactly ONCE - no model fallback occurred on schema validation failure
        self.assertEqual(mock_http.call_count, 1)

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_product_comparison_schema_success(self, mock_http):
        """Verifies ProductComparisonSchema parsing with multiple products and summary."""
        comparison_payload = {
            "products": [
                {
                    "id": "prod-1",
                    "name": "MacBook Air M2",
                    "price": 89900.0,
                    "discount": 10.0,
                    "sellersCount": 3,
                    "score": 90.0,
                    "trend": "FALLING",
                    "analytics": {"rating": 4.8}
                },
                {
                    "id": "prod-2",
                    "name": "Dell XPS 13",
                    "price": 99900.0,
                    "discount": 5.0,
                    "sellersCount": 2,
                    "score": 85.0,
                    "trend": "STABLE",
                    "analytics": {"rating": 4.6}
                }
            ],
            "summary": "MacBook Air M2 offers superior value at ₹10,000 less with higher active discounts."
        }

        mock_http.return_value = HttpResponse(
            status_code=200,
            headers={"Content-Type": "application/json"},
            body_bytes=json.dumps({
                "candidates": [
                    {
                        "content": {
                            "parts": [{"text": json.dumps(comparison_payload)}],
                            "role": "model"
                        },
                        "finishReason": "STOP"
                    }
                ],
                "usageMetadata": {"promptTokenCount": 40, "candidatesTokenCount": 50, "totalTokenCount": 90}
            }).encode("utf-8")
        )

        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Compare MacBook and Dell XPS")])
        obj, res = self.provider.generate_structured(req, ProductComparisonSchema)

        self.assertIsInstance(obj, ProductComparisonSchema)
        self.assertEqual(len(obj.products), 2)
        self.assertEqual(obj.products[0].id, "prod-1")
        self.assertEqual(obj.products[0].name, "MacBook Air M2")
        self.assertEqual(obj.products[0].price, 89900.0)
        self.assertEqual(obj.products[1].id, "prod-2")
        self.assertIn("MacBook Air M2 offers superior value", obj.summary)

        # Verify responseSchema was generated from ProductComparisonSchema
        sent_payload = mock_http.call_args[0][1]
        self.assertEqual(sent_payload["generationConfig"]["responseMimeType"], "application/json")
        self.assertEqual(sent_payload["generationConfig"]["responseSchema"], ProductComparisonSchema.model_json_schema())

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_product_comparison_schema_invalid_rejected(self, mock_http):
        """Verifies incomplete ProductComparison output (missing required fields) raises StructuredOutputValidationError."""
        mock_http.return_value = HttpResponse(
            status_code=200,
            headers={"Content-Type": "application/json"},
            body_bytes=json.dumps({
                "candidates": [
                    {
                        "content": {
                            "parts": [{"text": '{"products": [{"name": "Only Name, Missing ID and Price"}]}'}],
                            "role": "model"
                        },
                        "finishReason": "STOP"
                    }
                ]
            }).encode("utf-8")
        )

        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Compare")])
        with self.assertRaises(StructuredOutputValidationError) as ctx:
            self.provider.generate_structured(req, ProductComparisonSchema)

        self.assertEqual(ctx.exception.status_code, 502)
        # Confirm no automatic fallback on schema validation error
        self.assertEqual(mock_http.call_count, 1)


class TestProviderIntegrationAndErrorNormalization(unittest.TestCase):
    """Verifies error normalization, authentication security, and zero-retry invariants."""

    def setUp(self):
        self.provider = GeminiProvider(
            api_key="test-gemini-key",
            primary_model="gemini-3.8-flash",
            fallback_model="gemini-2.5-flash",
            timeout_seconds=3.5
        )

    def test_header_authentication_security(self):
        """Verifies API key is transmitted in x-goog-api-key header and never in query params."""
        headers = self.provider._get_headers()
        self.assertIn("x-goog-api-key", headers)
        self.assertEqual(headers["x-goog-api-key"], "test-gemini-key")

    def test_missing_api_key_raises_auth_error(self):
        """Verifies missing API key raises ProviderAuthError (401)."""
        unauthed = GeminiProvider(api_key=None)
        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Hi")])
        with self.assertRaises(ProviderAuthError) as ctx:
            unauthed.generate_text(req)
        self.assertEqual(ctx.exception.status_code, 401)

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_upstream_401_403_normalizes_to_auth_error(self, mock_http):
        mock_http.return_value = HttpResponse(
            status_code=401,
            headers={},
            body_bytes=b'{"error": {"message": "Invalid API Key"}}'
        )
        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Hi")])
        with self.assertRaises(ProviderAuthError) as ctx:
            self.provider.generate_text(req)
        self.assertEqual(ctx.exception.status_code, 401)

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_upstream_429_normalizes_to_rate_limit_error(self, mock_http):
        mock_http.return_value = HttpResponse(
            status_code=429,
            headers={"Retry-After": "4.2"},
            body_bytes=b'{"error": {"message": "Resource exhausted"}}'
        )
        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Hi")])
        with self.assertRaises(ProviderRateLimitError) as ctx:
            self.provider.generate_text(req)
        self.assertEqual(ctx.exception.status_code, 429)
        self.assertEqual(ctx.exception.retry_after, 4.2)

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_upstream_400_normalizes_to_invalid_request_error(self, mock_http):
        mock_http.return_value = HttpResponse(
            status_code=400,
            headers={},
            body_bytes=b'{"error": {"message": "Invalid parameter"}}'
        )
        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Hi")])
        with self.assertRaises(ProviderInvalidRequestError) as ctx:
            self.provider.generate_text(req)
        self.assertEqual(ctx.exception.status_code, 400)

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_upstream_503_normalizes_to_unavailable_error(self, mock_http):
        mock_http.return_value = HttpResponse(
            status_code=503,
            headers={},
            body_bytes=b'{"error": {"message": "Service unavailable"}}'
        )
        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Hi")])
        with self.assertRaises(ProviderUnavailableError) as ctx:
            self.provider.generate_text(req)
        self.assertEqual(ctx.exception.status_code, 503)

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_upstream_safety_block_normalizes_to_content_filter_error(self, mock_http):
        mock_http.return_value = HttpResponse(
            status_code=200,
            headers={},
            body_bytes=json.dumps({
                "candidates": [{"finishReason": "SAFETY", "content": {"parts": []}}]
            }).encode("utf-8")
        )
        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Flagged")])
        with self.assertRaises(ProviderContentFilterError) as ctx:
            self.provider.generate_text(req)
        self.assertEqual(ctx.exception.status_code, 422)

    @patch.object(GeminiProvider, "_execute_http_call")
    def test_zero_automatic_retries_and_no_fallback(self, mock_http):
        """Verifies exactly ONE HTTP attempt is made on failure, preserving zero-retry behavior."""
        mock_http.return_value = HttpResponse(
            status_code=503,
            headers={},
            body_bytes=b"Overloaded"
        )
        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Test")])
        with self.assertRaises(ProviderUnavailableError):
            self.provider.generate_text(req)
        self.assertEqual(mock_http.call_count, 1)

    def test_mock_fixture_provider_text_and_structured(self):
        """Verifies MockFixtureProvider behavior for offline testing."""
        mock_p = MockFixtureProvider(canned_text="Deterministic canned text", simulated_latency=0.0)
        self.assertEqual(mock_p.provider_name, "mock")
        self.assertEqual(mock_p.model_name, "mock-model")

        req = LlmCompletionRequest(messages=[LlmMessage(role=LlmRole.USER, content="Hello mock")])
        res = mock_p.generate_text(req)
        self.assertEqual(res.content, "Deterministic canned text")

        obj, s_res = mock_p.generate_structured(req, BuyConfidenceSchema)
        self.assertIsInstance(obj, BuyConfidenceSchema)
        self.assertGreaterEqual(obj.score, 0)
        self.assertLessEqual(obj.score, 100)

        # Test failure modes
        mock_p.set_fail_mode("timeout")
        with self.assertRaises(ProviderTimeoutError):
            mock_p.generate_text(req)

        mock_p.set_fail_mode("auth")
        with self.assertRaises(ProviderAuthError):
            mock_p.generate_text(req)

    def test_provider_factory(self):
        """Verifies ProviderFactory creates correct provider instances and validates names."""
        gemini_p = ProviderFactory.get_provider(
            provider_name="gemini",
            api_key="fact-key",
            primary_model="gemini-3.8-flash"
        )
        self.assertIsInstance(gemini_p, GeminiProvider)
        self.assertEqual(gemini_p.model_name, "gemini-3.8-flash")

        mock_p = ProviderFactory.get_provider(provider_name="mock")
        self.assertIsInstance(mock_p, MockFixtureProvider)

        with self.assertRaises(ValueError):
            ProviderFactory.get_provider(provider_name="unsupported-provider")


if __name__ == "__main__":
    unittest.main()
