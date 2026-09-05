#!/usr/bin/env bash
# setup-k8s.sh — Enable Kubernetes in Docker Desktop + install kubectl
# Run this ONCE to configure your local K8s cluster.
#
# After running this, deploy with: ./scripts/deploy-k8s.sh --build

set -euo pipefail

echo "=============================================="
echo " OMOBIO — Docker Desktop K8s Setup"
echo "=============================================="

# Check if running on Windows (Git Bash / MSYS2)
if [[ "$(uname)" == MINGW* ]] || [[ "$(uname)" == MSYS* ]]; then
  echo ""
  echo "Detected: Windows (Git Bash / MSYS2)"
  echo ""
  echo "IMPORTANT: To enable Kubernetes in Docker Desktop:"
  echo ""
  echo "  1. Open Docker Desktop"
  echo "  2. Click the gear icon (Settings)"
  echo "  3. Go to 'Kubernetes'"
  echo "  4. Check 'Enable Kubernetes'"
  echo "  5. Click 'Apply & Restart'"
  echo "  6. Wait for Docker Desktop to restart"
  echo ""
  echo "After Docker Desktop restarts, Kubernetes will be available."
  echo "Then run: kubectl config use-context docker-desktop"
  echo "Then run: ./scripts/deploy-k8s.sh --build"
  echo ""

  # Try to detect if Docker Desktop K8s is already enabled
  if kubectl config get-contexts docker-desktop &>/dev/null; then
    echo "✓ docker-desktop context found — K8s may already be enabled!"
    kubectl config use-context docker-desktop
    echo "✓ Switched to docker-desktop context"
    echo ""
    kubectl get nodes
    echo ""
    echo "Kubernetes is ready. Run: ./scripts/deploy-k8s.sh --build"
  else
    echo "K8s not yet enabled. Please follow the steps above."
  fi

elif [[ "$(uname)" == Darwin ]]; then
  echo "Detected: macOS"
  echo ""
  echo "To enable Kubernetes:"
  echo "  Docker Desktop → Settings → Kubernetes → Enable Kubernetes → Apply & Restart"
  echo ""
  if kubectl config get-contexts docker-desktop &>/dev/null; then
    kubectl config use-context docker-desktop
    echo "✓ K8s is enabled and context set"
    kubectl get nodes
  fi

elif [[ "$(uname)" == Linux ]]; then
  echo "Detected: Linux"
  echo ""
  echo "To enable Kubernetes in Docker Desktop on Linux:"
  echo "  docker-desktop -> Settings → Kubernetes → Enable Kubernetes"
  echo ""
  if kubectl config get-contexts docker-desktop &>/dev/null; then
    kubectl config use-context docker-desktop
    echo "✓ K8s is enabled and context set"
    kubectl get nodes
  fi
fi

echo ""
echo "=============================================="
echo " Setup complete"
echo ""
echo " Next steps:"
echo "   1. Ensure Docker Desktop K8s is enabled"
echo "   2. kubectl config use-context docker-desktop"
echo "   3. ./scripts/deploy-k8s.sh --build"
echo "=============================================="
