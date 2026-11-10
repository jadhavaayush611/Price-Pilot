import time
import json
import logging
import socket
import ssl
import http.client
from urllib.parse import urlsplit
from typing import Dict, Any, Type, TypeVar, Tuple, Optional
from pydantic import BaseModel, ValidationError

from app.assistant.providers.base import LlmProvider
from app.assistant.providers.contracts import (
    LlmCompletionRequest,
    LlmCompletionResponse,
    LlmRole,
    LlmUsage
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

T = TypeVar("T", bound=BaseModel)
logger = logging.getLogger("pricepilot.assistant.providers.gemini")


class HttpResponse:
    """Lightweight HTTP response holder for cumulative-deadline transport."""

    def __init__(self, status_code: int, headers: Dict[str, str], body_bytes: bytes) -> None:
        self.status_code = status_code
        self.headers = headers
        self._body_bytes = body_bytes
        self._text: Optional[str] = None
        self._json: Optional[Any] = None

    @property
    def text(self) -> str:
        if self._text is None:
            self._text = self._body_bytes.decode("utf-8", errors="replace")
        return self._text

    def json(self) -> Any:
        if self._json is None:
            self._json = json.loads(self.text)
        return self._json


class GeminiProvider(LlmProvider):
    """Stateless Google Gemini Developer API REST provider with genuine cumulative deadline enforcement."""

    def __init__(
        self,
        api_key: Optional[str] = None,
        primary_model: str = "gemini-3.8-flash",
        fallback_model: str = "gemini-2.5-flash",
        timeout_seconds: float = 3.5,
        base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    ) -> None:
        self._api_key = api_key
        self._primary_model = primary_model
        self._fallback_model = fallback_model
        self._timeout = timeout_seconds
        self._base_url = base_url.rstrip("/")

    @property
    def provider_name(self) -> str:
        return "gemini"

    @property
    def model_name(self) -> str:
        return self._primary_model

    def _get_headers(self) -> Dict[str, str]:
        headers = {
            "Content-Type": "application/json",
            "Accept": "application/json"
        }
        if self._api_key:
            headers["x-goog-api-key"] = self._api_key
        return headers

    def _build_gemini_payload(self, request: LlmCompletionRequest) -> Dict[str, Any]:
        payload: Dict[str, Any] = {}

        # 1. System instruction (native v1beta parameter)
        if request.system_instruction and request.system_instruction.strip():
            payload["systemInstruction"] = {
                "parts": [{"text": request.system_instruction.strip()}]
            }

        # 2. Conversation messages
        contents = []
        for msg in request.messages:
            role = "user" if msg.role == LlmRole.USER else "model"
            contents.append({
                "role": role,
                "parts": [{"text": msg.content}]
            })
        if not contents:
            contents.append({"role": "user", "parts": [{"text": ""}]})
        payload["contents"] = contents

        # 3. Generation configuration
        cfg = request.generation_config
        gen_config: Dict[str, Any] = {
            "temperature": cfg.temperature,
            "topP": cfg.top_p,
            "maxOutputTokens": cfg.max_output_tokens,
            "responseMimeType": cfg.response_mime_type
        }
        if cfg.response_schema:
            gen_config["responseSchema"] = cfg.response_schema

        payload["generationConfig"] = gen_config
        return payload

    def _execute_http_call(
        self,
        endpoint: str,
        payload: Optional[Dict[str, Any]] = None,
        method: str = "POST"
    ) -> HttpResponse:
        """Executes HTTP request enforcing a genuine cumulative deadline covering connect,

        transmission, headers, and full response body consumption.
        """
        start_time = time.monotonic()
        deadline = start_time + self._timeout
        headers = self._get_headers()

        parsed = urlsplit(endpoint)
        is_ssl = parsed.scheme.lower() == "https"
        host = parsed.hostname or "localhost"
        port = parsed.port or (443 if is_ssl else 80)
        path = parsed.path or "/"
        if parsed.query:
            path = f"{path}?{parsed.query}"

        conn: Optional[http.client.HTTPConnection] = None
        try:
            # 1. Connection establishment under remaining deadline
            rem = deadline - time.monotonic()
            if rem <= 0:
                raise ProviderTimeoutError(
                    f"Gemini API timed out before connection could be established (budget: {self._timeout:.2f}s)",
                    provider=self.provider_name,
                    elapsed_seconds=time.monotonic() - start_time
                )

            if is_ssl:
                ssl_context = ssl.create_default_context()
                conn = http.client.HTTPSConnection(host, port, timeout=rem, context=ssl_context)
            else:
                conn = http.client.HTTPConnection(host, port, timeout=rem)

            conn.connect()
            if conn.sock:
                conn.sock.settimeout(rem)

            # 2. Request transmission under remaining deadline
            rem = deadline - time.monotonic()
            if rem <= 0:
                raise ProviderTimeoutError(
                    f"Gemini API timed out before request transmission (budget: {self._timeout:.2f}s)",
                    provider=self.provider_name,
                    elapsed_seconds=time.monotonic() - start_time
                )
            if conn.sock:
                conn.sock.settimeout(rem)

            body_bytes = json.dumps(payload).encode("utf-8") if payload is not None else None
            conn.request(method, path, body=body_bytes, headers=headers)

            # 3. Waiting for response headers under remaining deadline
            rem = deadline - time.monotonic()
            if rem <= 0:
                raise ProviderTimeoutError(
                    f"Gemini API timed out waiting for response headers (budget: {self._timeout:.2f}s)",
                    provider=self.provider_name,
                    elapsed_seconds=time.monotonic() - start_time
                )
            if conn.sock:
                conn.sock.settimeout(rem)

            resp = conn.getresponse()

            # 4. Reading and consuming complete response body under cumulative deadline
            chunks = []
            sock = getattr(getattr(resp.fp, "raw", None), "_sock", None) or conn.sock
            while True:
                rem = deadline - time.monotonic()
                if rem <= 0:
                    elapsed = time.monotonic() - start_time
                    raise ProviderTimeoutError(
                        f"Gemini API cumulative deadline exceeded while consuming response body after {elapsed:.2f}s (budget: {self._timeout:.2f}s)",
                        provider=self.provider_name,
                        elapsed_seconds=elapsed
                    )
                if sock:
                    sock.settimeout(rem)

                # read1 yields available buffered/streamed chunks immediately without blocking for full buffer
                chunk = resp.read1(4096) if hasattr(resp, "read1") else resp.read(4096)
                if not chunk:
                    break
                chunks.append(chunk)

            raw_body = b"".join(chunks)
            res_headers = {k: v for k, v in resp.getheaders()}
            return HttpResponse(resp.status, res_headers, raw_body)

        except (socket.timeout, TimeoutError) as e:
            elapsed = time.monotonic() - start_time
            if conn:
                conn.close()
            raise ProviderTimeoutError(
                f"Gemini API timed out after {elapsed:.2f}s (budget: {self._timeout:.2f}s)",
                provider=self.provider_name,
                elapsed_seconds=elapsed
            ) from e
        except ProviderTimeoutError:
            if conn:
                conn.close()
            raise
        except Exception as e:
            elapsed = time.monotonic() - start_time
            if conn:
                conn.close()
            raise ProviderUnavailableError(
                f"Gemini API connection error: {str(e)}",
                provider=self.provider_name,
                raw_error=str(e)
            ) from e
        finally:
            if conn:
                conn.close()

    def _handle_http_error(self, response: HttpResponse) -> None:
        status_code = response.status_code
        error_text = response.text
        try:
            err_json = response.json()
            message = err_json.get("error", {}).get("message", error_text)
        except Exception:
            message = error_text

        if status_code in (401, 403):
            raise ProviderAuthError(
                f"Gemini authentication failed ({status_code}): {message}",
                provider=self.provider_name,
                status_code=status_code,
                raw_error=error_text
            )
        elif status_code == 429:
            retry_after = 0.0
            ra_hdr = response.headers.get("Retry-After")
            if ra_hdr:
                try:
                    retry_after = float(ra_hdr)
                except ValueError:
                    pass
            raise ProviderRateLimitError(
                f"Gemini quota exhausted ({status_code}): {message}",
                provider=self.provider_name,
                retry_after=retry_after,
                raw_error=error_text
            )
        elif status_code == 400:
            raise ProviderInvalidRequestError(
                f"Gemini invalid request ({status_code}): {message}",
                provider=self.provider_name,
                raw_error=error_text
            )
        elif status_code in (500, 502, 503, 504):
            raise ProviderUnavailableError(
                f"Gemini upstream unavailable ({status_code}): {message}",
                provider=self.provider_name,
                status_code=status_code,
                raw_error=error_text
            )
        else:
            raise ProviderError(
                f"Gemini API call failed with status {status_code}: {message}",
                provider=self.provider_name,
                status_code=status_code,
                raw_error=error_text
            )

    def generate_text(self, request: LlmCompletionRequest) -> LlmCompletionResponse:
        """Executes plain text completion using the primary candidate model with 0 automatic retries."""
        if not self._api_key:
            raise ProviderAuthError(
                "Gemini API key is missing or unconfigured",
                provider=self.provider_name,
                status_code=401
            )

        start_time = time.monotonic()
        url = f"{self._base_url}/models/{self._primary_model}:generateContent"
        payload = self._build_gemini_payload(request)

        response = self._execute_http_call(url, payload, method="POST")
        if response.status_code != 200:
            self._handle_http_error(response)

        latency = time.monotonic() - start_time
        res_json = response.json()

        # Parse candidate
        candidates = res_json.get("candidates", [])
        if not candidates:
            raise ProviderError(
                "Gemini API returned zero candidates",
                provider=self.provider_name,
                raw_error=str(res_json)
            )

        primary_candidate = candidates[0]
        finish_reason = primary_candidate.get("finishReason")
        if finish_reason == "SAFETY":
            raise ProviderContentFilterError(
                "Gemini blocked response due to safety policy",
                provider=self.provider_name,
                raw_error=str(primary_candidate)
            )

        parts = primary_candidate.get("content", {}).get("parts", [])
        text = "".join(part.get("text", "") for part in parts).strip()

        usage_meta = res_json.get("usageMetadata", {})
        usage = LlmUsage(
            prompt_tokens=usage_meta.get("promptTokenCount", 0),
            completion_tokens=usage_meta.get("candidatesTokenCount", 0),
            total_tokens=usage_meta.get("totalTokenCount", 0)
        )

        return LlmCompletionResponse(
            content=text,
            model_name=self._primary_model,
            provider_name=self.provider_name,
            latency_seconds=latency,
            usage=usage,
            finish_reason=finish_reason,
            raw_response=res_json
        )

    def generate_structured(self, request: LlmCompletionRequest, schema_class: Type[T]) -> Tuple[T, LlmCompletionResponse]:
        """Executes structured JSON completion with strict Pydantic schema validation."""
        request.generation_config.response_mime_type = "application/json"
        request.generation_config.response_schema = schema_class.model_json_schema()

        response = self.generate_text(request)
        try:
            validated_obj = schema_class.model_validate_json(response.content)
            response.structured_data = validated_obj.model_dump()
            return validated_obj, response
        except ValidationError as e:
            logger.error("Structured output schema validation failed: %s", e)
            raise StructuredOutputValidationError(
                f"Model response failed Pydantic schema validation for {schema_class.__name__}: {str(e)}",
                provider=self.provider_name,
                validation_errors=str(e)
            ) from e

    def check_health(self) -> Dict[str, Any]:
        """Verifies API key and model availability via metadata endpoint."""
        if not self._api_key:
            return {"status": "DOWN", "error": "api_key_missing", "model": self._primary_model}

        url = f"{self._base_url}/models/{self._primary_model}"
        try:
            resp = self._execute_http_call(url, payload=None, method="GET")
            if resp.status_code == 200:
                return {"status": "UP", "model": self._primary_model, "provider": self.provider_name}
            return {"status": "DOWN", "status_code": resp.status_code, "model": self._primary_model}
        except Exception as e:
            return {"status": "DOWN", "error": str(e), "model": self._primary_model}
