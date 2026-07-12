import logging
import json
import os
from datetime import datetime, timezone
from typing import Any, Dict

# Configure standard logging level from environment
log_level_str = os.getenv("LOG_LEVEL", "INFO").upper()
log_level = getattr(logging, log_level_str, logging.INFO)

is_production = os.getenv("ENV", "production").lower() in ("prod", "production")

if is_production:
    # In production, output raw message (which is already structured JSON from log_structured)
    logging.basicConfig(
        level=log_level,
        format='%(message)s',
        handlers=[logging.StreamHandler()]
    )
else:
    # In development, readable format
    logging.basicConfig(
        level=log_level,
        format='%(asctime)s [%(levelname)s] %(name)s: %(message)s',
        handlers=[logging.StreamHandler()]
    )

logger = logging.getLogger("pricepilot-ai")

def log_structured(level: int, event: str, extra: Dict[str, Any] = None):
    """Logs structured JSON messages for observability."""
    payload = {
        "timestamp": datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
        "event": event,
        "level": logging.getLevelName(level),
    }
    if extra:
        payload.update(extra)
    
    if is_production:
        logger.log(level, json.dumps(payload))
    else:
        # Readable formatting in dev
        extra_str = f" | {extra}" if extra else ""
        logger.log(level, f"EVENT: {event}{extra_str}")

