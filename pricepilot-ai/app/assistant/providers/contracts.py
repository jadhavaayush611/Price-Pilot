from enum import Enum
from typing import List, Dict, Any, Optional
from pydantic import BaseModel, Field

class LlmRole(str, Enum):
    SYSTEM = "system"
    USER = "user"
    ASSISTANT = "assistant"

class LlmMessage(BaseModel):
    role: LlmRole
    content: str

class LlmGenerationConfig(BaseModel):
    temperature: float = Field(default=0.2, ge=0.0, le=2.0)
    top_p: float = Field(default=0.8, ge=0.0, le=1.0)
    max_output_tokens: int = Field(default=2048, ge=1, le=8192)
    response_mime_type: str = "text/plain"
    response_schema: Optional[Dict[str, Any]] = None

class LlmCompletionRequest(BaseModel):
    system_instruction: Optional[str] = None
    messages: List[LlmMessage] = Field(default_factory=list)
    generation_config: LlmGenerationConfig = Field(default_factory=LlmGenerationConfig)
    metadata: Dict[str, Any] = Field(default_factory=dict)

class LlmUsage(BaseModel):
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0

class LlmCompletionResponse(BaseModel):
    content: str
    structured_data: Optional[Dict[str, Any]] = None
    model_name: str
    provider_name: str
    latency_seconds: float
    usage: LlmUsage = Field(default_factory=LlmUsage)
    finish_reason: Optional[str] = None
    raw_response: Optional[Dict[str, Any]] = None
