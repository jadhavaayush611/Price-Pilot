import logging
import json
import time
from typing import Any, Dict
import uuid

# Configure standard logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
    handlers=[
        logging.StreamHandler()
    ]
)

logger = logging.getLogger("pricepilot-ai")

def log_structured(level: int, event: str, extra: Dict[str, Any] = None):
    """Logs structured JSON messages for observability."""
    payload = {
        "timestamp": time.time(),
        "event": event,
        "level": logging.getLevelName(level),
    }
    if extra:
        payload.update(extra)
    
    # Also log string message for standard stdout collection
    logger.log(level, json.dumps(payload))
