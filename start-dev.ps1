# ==============================================================================
# Distributed Auction Engine - PowerShell Dev Runner
# Author: Jayavignesh (jayavignesh.dev)
# ==============================================================================

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "Starting Distributed Auction Engine Dev Environment..." -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Cyan

$root = $PSScriptRoot

# 1. Start Redis Server
Write-Host "[1/3] Starting Redis Server on port 6379..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "redis-server --port 6379"

Start-Sleep -Seconds 2

# 2. Start Backend
Write-Host "[2/3] Starting Spring Boot Backend (dev profile)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\backend'; mvn spring-boot:run '-Dspring-boot.run.profiles=dev'"

Start-Sleep -Seconds 4

# 3. Start Frontend
Write-Host "[3/3] Starting Vite React Frontend..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$root\frontend'; npm run dev"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host "All 3 services launched in separate windows!" -ForegroundColor Green
Write-Host " - Frontend: http://localhost:5173" -ForegroundColor White
Write-Host " - Backend:  http://localhost:8080" -ForegroundColor White
Write-Host " - Swagger:  http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host " - Redis:    localhost:6379" -ForegroundColor White
Write-Host "==============================================================================" -ForegroundColor Cyan

