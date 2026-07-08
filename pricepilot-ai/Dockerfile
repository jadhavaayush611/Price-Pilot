FROM python:3.11-slim

WORKDIR /app

# Install system dependencies if any
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install dependencies
COPY pricepilot-ai/requirements.txt /app/pricepilot-ai/requirements.txt
RUN pip install --no-cache-dir -r /app/pricepilot-ai/requirements.txt

# Copy ML codebase so class definitions can be loaded by pickle
COPY pricepilot_ml /app/pricepilot_ml
# Copy models directory (default location)
COPY models /app/models

# Copy the FastAPI app itself
COPY pricepilot-ai /app/pricepilot-ai

# Set python path to allow importing pricepilot_ml and app
ENV PYTHONPATH=/app:/app/pricepilot-ai
ENV MODEL_DIR=/app/models/recommendation

WORKDIR /app/pricepilot-ai

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
