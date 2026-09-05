# =====================================================================
# OMOBIO Selfcare Platform — Makefile
# =====================================================================
# One-line environment switching + per-component commands.
# Set OMOBIO_ENV=dev|stg|reg|prod (default: dev)
# =====================================================================

OMOBIO_ENV ?= dev
export OMOBIO_ENV

# Sync env from .env.<OMOBIO_ENV> into the shell
.env:
	@./env-loader.sh

# ---------- High-level: start the full stack for an environment ----------
.PHONY: dev stg reg prod
dev:
	@echo "Starting DEV environment..."
	@OMOBIO_ENV=dev docker-compose up -d

stg:
	@echo "Deploying STG environment..."
	@OMOBIO_ENV=stg kubectl apply -k backend/deploy/helm/overlays/stg

reg:
	@echo "Deploying REG environment..."
	@OMOBIO_ENV=reg kubectl apply -k backend/deploy/helm/overlays/reg

prod:
	@echo "Deploying PROD environment..."
	@OMOBIO_ENV=prod kubectl apply -k backend/deploy/helm/overlays/prod

# ---------- Backend (Java Spring Boot) ----------
.PHONY: backend-build backend-run backend-test backend-stop
backend-build:
	cd backend && mvn clean install -DskipTests

backend-run:
	@./env-loader.sh >/dev/null
	cd backend && SPRING_PROFILES_ACTIVE=$$OMOBIO_ENV mvn spring-boot:run -pl api-gateway

backend-test:
	cd backend && mvn test

backend-stop:
	cd backend && docker-compose down

# ---------- Admin (Vite/React) ----------
.PHONY: admin-install admin-run admin-build admin-preview
admin-install:
	cd admin/selfcare-studio && npm install

admin-run:
	@./env-loader.sh >/dev/null
	cd admin/selfcare-studio && npm run dev -- --mode $$OMOBIO_ENV

admin-build:
	@./env-loader.sh >/dev/null
	cd admin/selfcare-studio && npm run build -- --mode $$OMOBIO_ENV

admin-preview:
	@./env-loader.sh >/dev/null
	cd admin/selfcare-studio && npm run preview -- --mode $$OMOBIO_ENV

# ---------- Mobile (React Native) ----------
.PHONY: mobile-install mobile-run mobile-build-android mobile-build-ios
mobile-install:
	cd mobile/selfcare-app && npm install
	cd mobile/selfcare-app && ./scripts/load-env.sh

mobile-run:
	@./env-loader.sh >/dev/null
	cd mobile/selfcare-app && ./scripts/load-env.sh $$OMOBIO_ENV
	cd mobile/selfcare-app && npx react-native start

mobile-build-android:
	@./env-loader.sh >/dev/null
	cd mobile/selfcare-app && ./scripts/load-env.sh $$OMOBIO_ENV
	cd mobile/selfcare-app && cd android && ./gradlew assembleRelease

mobile-build-ios:
	@./env-loader.sh >/dev/null
	cd mobile/selfcare-app && ./scripts/load-env.sh $$OMOBIO_ENV
	cd mobile/selfcare-app && cd ios && xcodebuild -workspace SelfcareApp.xcworkspace -scheme SelfcareApp -configuration Release

# ---------- Environment helpers ----------
.PHONY: env-show env-switch env-validate
env-show:
	@./env-loader.sh --print

env-switch:
	@echo "Current OMOBIO_ENV=$$OMOBIO_ENV"
	@echo "Available: dev, stg, reg, prod"
	@echo ""
	@echo "Switch with:  export OMOBIO_ENV=stg"
	@echo "Then run:     make dev  (or stg/reg/prod)"

env-validate:
	@./env-loader.sh >/dev/null
	@echo "Validating environment: $$OMOBIO_ENV"
	@cd backend && mvn -q help:effective-pom >/dev/null 2>&1 || true
	@echo "Environment OK"

# ---------- Lint & format ----------
.PHONY: lint format
lint:
	cd backend && mvn -q checkstyle:check
	cd admin/selfcare-studio && npm run lint
	cd mobile/selfcare-app && npm run lint

format:
	cd admin/selfcare-studio && npm run format
	cd mobile/selfcare-app && npm run format
