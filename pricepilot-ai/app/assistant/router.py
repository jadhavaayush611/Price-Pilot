from fastapi import APIRouter, Depends, HTTPException, Security, Header, status
from pydantic import BaseModel
from typing import Dict, Any, List, Optional
from app.api.endpoints import verify_api_key
from app.assistant.orchestrator import orchestrator
from app.assistant.memory import memory_manager
from app.assistant.tools import get_product_details
from app.assistant.response_formatter import response_formatter

router = APIRouter(prefix="/assistant", tags=["Assistant"])

class ChatRequest(BaseModel):
    message: str
    conversationId: Optional[str] = None
    userId: Optional[str] = None
    email: Optional[str] = None

class CompareRequest(BaseModel):
    productIds: List[str]
    conversationId: Optional[str] = None

class AskRequest(BaseModel):
    question: str
    conversationId: Optional[str] = None

class ClearMemoryRequest(BaseModel):
    conversationId: str

@router.post("/chat")
def assistant_chat(
    request: ChatRequest,
    authorization: Optional[str] = Header(None),
    api_key: str = Depends(verify_api_key)
):
    """Orchestrated RAG chat endpoint for the PricePilot assistant."""
    try:
        response = orchestrator.chat(
            message=request.message,
            conversation_id=request.conversationId,
            token=authorization
        )
        return response
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Assistant chat failed: {str(e)}"
        )

@router.post("/compare")
def assistant_compare(
    request: CompareRequest,
    authorization: Optional[str] = Header(None),
    api_key: str = Depends(verify_api_key)
):
    """Compares multiple products side-by-side using catalog data."""
    try:
        # Retrieve context for each product ID
        products = []
        for pid in request.productIds:
            details = get_product_details(pid, token=authorization)
            if details:
                products.append(details)
                
        context = {
            "products": products,
            "price_history": []
        }
        
        # Build comparison prompt and run reasoning
        p_names = ", ".join([p.get("name", "Unknown") for p in products])
        query = f"Compare these products: {p_names}"
        
        raw_response = orchestrator._run_local_reasoning(query, context, None)
        
        structured_res = response_formatter.format_response(raw_response, context, query)
        structured_res["conversationId"] = request.conversationId or ""
        return structured_res
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Product comparison failed: {str(e)}"
        )

@router.post("/ask")
def assistant_ask(
    request: AskRequest,
    authorization: Optional[str] = Header(None),
    api_key: str = Depends(verify_api_key)
):
    """Single turn question answering endpoint using RAG."""
    try:
        # Ask is similar to chat, but typically resets memory or runs single turn
        conversation_id = request.conversationId or "single-turn-ask"
        # Reset memory for this conv ID beforehand to ensure single turn behavior
        memory_manager.clear(conversation_id)
        
        response = orchestrator.chat(
            message=request.question,
            conversation_id=conversation_id,
            token=authorization
        )
        return response
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Ask request failed: {str(e)}"
        )

@router.post("/clear_memory")
def clear_memory(
    request: ClearMemoryRequest,
    api_key: str = Depends(verify_api_key)
):
    """Clears history and context saved in memory for a conversation."""
    try:
        memory_manager.clear(request.conversationId)
        return {"status": "success", "message": f"Memory cleared for conversation: {request.conversationId}"}
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Failed to clear memory: {str(e)}"
        )
