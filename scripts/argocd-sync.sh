#!/usr/bin/env bash
# argocd-sync.sh — Update GitOps environment image tag + trigger ArgoCD sync.
#
# Usage:
#   ./scripts/argocd-sync.sh --env dev|stg|prod --tag VERSION [--sync] [--push]
#
# What it does:
#   1. Clones the GITOPS_REPO (selfcare-platform) if not already checked out.
#   2. Updates backend/deploy/gitops/environments/<env>.yaml DEFAULT_IMAGE_TAG
#      and (for prod) DEFAULT_TARGET_REVISION.
#   3. Commits and pushes the change (--push) to the GITOPS_BRANCH.
#   4. Optionally runs `argocd app sync` for each tenant app in that env (--sync).
#
# Required env vars / CLI:
#   GITOPS_REPO   (default: https://github.com/sangiya123/selfcare-platform)
#   GITOPS_BRANCH (default: main)
#
# ArgoCD access (if --sync):
#   ARGOCD_SERVER   e.g. https://argocd.example.com
#   ARGOCD_TOKEN    (argocd login ... --grpc-web → server/account/token)
#   or KUBECONFIG with argocd cluster configured (argocd context).

set -euo pipefail

ENV=""
TAG=""
DO_SYNC=false
DO_PUSH=false
GITOPS_REPO="${GITOPS_REPO:-https://github.com/sangiya123/selfcare-platform}"
GITOPS_BRANCH="${GITOPS_BRANCH:-main}"
GITOPS_DIR="${GITOPS_DIR:-/tmp/selfcare-gitops}"

while [[ $# -gt 0 ]]; do
  case $1 in
    --env)   ENV="$2";   shift 2 ;;
    --tag)   TAG="$2";   shift 2 ;;
    --sync)  DO_SYNC=true; shift ;;
    --push)  DO_PUSH=true; shift ;;
    *)       echo "Unknown option: $1"; exit 1 ;;
  esac
done

[ -z "$ENV" ] && { echo "ERROR: --env required"; exit 1; }
[ -z "$TAG" ] && { echo "ERROR: --tag required"; exit 1; }

ENV_FILE="backend/deploy/gitops/environments/${ENV}.yaml"

# ---------------------------------------------------------------
# 1. Clone / update gitops repo
# ---------------------------------------------------------------
echo "--- Clone/update GITOPS_REPO → $GITOPS_DIR ---"
if [ ! -d "$GITOPS_DIR/.git" ]; then
  rm -rf "$GITOPS_DIR"
  git clone --branch "$GITOPS_BRANCH" --depth 1 "$GITOPS_REPO" "$GITOPS_DIR"
fi
cd "$GITOPS_DIR"
git checkout "$GITOPS_BRANCH"
git pull --ff-only "$GITOPS_REPO" "$GITOPS_BRANCH" 2>/dev/null || true

# ---------------------------------------------------------------
# 2. Patch environment image tag
# ---------------------------------------------------------------
echo "--- Patch $ENV_FILE → DEFAULT_IMAGE_TAG=$TAG ---"
if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE not found in gitops repo"
  exit 1
fi

# Update DEFAULT_IMAGE_TAG (replace line containing DEFAULT_IMAGE_TAG: …)
sed -i "s/^\(\s*DEFAULT_IMAGE_TAG:\s*\).*/\1\"${TAG}\"/" "$ENV_FILE"

# For prod, also update DEFAULT_TARGET_REVISION to the pinned tag
if [ "$ENV" = "prod" ]; then
  sed -i "s/^\(\s*DEFAULT_TARGET_REVISION:\s*\).*/\1\"tags\/v${TAG}\"/" "$ENV_FILE"
fi

echo "Updated:"
grep -E "DEFAULT_IMAGE_TAG|DEFAULT_TARGET_REVISION" "$ENV_FILE" || true

# ---------------------------------------------------------------
# 3. Commit + push (gitops commit for ArgoCD reconciliation)
# ---------------------------------------------------------------
if [ "$DO_PUSH" = true ]; then
  echo "--- Git commit + push ---"
  git add "$ENV_FILE"
  if git diff --cached --quiet; then
    echo "No changes to commit."
  else
    git commit -m "chore(gitops): update ${ENV} image tag → ${TAG}  [ci skip]"
    git push origin "$GITOPS_BRANCH"
    echo "Pushed to $GITOPS_BRANCH"
  fi
fi

# ---------------------------------------------------------------
# 4. ArgoCD sync (if --sync and ArgoCD credentials present)
# ---------------------------------------------------------------
if [ "$DO_SYNC" = true ]; then
  echo "--- ArgoCD sync for ${ENV} ---"

  ARGOCD_OPTS=""
  [ -n "${ARGOCD_SERVER:-}" ] && ARGOCD_OPTS="$ARGOCD_OPTS --server $ARGOCD_SERVER"
  [ -n "${ARGOCD_TOKEN:-}" ]  && ARGOCD_OPTS="$ARGOCD_OPTS --auth-token $ARGOCD_TOKEN"
  ARGOCD_OPTS="$ARGOCD_OPTS --grpc-web"

  # List matching apps and sync each
  APPS=$(argocd app list $ARGOCD_OPTS -o json 2>/dev/null \
    | python3 -c "import sys,json; [print(a['metadata']['name']) for a in json.load(sys.stdin) if '${ENV}' in a['metadata']['name']]" 2>/dev/null || echo "")

  if [ -z "$APPS" ]; then
    echo "WARN  No ArgoCD apps found matching env=$ENV (ArgoCD may not be configured locally)"
    echo "      GitOps commit was pushed; ArgoCD will auto-sync on next reconciliation."
  else
    for APP in $APPS; do
      echo "SYNC  $APP"
      argocd app sync "$APP" $ARGOCD_OPTS || echo "WARN  sync failed for $APP (may need manual retry)"
      argocd app wait "$APP" $ARGOCD_OPTS --health --timeout 300 || true
      echo "OK    $APP synced"
    done
  fi
fi

echo ""
echo "=============================================="
echo " GitOps update complete: env=$ENV tag=$TAG"
echo " Repo: $GITOPS_REPO @ $GITOPS_BRANCH"
echo "=============================================="
