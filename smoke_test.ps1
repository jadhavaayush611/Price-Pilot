# PricePilot Post-Deployment Smoke Test (PowerShell Version)
$ErrorActionPreference = "Stop"

$FRONTEND_URL = "http://localhost:80"
$BACKEND_URL = "http://localhost:8080/api/v1"
$ACTUATOR_URL = "http://localhost:8080/actuator"
$AI_URL = "http://localhost:8000"

function Log-Test {
    param(
        [string]$StepName,
        [bool]$Success,
        [string]$Details = ""
    )
    if ($Success) {
        Write-Host -NoNewline "["
        Write-Host -NoNewline -ForegroundColor Green "SUCCESS"
        Write-Host "] $StepName $Details"
    } else {
        Write-Host -NoNewline "["
        Write-Host -NoNewline -ForegroundColor Red "FAILED"
        Write-Host "] $StepName $Details"
    }
}

Write-Host "=================================================="
Write-Host "       PricePilot Post-Deployment Smoke Test      "
Write-Host "=================================================="

# 1. Frontend Reachable
try {
    $resp = Invoke-WebRequest -Uri $FRONTEND_URL -UseBasicParsing -TimeoutSec 5
    if ($resp.StatusCode -eq 200 -or $resp.StatusCode -eq 304) {
        Log-Test "Frontend Reachable" $true "($FRONTEND_URL -> HTTP $($resp.StatusCode))"
    } else {
        Log-Test "Frontend Reachable" $false "($FRONTEND_URL returned HTTP $($resp.StatusCode))"
    }
} catch {
    Log-Test "Frontend Reachable" $false "$($_.Exception.Message)"
}

# 2. Backend Health Check
try {
    $resp = Invoke-RestMethod -Uri "$BACKEND_URL/health" -Method Get -TimeoutSec 5
    Log-Test "Backend Custom Health Check" $true "(status: $($resp.status), database: $($resp.database), redis: $($resp.redis), ai_service: $($resp.ai_service))"
} catch {
    Log-Test "Backend Custom Health Check" $false "$($_.Exception.Message)"
}

# 3. Backend Actuator Readiness check
try {
    $resp = Invoke-RestMethod -Uri "$ACTUATOR_URL/health/readiness" -Method Get -TimeoutSec 5
    Log-Test "Backend Actuator Readiness Check" $true "(status: $($resp.status))"
} catch {
    Log-Test "Backend Actuator Readiness Check" $false "$($_.Exception.Message)"
}

# 4. AI Service Reachability
try {
    $resp = Invoke-RestMethod -Uri "$AI_URL/health/readiness" -Method Get -TimeoutSec 5
    Log-Test "AI Service Readiness Check" $true "(status: $($resp.status))"
} catch {
    Log-Test "AI Service Readiness Check" $false "$($_.Exception.Message)"
}

# 5. Authentication Test (Register & Login)
$timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$email = "smoketest_$timestamp@example.com"
$password = "Password123!"

$registerPayload = @{
    firstName = "Smoke"
    lastName = "Tester"
    email = $email
    password = $password
} | ConvertTo-Json

$token = $null

try {
    $resp = Invoke-RestMethod -Uri "$BACKEND_URL/auth/register" -Method Post -Body $registerPayload -ContentType "application/json" -TimeoutSec 10
    $token = $resp.token
    Log-Test "Auth Registration" $true "(Created test user: $email)"
} catch {
    Log-Test "Auth Registration" $false "$($_.Exception.Message)"
}

if ($token) {
    # Test Login
    $loginPayload = @{
        email = $email
        password = $password
    } | ConvertTo-Json

    try {
        $resp = Invoke-RestMethod -Uri "$BACKEND_URL/auth/login" -Method Post -Body $loginPayload -ContentType "application/json" -TimeoutSec 10
        Log-Test "Auth Login" $true "(Login successful)"
    } catch {
        Log-Test "Auth Login" $false "$($_.Exception.Message)"
    }

    # 6. Recommendations Test
    $headers = @{
        Authorization = "Bearer $token"
    }

    try {
        $resp = Invoke-RestMethod -Uri "$BACKEND_URL/recommendations" -Method Get -Headers $headers -TimeoutSec 10
        Log-Test "Recommendations Retrieval" $true "(Successfully fetched personalized recommendations)"
    } catch {
        Log-Test "Recommendations Retrieval" $false "$($_.Exception.Message)"
    }

    # 7. AI Assistant Test
    $chatPayload = @{
        message = "Hello pricepilot, list standard recommendation models."
    } | ConvertTo-Json

    try {
        $resp = Invoke-RestMethod -Uri "$BACKEND_URL/assistant/chat" -Method Post -Body $chatPayload -ContentType "application/json" -Headers $headers -TimeoutSec 15
        Log-Test "AI Assistant Chat" $true "(Successfully received response from AI Assistant)"
    } catch {
        Log-Test "AI Assistant Chat" $false "$($_.Exception.Message)"
    }
} else {
    Log-Test "Auth Login" $false "(Skipped due to registration failure)"
    Log-Test "Recommendations Retrieval" $false "(Skipped due to registration failure)"
    Log-Test "AI Assistant Chat" $false "(Skipped due to registration failure)"
}

Write-Host "=================================================="
Write-Host "               Smoke Test Complete                "
Write-Host "=================================================="
