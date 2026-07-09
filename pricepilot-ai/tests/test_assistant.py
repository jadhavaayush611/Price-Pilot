import unittest
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient

from app.main import app
from app.config.settings import settings
from app.assistant.memory import memory_manager
from app.assistant.rag import rag_pipeline
from app.assistant.tools import search_products, get_product_details
from app.assistant.prompts import SYSTEM_PROMPT

class TestAssistantModule(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(app)
        self.api_headers = {"X-API-Key": settings.api_key}

    def test_memory_lifecycle(self):
        """Test adding messages, tracking active products, and trimming memory."""
        conv_id = "test-conv-123"
        memory_manager.clear(conv_id)

        # Initially empty
        self.assertEqual(len(memory_manager.get_messages(conv_id)), 0)
        self.assertIsNone(memory_manager.get_active_product(conv_id))

        # Add message
        memory_manager.add_message(conv_id, "user", "Hello assistant")
        self.assertEqual(len(memory_manager.get_messages(conv_id)), 1)

        # Track active product
        memory_manager.set_active_product(conv_id, "p-100")
        self.assertEqual(memory_manager.get_active_product(conv_id), "p-100")

        # Trim check
        for i in range(25):
            memory_manager.add_message(conv_id, "user", f"msg {i}")
            memory_manager.add_message(conv_id, "assistant", f"res {i}")

        # Limit is 10 pairs (20 messages)
        self.assertLessEqual(len(memory_manager.get_messages(conv_id)), 20)

    @patch("app.assistant.rag.get_product_analytics")
    @patch("app.assistant.rag.get_price_history")
    @patch("app.assistant.rag.search_products")
    def test_rag_retrieval_trigger(self, mock_search, mock_history, mock_analytics):
        """Test that the retrieval pipeline triggers product search for matching keywords."""
        mock_search.return_value = {"content": [{"id": "p123", "name": "iPhone 16"}], "totalElements": 1}
        mock_history.return_value = []
        mock_analytics.return_value = {}
        
        context = rag_pipeline.retrieve_context("iPhone 16", token="mock-token")
        
        mock_search.assert_called_once_with(keyword="iPhone 16", token="mock-token")
        self.assertTrue(len(context["products"]) > 0)
        self.assertEqual(context["products"][0]["id"], "p123")

    @patch("requests.get")
    def test_tool_execution(self, mock_get):
        """Test that service wrappers correctly execute HTTP requests to the backend with authentication."""
        mock_resp = MagicMock()
        mock_resp.status_code = 200
        mock_resp.json.return_value = {"name": "Test Laptop", "id": "p456"}
        mock_get.return_value = mock_resp

        data = get_product_details("p456", token="my-secret-token")
        
        self.assertEqual(data["name"], "Test Laptop")
        mock_get.assert_called_once()
        headers = mock_get.call_args[1]["headers"]
        self.assertEqual(headers["Authorization"], "Bearer my-secret-token")

    def test_chat_endpoint_routing(self):
        """Test routing of assistant endpoints with API key protection."""
        # Unauthenticated
        res = self.client.post("/assistant/chat", json={"message": "hello"})
        self.assertEqual(res.status_code, 401)

        # Authenticated - mock orchestrator response
        with patch("app.assistant.router.orchestrator.chat") as mock_chat:
            mock_chat.return_value = {
                "response": "Perfect deal!",
                "conversationId": "c789",
                "products": [],
                "suggestedPrompts": []
            }
            res = self.client.post(
                "/assistant/chat",
                json={"message": "Should I buy?", "conversationId": "c789"},
                headers=self.api_headers
            )
            self.assertEqual(res.status_code, 200)
            self.assertEqual(res.json()["response"], "Perfect deal!")
            mock_chat.assert_called_once()

    def test_clear_memory_endpoint(self):
        """Test clearing conversational memory via API."""
        conv_id = "temp-conv"
        memory_manager.add_message(conv_id, "user", "remember me")
        
        res = self.client.post(
            "/assistant/clear_memory",
            json={"conversationId": conv_id},
            headers=self.api_headers
        )
        self.assertEqual(res.status_code, 200)
        self.assertEqual(len(memory_manager.get_messages(conv_id)), 0)
