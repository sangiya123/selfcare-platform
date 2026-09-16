@echo off
REM start-local.bat — Start selfcare infrastructure ONLY (MongoDB, Redis, MySQL, Kafka, admin UIs)
REM Application microservices deploy to Kubernetes via Jenkins (see docs/RUNBOOK_CI_CD.md).
REM Usage: scripts\start-local.bat

setlocal enabledelayedexpansion
cd /d "%~dp0.."

echo ==============================================
echo  selfcare - Local Infrastructure Only
echo ==============================================

echo.
echo [1/2] Starting infrastructure...
docker compose up -d
if errorlevel 1 goto :err

echo.
echo [2/2] Waiting for MongoDB...
:wait_mongo
docker exec selfcare-infra-mongodb mongosh --quiet --eval "db.adminCommand('ping').ok" 2>nul | findstr /C:"1" >nul
if errorlevel 1 (
  timeout /t 2 /nobreak >nul
  goto :wait_mongo
)
echo MongoDB ready.

echo.
echo ==============================================
echo  Stateful infra is running (private, local only)
echo   Mongo Express: http://localhost:8081
echo   PHPMyAdmin:    http://localhost:8080
echo.
echo  Application deploys to Kubernetes via Jenkins:
echo   scripts\deploy-k8s.bat --env dev --local
echo.
echo   docker compose ps
echo   docker compose down
echo ==============================================
exit /b 0

:err
echo ERROR: docker compose failed.
exit /b 1
endlocal