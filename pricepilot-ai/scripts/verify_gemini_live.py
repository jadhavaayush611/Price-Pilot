#!/usr/bin/env python3
"""
PricePilot AI - Live Gemini API Verification Script
Checks live availability and latency of candidate models (e.g. gemini-3.8-flash / gemini-2.5-flash).
Safely exits if GEMINI_API_KEY is not configured in the environment.
"""

import os
import sys
import time
import json
from typing import Dict, Any, Optional

# Set default development mode if not set so script can be run standalone
os.environ.setdefault("ENV", "development")
os.environ.setdefault("PRICEPILOT_AI_API_KEY", "pricepilot-secret-api-key")

# Add parent directory to path so app modules can be loaded if run directly
current_dir = os.path.dirname(os.path.abspath(__file__))
parent_dir = os.path.dirname(current_dir)
if parent_dir not in sys.path:
    sys.path.insert(0, parent_dir)

from app.assistant.providers.gemini import GeminiProvider
from app.assistant.providers.contracts import LlmCompletionRequest, LlmMessage, LlmRole
from app.assistant.providers.schemas import BuyConfidenceSchema
from app.assistant.providers.exceptions import ProviderError, ProviderTimeoutError, ProviderAuthError

def mask_key(key: Optional[str]) -> str:
    if not key:
        return "<none>"
    if len(key) <= 8:
        return "***"
    return f"{key[:4]}...{key[-4:]}"

def run_live_verification():
    api_key = os.getenv("GEMINI_API_KEY") or os.getenv("GOOGLE_API_KEY")
    primary_model = os.getenv("GEMINI_PRIMARY_MODEL") or "gemini-3.8-flash"
    fallback_model = os.getenv("GEMINI_FALLBACK_MODEL") or "gemini-2.5-flash"

    print("=================================================================")
    print("PricePilot AI - Live Gemini Provider Diagnostic")
    print("=================================================================")
    print(f"Primary Target Model  : {primary_model}")
    print(f"Fallback Target Model : {fallback_model}")
    print(f"Configured API Key    : {mask_key(api_key)}")
    print(f"Provider Budget Limit : 3.50 seconds")
    print("-----------------------------------------------------------------")

    if not api_key:
        print("\n[INFO] GEMINI_API_KEY is not set in environment.")
        print("[INFO] Live network check skipped. Use offline test suite:")
        print("       python -m pytest tests/test_gemini_provider.py")
        print("\nTo run live diagnostic:")
        print("  set GEMINI_API_KEY=your_actual_key (Windows CMD)")
        print("  $env:GEMINI_API_KEY=\"your_actual_key\" (PowerShell)")
        print("  python scripts/verify_gemini_live.py")
        return 0

    provider = GeminiProvider(
        api_key=api_key,
        primary_model=primary_model,
        fallback_model=fallback_model,
        timeout_seconds=3.5
    )

    # 1. Health check via metadata endpoint
    print(f"\n[1/3] Checking Model Metadata ({primary_model})...")
    health = provider.check_health()
    print(f"      Status: {health.get('status')} | Details: {health}")
    if health.get("status") != "UP":
        print(f"      WARNING: {primary_model} metadata check returned non-200.")
        if health.get("status_code") == 404:
            print(f"      Model {primary_model} not found on v1beta endpoint.")
            print(f"      Checking fallback model {fallback_model}...")
            fb_provider = GeminiProvider(api_key=api_key, primary_model=fallback_model, timeout_seconds=3.5)
            fb_health = fb_provider.check_health()
            print(f"      Fallback {fallback_model} Status: {fb_health.get('status')}")

    # 2. Plain Text Generation
    print(f"\n[2/3] Testing Plain Text Generation ({primary_model})...")
    text_req = LlmCompletionRequest(
        system_instruction="You are a helpful assistant. Reply in one short sentence.",
        messages=[LlmMessage(role=LlmRole.USER, content="Explain PricePilot in one sentence.")]
    )
    t0 = time.time()
    try:
        res = provider.generate_text(text_req)
        elapsed = time.time() - t0
        print(f"      Success! ({elapsed:.3f}s)")
        print(f"      Model Used    : {res.model_name}")
        print(f"      Tokens        : prompt={res.usage.prompt_tokens}, completion={res.usage.completion_tokens}, total={res.usage.total_tokens}")
        print(f"      Response Text : {res.content[:100]}...")
        if elapsed > 3.5:
            print(f"      WARNING: Generation latency ({elapsed:.3f}s) exceeded 3.5s provider budget!")
        else:
            print(f"      PASS: Latency ({elapsed:.3f}s) within 3.5s budget.")
    except ProviderTimeoutError as e:
        print(f"      FAIL: Call timed out after {e.elapsed_seconds:.2f}s (budget: 3.5s)")
        return 1
    except ProviderError as e:
        print(f"      FAIL: Provider error: {type(e).__name__} - {e}")
        return 1
    except Exception as e:
        print(f"      FAIL: Unexpected error: {e}")
        return 1

    # 3. Structured Output Generation (BuyConfidenceSchema)
    print(f"\n[3/3] Testing Native Structured JSON Output ({primary_model})...")
    struct_req = LlmCompletionRequest(
        system_instruction="You are a price evaluation engine. Provide a buy confidence score (0-100) and reason.",
        messages=[LlmMessage(role=LlmRole.USER, content="Product: MacBook Air M2. Current price ₹79,990 (lowest historical ₹79,990).")]
    )
    t0 = time.time()
    try:
        obj, s_res = provider.generate_structured(struct_req, BuyConfidenceSchema)
        elapsed = time.time() - t0
        print(f"      Success! ({elapsed:.3f}s)")
        print(f"      Parsed Model  : BuyConfidenceSchema(score={obj.score}, reason='{obj.reason[:60]}...')")
        print(f"      PASS: Pydantic schema validation successful.")
    except ProviderError as e:
        print(f"      FAIL: Structured output error: {type(e).__name__} - {e}")
        return 1
    except Exception as e:
        print(f"      FAIL: Unexpected error: {e}")
        return 1

    print("\n=================================================================")
    print("ALL LIVE VERIFICATION CHECKS COMPLETED SUCCESSFULLY")
    print("=================================================================")
    return 0

if __name__ == "__main__":
    sys.exit(run_live_verification())
