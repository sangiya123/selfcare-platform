#!/usr/bin/env bash
# =====================================================================
# OMOBIO — Universal env launcher for Docker / K8s / bare-metal
# =====================================================================
# Usage:
#   ./env.sh dev    backend-run
#   ./env.sh stg    admin-build
#   ./env.sh prod   mobile-build-android
#
# Or with explicit env:  OMOBIO_ENV=prod ./env.sh <command...>
# =====================================================================

set -euo pipefail

# Pick env
ENV="${1:-}"
shift || true
CMD="${@:-env-show}"

if [[ -z "$ENV" ]]; then
  if [[ -n "${OMOBIO_ENV:-}" ]]; then
    ENV="$OMOBIO_ENV"
  else
    echo "Usage: $0 <env> <command...>"
    echo "       OMOBIO_ENV must be one of: dev, stg, reg, prod"
    exit 1
  fi
fi

# Source the env file
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/.env.${ENV}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# Also expose as Spring profile / Vite mode
export SPRING_PROFILES_ACTIVE="$ENV"
export VITE_APP_ENV="$ENV"
export NODE_ENV="$ENV"

# Run the command
echo "[env.sh] OMOBIO_ENV=$ENV  CMD=$CMD"
exec $CMD
