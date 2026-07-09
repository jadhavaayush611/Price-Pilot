import logging
from typing import Any, Dict, List, Optional
from pricepilot.http import HttpClientSession

logger = logging.getLogger("pricepilot.assistant")

class AssistantModule:
    """Module for interacting with the PricePilot AI Assistant."""

    def __init__(self, http_client: HttpClientSession) -> None:
        self._http = http_client

    def chat(self, message: str, conversation_id: Optional[str] = None) -> Dict[str, Any]:
        """Sends a message to the AI Assistant and retrieves response details."""
        logger.info(f"SDK: Chat message: '{message}' | Conversation ID: {conversation_id}")
        payload = {"message": message}
        if conversation_id:
            payload["conversationId"] = conversation_id
        
        return self._http.request(
            "POST",
            "/assistant/chat",
            json=payload
        )

    def compare(self, product_ids: List[str], conversation_id: Optional[str] = None) -> Dict[str, Any]:
        """Compares multiple products side-by-side."""
        logger.info(f"SDK: Compare products: {product_ids} | Conversation ID: {conversation_id}")
        payload = {"productIds": product_ids}
        if conversation_id:
            payload["conversationId"] = conversation_id
            
        return self._http.request(
            "POST",
            "/assistant/compare",
            json=payload
        )

    def ask(self, question: str, conversation_id: Optional[str] = None) -> Dict[str, Any]:
        """Sends a single-turn question to the AI Assistant."""
        logger.info(f"SDK: Ask question: '{question}' | Conversation ID: {conversation_id}")
        payload = {"question": question}
        if conversation_id:
            payload["conversationId"] = conversation_id
            
        return self._http.request(
            "POST",
            "/assistant/ask",
            json=payload
        )

    def clear_memory(self, conversation_id: str) -> Dict[str, Any]:
        """Clears assistant conversation history memory."""
        logger.info(f"SDK: Clear assistant memory for conversation: {conversation_id}")
        payload = {"conversationId": conversation_id}
        
        return self._http.request(
            "POST",
            "/assistant/clear_memory",
            json=payload
        )
