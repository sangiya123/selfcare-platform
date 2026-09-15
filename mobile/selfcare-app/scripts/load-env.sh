#!/usr/bin/env bash
# =====================================================================
# Mobile App — Load Environment Script
# =====================================================================
# Usage:
#   ./scripts/load-env.sh dev      # load .env.dev → .env
#   ./scripts/load-env.sh stg      # load .env.stg → .env
#   ./scripts/load-env.sh reg       # load .env.reg → .env
#   ./scripts/load-env.sh prod     # load .env.prod → .env
#   ./scripts/load-env.sh          # interactive — prompts for env
#
# This script runs before:
#   - react-native start (loads .env into the bundler)
#   - react-native run-android / run-ios
#   - Fastlane / CD pipelines
# =====================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(dirname "$SCRIPT_DIR")"
ENV="${1:-}"

if [[ -z "$ENV" ]]; then
  echo "Usage: $0 <env>  (dev | stg | reg | prod)"
  echo "Current SELFCARE_ENV=${SELFCARE_ENV:-not set}"
  echo ""
  echo "Select environment:"
  select ENV in dev stg reg prod; do
    [[ -n "$ENV" ]] && break
  done
fi

ENV_FILE="${APP_DIR}/.env.${ENV}"
TARGET_FILE="${APP_DIR}/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: Environment file not found: $ENV_FILE" >&2
  echo "Create it from .env.example first." >&2
  exit 1
fi

# Resolve ${SECRET_*} placeholders from the current environment
# (In CI, these are injected from Vault / AWS SM)
envsubst < "$ENV_FILE" > "$TARGET_FILE"

echo "[load-env] Loaded ${ENV} → $TARGET_FILE"
echo "[load-env] API_BASE_URL=$(grep MOBILE_API_BASE_URL "$TARGET_FILE" | cut -d= -f2)"
echo "[load-env] TENANT_ID=$(grep MOBILE_TENANT_ID "$TARGET_FILE" | cut -d= -f2)"
