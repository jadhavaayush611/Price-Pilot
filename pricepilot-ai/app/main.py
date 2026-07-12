import os
import time
import uuid
from fastapi import FastAPI, Request
from app.api.endpoints import router as api_router
from app.loaders.model_registry import model_registry
from app.utils.logger import log_structured
from app.config.settings import settings
import logging

app = FastAPI(
    title="PricePilot AI Microservice",
    description="Dedicated FastAPI service for machine learning model inference and explanations.",
    version="1.0.0"
)

# Startup event handler
@app.on_event("startup")
def startup_event():
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

# Shutdown event handler
@app.on_event("shutdown")
def shutdown_event():
    log_structured(logging.INFO, "application_shutdown")

# Observability middleware for Request IDs & response times
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

