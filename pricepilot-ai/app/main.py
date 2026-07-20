import os
import time
import uuid
from fastapi import FastAPI, Request
from app.api.endpoints import router as api_router
from app.loaders.model_registry import model_registry
from app.utils.logger import log_structured
from app.config.settings import settings
import logging

from contextlib import asynccontextmanager

@asynccontextmanager
async def lifespan(app: FastAPI):
    # 1. API key validation status
    api_key_status = "MISSING"
    raw_key = os.getenv("PRICEPILOT_AI_API_KEY")
    if raw_key:
        if raw_key == "pricepilot-secret-api-key":
            api_key_status = "INSECURE_DEFAULT"
        else:
            api_key_status = "CONFIGURED"

    # 2. Memory configuration diagnostics
    mem_info = {}
    try:
        if os.path.exists("/sys/fs/cgroup/memory.max"):
            with open("/sys/fs/cgroup/memory.max", "r") as f:
                val = f.read().strip()
                if val != "max":
                    mem_info["container_limit_mb"] = int(val) // (1024 * 1024)
        elif os.path.exists("/sys/fs/cgroup/memory/memory.limit_in_bytes"):
            with open("/sys/fs/cgroup/memory/memory.limit_in_bytes", "r") as f:
                val = f.read().strip()
                mem_info["container_limit_mb"] = int(val) // (1024 * 1024)
    except Exception:
        pass

    try:
        if os.path.exists("/proc/meminfo"):
            with open("/proc/meminfo", "r") as f:
                for line in f:
                    if line.startswith("MemTotal:"):
                        mem_info["system_total_mb"] = int(line.split()[1]) // 1024
                        break
    except Exception:
        pass

    # Load models
    success = model_registry.load_all_models()
    loaded_models = list(model_registry._models.keys()) if success else []

    log_structured(logging.INFO, "application_startup", {
        "settings": {
            "env": settings.env,
            "model_dir": settings.model_dir,
            "dataset_dir": settings.dataset_dir,
            "api_key_status": api_key_status,
            "memory_config": mem_info,
            "loaded_models": loaded_models
        }
    })

    if not success:
        log_structured(logging.WARNING, "application_startup_no_models_loaded", {
            "message": "FastAPI started but no models were loaded. Running in fallback mode."
        })

    yield

    log_structured(logging.INFO, "application_shutdown")

app = FastAPI(
    title="PricePilot AI Microservice",
    description="Dedicated FastAPI service for machine learning model inference and explanations.",
    version="1.0.0",
    lifespan=lifespan
)

# Simple in-memory rate limiting for FastAPI
from collections import defaultdict

class TokenBucket:
    def __init__(self, capacity: int, refill_rate: float):
        self.capacity = capacity
        self.refill_rate = refill_rate
        self.tokens = float(capacity)
        self.last_update = time.time()

    def consume(self) -> bool:
        now = time.time()
        elapsed = now - self.last_update
        self.tokens = min(self.capacity, self.tokens + elapsed * self.refill_rate)
        self.last_update = now
        if self.tokens >= 1.0:
            self.tokens -= 1.0
            return True
        return False

# Limiters mapping: category -> client_ip -> TokenBucket
limiters = defaultdict(dict)

@app.middleware("http")
async def rate_limiting_middleware(request: Request, call_next):
    if not settings.rate_limit_enabled:
        return await call_next(request)

    path = request.url.path
    client_ip = request.client.host if request.client else "unknown"

    limit = None
    category = None

    if path.startswith("/assistant"):
        category = "assistant"
        limit = settings.assistant_limit
    elif path.startswith("/recommendations") or path.startswith("/api/v1/recommendations"):
        category = "recommendation"
        limit = settings.recommendation_limit

    if limit is not None:
        refill_rate = limit / 60.0
        bucket = limiters[category].get(client_ip)
        if not bucket:
            bucket = TokenBucket(limit, refill_rate)
            limiters[category][client_ip] = bucket

        if not bucket.consume():
            log_structured(logging.WARNING, "AUDIT: RATE_LIMIT_EXCEEDED", {
                "client_ip": client_ip,
                "category": category,
                "endpoint": path
            })
            from fastapi.responses import JSONResponse
            return JSONResponse(
                status_code=429,
                content={"error": "Too Many Requests", "detail": "Rate limit exceeded. Please try again later."}
            )

    return await call_next(request)

# Observability middleware for Request IDs, response times & security headers
@app.middleware("http")
async def add_observability_headers(request: Request, call_next):
    # Retrieve X-Request-ID from request headers, or generate a new one
    request_id = request.headers.get("X-Request-ID")
    if not request_id:
        request_id = str(uuid.uuid4())
        
    start_time = time.time()
    
    # Process request
    response = await call_next(request)
    
    process_time = time.time() - start_time
    response.headers["X-Request-ID"] = request_id
    response.headers["X-Response-Time"] = f"{process_time:.6f}s"
    
    # Inject Security Headers
    response.headers["X-Content-Type-Options"] = "nosniff"
    response.headers["X-Frame-Options"] = "DENY"
    response.headers["Referrer-Policy"] = "strict-origin-when-cross-origin"
    response.headers["Content-Security-Policy"] = "default-src 'self'; frame-ancestors 'none';"
    response.headers["Permissions-Policy"] = "geolocation=(), microphone=(), camera=()"
    
    # Log the request details structured
    log_structured(
        logging.INFO,
        "http_request",
        {
            "request_id": request_id,
            "method": request.method,
            "url": str(request.url),
            "status_code": response.status_code,
            "process_time_seconds": process_time
        }
    )
    
    return response

# Register routers
from app.assistant.router import router as assistant_router
app.include_router(api_router)
app.include_router(assistant_router)

