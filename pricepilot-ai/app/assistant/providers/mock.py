import time
import json
from typing import Dict, Any, Type, TypeVar, Tuple, Optional, List
from pydantic import BaseModel

from app.assistant.providers.base import LlmProvider
from app.assistant.providers.contracts import (
    LlmCompletionRequest,
    LlmCompletionResponse,
    LlmUsage
)
from app.assistant.providers.exceptions import (
    ProviderError,
    ProviderTimeoutError,
    ProviderAuthError,
    ProviderRateLimitError
)

T = TypeVar("T", bound=BaseModel)

class MockFixtureProvider(LlmProvider):
    """Deterministic mock provider for offline testing and local development."""

    def __init__(
        self,
        canned_text: str = "This is a deterministic mock AI response.",
        simulated_latency: float = 0.05,
        fail_mode: Optional[str] = None
    ) -> None:
        self._canned_text = canned_text
        self._simulated_latency = simulated_latency
        self._fail_mode = fail_mode  # None, "timeout", "auth", "rate_limit", "error"
        self.call_history: List[LlmCompletionRequest] = []

    @property
    def provider_name(self) -> str:
        return "mock"

    @property
    def model_name(self) -> str:
        return "mock-model"

    def set_fail_mode(self, mode: Optional[str]) -> None:
        self._fail_mode = mode

    def set_canned_text(self, text: str) -> None:
        self._canned_text = text

    def generate_text(self, request: LlmCompletionRequest) -> LlmCompletionResponse:
        self.call_history.append(request)

        if self._fail_mode == "timeout":
            time.sleep(self._simulated_latency)
            raise ProviderTimeoutError("Simulated mock timeout", provider=self.provider_name, elapsed_seconds=self._simulated_latency)
        elif self._fail_mode == "auth":
            raise ProviderAuthError("Simulated mock auth error", provider=self.provider_name, status_code=401)
        elif self._fail_mode == "rate_limit":
            raise ProviderRateLimitError("Simulated mock rate limit", provider=self.provider_name, retry_after=1.0)
        elif self._fail_mode == "error":
            raise ProviderError("Simulated mock provider error", provider=self.provider_name, status_code=500)

        if self._simulated_latency > 0:
            time.sleep(self._simulated_latency)

        return LlmCompletionResponse(
            content=self._canned_text,
            model_name=self.model_name,
            provider_name=self.provider_name,
            latency_seconds=self._simulated_latency,
            usage=LlmUsage(prompt_tokens=15, completion_tokens=10, total_tokens=25),
            finish_reason="STOP",
            raw_response={"mock": True}
        )

    def generate_structured(self, request: LlmCompletionRequest, schema_class: Type[T]) -> Tuple[T, LlmCompletionResponse]:
        self.call_history.append(request)

        if self._fail_mode:
            self.generate_text(request)  # Will raise the appropriate error

        # Generate realistic default fixture data for known schemas or generic BaseModel
        if schema_class.__name__ == "BuyConfidenceSchema":
            fixture_json = json.dumps({"score": 85, "reason": "Current price is 15% below 90-day moving average."})
        elif schema_class.__name__ == "ProductComparisonSchema":
            fixture_json = json.dumps({
                "products": [
                    {
                        "id": "prod-1",
                        "name": "Mock Product A",
                        "price": 99.99,
                        "discount": 10.0,
                        "sellersCount": 3,
                        "score": 85.0,
                        "trend": "FALLING",
                        "analytics": {}
                    }
                ],
                "summary": "Mock Product A is currently at an optimal price point."
            })
        else:
            # Try to build instance with defaults
            try:
                obj = schema_class()
                fixture_json = obj.model_dump_json()
            except Exception:
                fixture_json = "{}"

        validated_obj = schema_class.model_validate_json(fixture_json)
        res = LlmCompletionResponse(
            content=fixture_json,
            structured_data=validated_obj.model_dump(),
            model_name=self.model_name,
            provider_name=self.provider_name,
            latency_seconds=self._simulated_latency,
            usage=LlmUsage(prompt_tokens=20, completion_tokens=25, total_tokens=45),
            finish_reason="STOP"
        )
        return validated_obj, res

    def check_health(self) -> Dict[str, Any]:
        return {
            "status": "UP",
            "provider": self.provider_name,
            "model": self.model_name
        }
