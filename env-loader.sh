#!/usr/bin/env bash
# =====================================================================
# selfcare Environment Loader
# =====================================================================
# Sources the active .env.<SELFCARE_ENV> file and exports all variables.
# Usage:
#   source ./env-loader.sh        # uses $SELFCARE_ENV or defaults to dev
#   SELFCARE_ENV=stg source ./env-loader.sh
#   ./env-loader.sh --print       # print loaded vars (for debugging)
# =====================================================================

set -euo pipefail

# Resolve active environment
SELFCARE_ENV="${SELFCARE_ENV:-dev}"

case "$SELFCARE_ENV" in
  dev|stg|reg|prod) ;;
  *)
    echo "ERROR: SELFCARE_ENV must be one of: dev, stg, reg, prod (got: $SELFCARE_ENV)" >&2
    exit 1
    ;;
esac

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ENV_FILE="${SCRIPT_DIR}/.env.${SELFCARE_ENV}"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: Environment file not found: $ENV_FILE" >&2
  echo "       Create it from .env.example or set SELFCARE_ENV to a valid value." >&2
  exit 1
fi

# Load environment
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

# If only print mode, dump and exit
if [[ "${1:-}" == "--print" ]]; then
  echo "Loaded environment: $SELFCARE_ENV"
  echo "From: $ENV_FILE"
  echo "---"
  env | grep -E '^[A-Z_]+=' | sort
  exit 0
fi

echo "[env-loader] Loaded SELFCARE_ENV=${SELFCARE_ENV} from ${ENV_FILE}"
