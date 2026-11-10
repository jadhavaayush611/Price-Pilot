import logging
from typing import Optional
from app.config.settings import settings
from app.assistant.providers.base import LlmProvider
from app.assistant.providers.gemini import GeminiProvider
from app.assistant.providers.mock import MockFixtureProvider

logger = logging.getLogger("pricepilot.assistant.providers.factory")

class ProviderFactory:
    """Factory to instantiate and configure LLM provider adapters."""

    @staticmethod
    def get_provider(
        provider_name: Optional[str] = None,
        api_key: Optional[str] = None,
        primary_model: Optional[str] = None,
        fallback_model: Optional[str] = None,
        timeout_seconds: Optional[float] = None
    ) -> LlmProvider:
        target_provider = (provider_name or settings.ai_provider or "gemini").lower()

        if target_provider == "gemini":
            key = api_key if api_key is not None else settings.gemini_api_key
            model = primary_model or settings.gemini_primary_model
            fallback = fallback_model or settings.gemini_fallback_model
            timeout = timeout_seconds if timeout_seconds is not None else settings.llm_timeout_seconds

            logger.info("Instantiating GeminiProvider (model=%s, timeout=%.2fs)", model, timeout)
            return GeminiProvider(
                api_key=key,
                primary_model=model,
                fallback_model=fallback,
                timeout_seconds=timeout
            )
        elif target_provider == "mock":
            logger.info("Instantiating MockFixtureProvider")
            return MockFixtureProvider()
        else:
            raise ValueError(f"Unsupported LLM provider: {target_provider}")
