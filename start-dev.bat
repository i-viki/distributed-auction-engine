@echo off
title Distributed Auction Engine - Dev Runner
echo ==============================================================================
echo Starting Distributed Auction Engine Dev Environment...
echo Author: Jayavignesh (jayavignesh.dev)
echo ==============================================================================

echo [1/3] Starting Redis Server on port 6379...
start "LiveBid - Redis Server" cmd /k "redis-server --port 6379"

timeout /t 2 /nobreak >nul

echo [2/3] Starting Spring Boot Backend (dev profile)...
start "LiveBid - Backend (Spring Boot)" cmd /k "cd /d %~dp0backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev"

timeout /t 4 /nobreak >nul

echo [3/3] Starting Vite React Frontend...
start "LiveBid - Frontend (React Vite)" cmd /k "cd /d %~dp0frontend && npm run dev"

echo ==============================================================================
echo All 3 services launched in separate windows!
echo - Frontend: http://localhost:5173
echo - Backend:  http://localhost:8080
echo - Swagger:  http://localhost:8080/swagger-ui.html
echo - Redis:    localhost:6379
echo ==============================================================================

