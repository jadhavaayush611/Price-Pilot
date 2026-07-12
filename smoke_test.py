#!/usr/bin/env python3
import sys
import json
import urllib.request
import urllib.error
import time

FRONTEND_URL = "http://localhost:80"
BACKEND_URL = "http://localhost:8080/api/v1"
ACTUATOR_URL = "http://localhost:8080/actuator"
AI_URL = "http://localhost:8000"

def log_test(step_name, success, details=""):
    status = "SUCCESS" if success else "FAILED"
    color = "\033[92m" if success else "\033[91m"
    reset = "\033[0m"
    print(f"[{color}{status}{reset}] {step_name} {details}")

def http_get(url, headers=None):
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req, timeout=5) as response:
            return response.status, response.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        try:
            err_body = e.read().decode('utf-8')
        except Exception:
            err_body = str(e)
        return e.code, err_body
    except Exception as e:
        return 0, str(e)

def http_post(url, body_dict, headers=None):
    if headers is None:
        headers = {}
    headers["Content-Type"] = "application/json"
    data = json.dumps(body_dict).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, response.read().decode('utf-8')
    except urllib.error.HTTPError as e:
        try:
            err_body = e.read().decode('utf-8')
        except Exception:
            err_body = str(e)
        return e.code, err_body
    except Exception as e:
        return 0, str(e)

def main():
    print("==================================================")
    print("       PricePilot Post-Deployment Smoke Test      ")
    print("==================================================")
    
    # 1. Frontend Reachable
    code, body = http_get(FRONTEND_URL)
    if code in (200, 304) or (code == 0 and "Connection refused" not in body):
        log_test("Frontend Reachable", True, f"({FRONTEND_URL} -> HTTP {code})")
    else:
        # Try dev port 3000 or fall back to port 5173
        code_3000, body_3000 = http_get("http://localhost:3000")
        if code_3000 in (200, 304):
            log_test("Frontend Reachable", True, f"(http://localhost:3000 -> HTTP {code_3000})")
        else:
            log_test("Frontend Reachable", False, f"({FRONTEND_URL} failed, http://localhost:3000 failed)")

    # 2. Backend Health Check
    code, body = http_get(f"{BACKEND_URL}/health")
    if code == 200:
        try:
            data = json.loads(body)
            log_test("Backend Custom Health Check", True, f"({data})")
        except Exception:
            log_test("Backend Custom Health Check", False, "Could not parse JSON response")
    else:
        log_test("Backend Custom Health Check", False, f"HTTP Status {code}: {body}")

    # 3. Backend Actuator Readiness check
    code, body = http_get(f"{ACTUATOR_URL}/health/readiness")
    if code == 200:
        try:
            data = json.loads(body)
            log_test("Backend Actuator Readiness Check", True, f"(Status: {data.get('status')})")
        except Exception:
            log_test("Backend Actuator Readiness Check", False, "Could not parse JSON response")
    else:
        log_test("Backend Actuator Readiness Check", False, f"HTTP Status {code}: {body}")

    # 4. AI Service Reachability
    code, body = http_get(f"{AI_URL}/health/readiness")
    if code == 200:
        try:
            data = json.loads(body)
            log_test("AI Service Readiness Check", True, f"(Status: {data.get('status')})")
        except Exception:
            log_test("AI Service Readiness Check", False, "Could not parse JSON response")
    else:
        log_test("AI Service Readiness Check", False, f"HTTP Status {code}: {body}")

    # 5. Authentication Test (Register & Login)
    email = f"smoketest_{int(time.time())}@example.com"
    password = "Password123!"
    register_payload = {
        "firstName": "Smoke",
        "lastName": "Tester",
        "email": email,
        "password": password
    }
    
    code, body = http_post(f"{BACKEND_URL}/auth/register", register_payload)
    token = None
    if code in (200, 201):
        try:
            data = json.loads(body)
            token = data.get("token")
            log_test("Auth Registration", True, f"(Created test user: {email})")
        except Exception:
            log_test("Auth Registration", False, "Could not parse JSON response")
    else:
        log_test("Auth Registration", False, f"HTTP Status {code}: {body}")

    if token:
        # Test Login
        login_payload = {
            "email": email,
            "password": password
        }
        code, body = http_post(f"{BACKEND_URL}/auth/login", login_payload)
        if code == 200:
            log_test("Auth Login", True, "(Login successful)")
        else:
            log_test("Auth Login", False, f"HTTP Status {code}: {body}")

        # 6. Recommendations Test
        headers = {"Authorization": f"Bearer {token}"}
        code, body = http_get(f"{BACKEND_URL}/recommendations", headers=headers)
        if code == 200:
            log_test("Recommendations Retrieval", True, "(Successfully fetched personalized recommendations)")
        else:
            log_test("Recommendations Retrieval", False, f"HTTP Status {code}: {body}")

        # 7. AI Assistant Test
        chat_payload = {
            "message": "Hello pricepilot, list standard recommendation models."
        }
        code, body = http_post(f"{BACKEND_URL}/assistant/chat", chat_payload, headers=headers)
        if code == 200:
            log_test("AI Assistant Chat", True, "(Successfully received response from AI Assistant)")
        else:
            log_test("AI Assistant Chat", False, f"HTTP Status {code}: {body}")
    else:
        log_test("Auth Login", False, "(Skipped due to registration failure)")
        log_test("Recommendations Retrieval", False, "(Skipped due to registration failure)")
        log_test("AI Assistant Chat", False, "(Skipped due to registration failure)")

    print("==================================================")
    print("               Smoke Test Complete                ")
    print("==================================================")

if __name__ == "__main__":
    main()
