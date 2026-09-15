@echo off
REM build-images.bat — Build all selfcare Docker images on Windows
REM Usage: scripts\build-images.bat
REM
REM Build context = platform root (contains backend/ and industry-packs/)
REM This is required because the parent pom.xml references industry-packs.

setlocal enabledelayedexpansion
set PLATFORM_DIR=%~dp0..
set IMAGE_REGISTRY=localhost:5000/selfcare
set VERSION=1.0.0

set SERVICES=api-gateway config-tenant-service customer-identity-service admin-identity-service account-entitlement-service dashboard-bff product-service usage-service billing-service payment-service notification-service content-service journey-service reporting-service ai-gateway audit-service insurance-service approval-service

echo ==============================================
echo  Selfcare Platform - Docker Build
echo  Registry : %IMAGE_REGISTRY%
echo  Version  : %VERSION%
echo  Context  : %PLATFORM_DIR%
echo ==============================================

for %%S in (%SERVICES%) do (
  if exist "%PLATFORM_DIR%\backend\%%S\Dockerfile" (
    echo.
    echo Building %%S ...
    docker build --build-arg "MODULE=%%S" -t "%IMAGE_REGISTRY%/%%S:%VERSION%" -f "%PLATFORM_DIR%\backend\%%S\Dockerfile" "%PLATFORM_DIR%"
    if errorlevel 1 (
      echo ERROR: failed to build %%S
      exit /b 1
    )
    echo OK %%S
  ) else (
    echo WARNING: Dockerfile not found for %%S - skipping
  )
)

echo.
echo ==============================================
echo All images built.
echo Deploy with: docker compose up -d
echo ==============================================
endlocal
