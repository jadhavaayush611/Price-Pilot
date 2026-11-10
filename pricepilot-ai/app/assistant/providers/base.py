from abc import ABC, abstractmethod
from typing import Dict, Any, Type, TypeVar, Tuple
from pydantic import BaseModel
from app.assistant.providers.contracts import LlmCompletionRequest, LlmCompletionResponse

T = TypeVar("T", bound=BaseModel)

class LlmProvider(ABC):
    """Stateless provider interface for LLM inference."""

    @property
    @abstractmethod
    def provider_name(self) -> str:
        """Provider identifier (e.g. 'gemini', 'mock')."""
        pass

    @property
    @abstractmethod
    def model_name(self) -> str:
        """Currently active primary model name."""
        pass

    @abstractmethod
    def generate_text(self, request: LlmCompletionRequest) -> LlmCompletionResponse:
        """Generates unstructured text using the configured candidate model."""
        pass

    @abstractmethod
    def generate_structured(self, request: LlmCompletionRequest, schema_class: Type[T]) -> Tuple[T, LlmCompletionResponse]:
        """Generates schema-validated structured response using native schema mode."""
        pass

    @abstractmethod
    def check_health(self) -> Dict[str, Any]:
        """Verifies API credentials and model accessibility without text generation."""
        pass
