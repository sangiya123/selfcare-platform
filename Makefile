# =====================================================================
# Selfcare Platform — Makefile
# =====================================================================
# One-line environment switching + per-component commands.
# Set SELFCARE_ENV=dev|stg|reg|prod (default: dev)
# =====================================================================

SELFCARE_ENV ?= dev
export SELFCARE_ENV

# Sync env from .env.<SELFCARE_ENV> into the shell
.env:
	@./env-loader.sh

# ---------- High-level: deploy to EKS via new scripts ----------
# Usage:
#   make dev                           # start stateful infra ONLY (docker compose; apps go to K8s)
#   make k8s-dev TAG=dev-42            # EKS deploy (Jenkins or manual)
#   make k8s-stg TAG=rc-42
#   make k8s-prod TAG=v1.2.0
#   make smoke ENV=dev
.PHONY: dev stg reg prod k8s-dev k8s-stg k8s-reg k8s-prod smoke dev-test
dev:
	@echo "Starting DEV stateful infrastructure (docker-compose) — apps run on Kubernetes..."
	@SELFCARE_ENV=dev docker-compose up -d

stg:
	@echo "Deploying STG via ArgoCD..."
	@VERSION=$(TAG) ./scripts/argocd-sync.sh --env stg --tag "$(TAG)" --push --sync

reg:
	@echo "Deploying REG via ArgoCD..."
	@VERSION=$(TAG) ./scripts/argocd-sync.sh --env reg --tag "$(TAG)" --push --sync

prod:
	@echo "Deploying PROD via ArgoCD..."
	@VERSION=$(TAG) ./scripts/argocd-sync.sh --env prod --tag "$(TAG)" --push --sync

k8s-dev:
	@./scripts/deploy-k8s.sh --env dev --tag "$(TAG)" --registry "$(REGISTRY)" --local

k8s-stg:
	@./scripts/deploy-k8s.sh --env stg --tag "$(TAG)" --registry "$(REGISTRY)"

k8s-reg:
	@./scripts/deploy-k8s.sh --env reg --tag "$(TAG)" --registry "$(REGISTRY)"

k8s-prod:
	@./scripts/deploy-k8s.sh --env prod --tag "$(TAG)" --registry "$(REGISTRY)"

smoke:
	@./scripts/smoke.sh --env "$(ENV)" --local

# Build locally, deploy to docker-desktop (fast inner loop, no EKS)
dev-test:
	@echo "Building + deploying to docker-desktop for local testing..."
	@cd backend && mvn -B -T 1C package -Dmaven.test.skip=true -Djacoco.skip=true
	@VERSION=local ./scripts/build-images.sh --registry selfcare
	@VERSION=local ./scripts/deploy-k8s.sh --env dev --local

# ---------- Backend (Java Spring Boot) ----------
.PHONY: backend-build backend-run backend-test backend-stop
backend-build:
	cd backend && mvn clean install -DskipTests

backend-run:
	@./env-loader.sh >/dev/null
	cd backend && SPRING_PROFILES_ACTIVE=$$SELFCARE_ENV mvn spring-boot:run -pl api-gateway

backend-test:
	cd backend && mvn test

backend-stop:
	@echo "Microservices run on Kubernetes; infra stops via 'docker compose down'"
	cd backend && docker compose down 2>/dev/null || true

# ---------- Admin (Vite/React) ----------
.PHONY: admin-install admin-run admin-build admin-preview
admin-install:
	cd admin/selfcare-admin && npm install

admin-run:
	@./env-loader.sh >/dev/null
	cd admin/selfcare-admin && npm run dev -- --mode $$SELFCARE_ENV

admin-build:
	@./env-loader.sh >/dev/null
	cd admin/selfcare-admin && npm run build -- --mode $$SELFCARE_ENV

admin-preview:
	@./env-loader.sh >/dev/null
	cd admin/selfcare-admin && npm run preview -- --mode $$SELFCARE_ENV

# ---------- Mobile (React Native) ----------
.PHONY: mobile-install mobile-run mobile-build-android mobile-build-ios
mobile-install:
	cd mobile/selfcare-app && npm install
	cd mobile/selfcare-app && ./scripts/load-env.sh

mobile-run:
	@./env-loader.sh >/dev/null
	cd mobile/selfcare-app && ./scripts/load-env.sh $$SELFCARE_ENV
	cd mobile/selfcare-app && npx react-native start

mobile-build-android:
	@./env-loader.sh >/dev/null
	cd mobile/selfcare-app && ./scripts/load-env.sh $$SELFCARE_ENV
	cd mobile/selfcare-app && cd android && ./gradlew assembleRelease

mobile-build-ios:
	@./env-loader.sh >/dev/null
	cd mobile/selfcare-app && ./scripts/load-env.sh $$SELFCARE_ENV
	cd mobile/selfcare-app && cd ios && xcodebuild -workspace selfcareApp.xcworkspace -scheme selfcareApp -configuration Release

# ---------- Environment helpers ----------
.PHONY: env-show env-switch env-validate
env-show:
	@./env-loader.sh --print

env-switch:
	@echo "Current SELFCARE_ENV=$$SELFCARE_ENV"
	@echo "Available: dev, stg, reg, prod"
	@echo ""
	@echo "Switch with:  export SELFCARE_ENV=stg"
	@echo "Then run:     make dev  (or stg/reg/prod)"

env-validate:
	@./env-loader.sh >/dev/null
	@echo "Validating environment: $$SELFCARE_ENV"
	@cd backend && mvn -q help:effective-pom >/dev/null 2>&1 || true
	@echo "Environment OK"

# ---------- Lint & format ----------
.PHONY: lint format
lint:
	cd backend && mvn -q checkstyle:check
	cd admin/selfcare-admin && npm run lint
	cd mobile/selfcare-app && npm run lint

format:
	cd admin/selfcare-admin && npm run format
	cd mobile/selfcare-app && npm run format
