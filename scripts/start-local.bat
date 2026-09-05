@echo off
REM start-local.bat — Start the full OMOBIO platform via docker-compose
REM Usage: scripts\start-local.bat

setlocal enabledelayedexpansion
cd /d "%~dp0.."

echo ==============================================
echo  OMOBIO - Local Stack
echo ==============================================

echo.
echo [1/4] Starting infrastructure...
docker compose up -d mongodb redis kafka zookeeper mysql
if errorlevel 1 goto :err

echo.
echo [2/4] Waiting for MongoDB...
:wait_mongo
docker exec omobio-mongodb mongosh --quiet --eval "db.adminCommand('ping').ok" 2>nul | findstr /C:"1" >nul
if errorlevel 1 (
  timeout /t 2 /nobreak >nul
  goto :wait_mongo
)
echo MongoDB ready.

echo.
echo [3/4] Starting all services...
docker compose up -d
if errorlevel 1 goto :err

echo.
echo [4/4] Waiting for API Gateway...
:wait_gw
curl -sf http://localhost:8080/actuator/health >nul 2>&1
if errorlevel 1 (
  timeout /t 5 /nobreak >nul
  goto :wait_gw
)
echo API Gateway ready.

echo.
echo ==============================================
echo  OMOBIO is running.
echo   API Gateway: http://localhost:8080
echo   Grafana:    http://localhost:3000
echo   Prometheus: http://localhost:9090
echo.
echo   docker compose ps
echo   docker compose logs -f api-gateway
echo   docker compose down
echo ==============================================
exit /b 0

:err
echo ERROR: docker compose failed.
exit /b 1
endlocal
