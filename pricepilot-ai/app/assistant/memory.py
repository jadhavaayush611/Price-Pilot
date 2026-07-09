import logging
from typing import Dict, Any, List, Optional

logger = logging.getLogger("pricepilot.assistant.memory")

class ConversationMemory:
    """Manages conversational memory for the PricePilot AI Assistant.
    Currently in-memory, but designed to easily plug in Redis.
    """
    def __init__(self, max_messages: int = 10) -> None:
        self.max_messages = max_messages
        # In-memory storage keyed by conversation_id
        # Schema per conversation:
        # {
        #   "messages": [{"role": "user"/"assistant", "content": "..."}],
        #   "active_product_id": Optional[str],
        #   "user_preferences": Dict[str, Any],
        #   "last_recommendations": List[Dict[str, Any]],
        #   "previous_searches": List[str]
        # }
        self._storage: Dict[str, Dict[str, Any]] = {}

    def _get_or_create(self, conversation_id: str) -> Dict[str, Any]:
        if conversation_id not in self._storage:
            self._storage[conversation_id] = {
                "messages": [],
                "active_product_id": None,
                "user_preferences": {},
                "last_recommendations": [],
                "previous_searches": []
            }
        return self._storage[conversation_id]

    def add_message(self, conversation_id: str, role: str, content: str) -> None:
        state = self._get_or_create(conversation_id)
        state["messages"].append({"role": role, "content": content})
        # Trim messages to max_messages limit
        if len(state["messages"]) > self.max_messages * 2:  # message pairs (user + assistant)
            state["messages"] = state["messages"][-self.max_messages * 2:]

    def get_messages(self, conversation_id: str) -> List[Dict[str, str]]:
        state = self._get_or_create(conversation_id)
        return state["messages"]

    def set_active_product(self, conversation_id: str, product_id: Optional[str]) -> None:
        state = self._get_or_create(conversation_id)
        state["active_product_id"] = product_id

    def get_active_product(self, conversation_id: str) -> Optional[str]:
        state = self._get_or_create(conversation_id)
        return state.get("active_product_id")

    def update_user_preferences(self, conversation_id: str, prefs: Dict[str, Any]) -> None:
        state = self._get_or_create(conversation_id)
        state["user_preferences"].update(prefs)

    def get_user_preferences(self, conversation_id: str) -> Dict[str, Any]:
        state = self._get_or_create(conversation_id)
        return state.get("user_preferences", {})

    def set_last_recommendations(self, conversation_id: str, recs: List[Dict[str, Any]]) -> None:
        state = self._get_or_create(conversation_id)
        state["last_recommendations"] = recs

    def get_last_recommendations(self, conversation_id: str) -> List[Dict[str, Any]]:
        state = self._get_or_create(conversation_id)
        return state.get("last_recommendations", [])

    def add_previous_search(self, conversation_id: str, query: str) -> None:
        state = self._get_or_create(conversation_id)
        if query not in state["previous_searches"]:
            state["previous_searches"].append(query)
            if len(state["previous_searches"]) > 5:
                state["previous_searches"].pop(0)

    def get_previous_searches(self, conversation_id: str) -> List[str]:
        state = self._get_or_create(conversation_id)
        return state.get("previous_searches", [])

    def clear(self, conversation_id: str) -> None:
        if conversation_id in self._storage:
            del self._storage[conversation_id]
        logger.info(f"Cleared memory for conversation: {conversation_id}")

    def get_all_context(self, conversation_id: str) -> Dict[str, Any]:
        return self._get_or_create(conversation_id)

# Singleton memory instance
memory_manager = ConversationMemory()
