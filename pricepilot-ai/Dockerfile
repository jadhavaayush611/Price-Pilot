FROM python:3.11-slim

# Create a non-root group and user
RUN groupadd -r -g 10001 appgroup && \
    useradd -r -u 10001 -g appgroup -m -d /home/appuser -s /bin/bash appuser

WORKDIR /app

# Install curl for health check
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install dependencies
COPY --chown=appuser:appgroup pricepilot-ai/requirements.txt /app/pricepilot-ai/requirements.txt
RUN pip install --no-cache-dir -r /app/pricepilot-ai/requirements.txt

# Copy ML codebase so class definitions can be loaded by pickle
COPY --chown=appuser:appgroup pricepilot_ml /app/pricepilot_ml
# Copy models directory (default location)
COPY --chown=appuser:appgroup models /app/models

# Copy the FastAPI app itself
COPY --chown=appuser:appgroup pricepilot-ai /app/pricepilot-ai

# Set python path to allow importing pricepilot_ml and app
ENV PYTHONPATH=/app:/app/pricepilot-ai
ENV MODEL_DIR=/app/models/recommendation
ENV ENV=production
ENV LOG_LEVEL=INFO

# Set runtime user and working directory
USER appuser
WORKDIR /app/pricepilot-ai

EXPOSE 8000

# Health check using the FastAPI health endpoint
HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=5 \
  CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--timeout-graceful-shutdown", "30"]
