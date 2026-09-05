# OMOBIO Selfcare Platform — Developer Setup Guide

This guide walks through setting up a local development environment
for the OMOBIO Selfcare Platform from scratch.

## Prerequisites

| Tool | Version | Required For |
|------|---------|--------------|
| Java | 25 (Temurin) | Backend |
| Maven | 3.9+ | Backend |
| Node.js | 20 LTS | Admin + Mobile |
| npm | 10+ | Admin + Mobile |
| Docker | 24+ | All (local infra) |
| Docker Compose | v2 | All |
| MongoDB | 7 (or use Docker) | Backend |
| MySQL | 8 (or use Docker) | Backend |
| Redis | 7 (or use Docker) | Backend |
| Kafka | 3.6 (or use Docker) | Backend |
| Android Studio | Hedgehog+ | Mobile (Android) |
| Xcode | 15+ | Mobile (iOS, macOS only) |

## Quick start

```bash
# 1. Clone
git clone <repo>
cd <repo>

# 2. Start infrastructure (Mongo, MySQL, Redis, Kafka, ELK, Prometheus, Grafana)
docker compose -f selfcare-platform/docker-compose.yml up -d

# 3. Wait ~30s for all services to be healthy
docker compose ps

# 4. Seed MongoDB with default tenants + layouts
mongosh mongodb://localhost:27017/selfcare_config \
  < selfcare-platform/backend/config-tenant-service/src/main/resources/seed/001_seed_tenants.js
mongosh mongodb://localhost:27017/selfcare_config \
  < selfcare-platform/backend/config-tenant-service/src/main/resources/seed/002_seed_layouts.js

# 5. Start backend (in another terminal)
cd selfcare-platform/backend
mvn -pl api-gateway -am spring-boot:run
# In another terminal:
mvn -pl config-tenant-service spring-boot:run
# ... repeat for each service
```

Or run all services with docker compose:
```bash
docker compose -f selfcare-platform/backend/deploy/docker-compose.dev.yml up
```

## Frontend

### Admin (Selfcare Studio)
```bash
cd selfcare-platform/admin/selfcare-studio
cp .env.example .env.local
npm install
npm run dev
# Open http://localhost:5173
```

### Mobile (Selfcare App)
```bash
cd selfcare-platform/mobile/selfcare-app
# Edit .env.dev to point at your local API
npm install

# iOS (macOS only)
cd ios && pod install && cd ..
npx react-native run-ios

# Android
npx react-native run-android
```

## IDE Setup

### IntelliJ IDEA (recommended for backend)
- Install plugins: Lombok, SonarLint, Checkstyle, .ignore
- Import `backend/pom.xml` as a multi-module project
- Enable annotation processing (Settings → Build → Compiler → Annotation Processors)

### VS Code (recommended for mobile + admin)
- Install: ESLint, Prettier, Tailwind CSS IntelliSense, React Native Tools
- Use workspace: `selfcare-platform.code-workspace`

## Testing

### Backend unit tests
```bash
cd selfcare-platform/backend
mvn test
```

### Backend conformance tests
```bash
cd selfcare-platform/tests/conformance
mvn test
```

### Admin unit tests (Vitest)
```bash
cd selfcare-platform/admin/selfcare-studio
npm test
```

### Mobile unit tests (Jest)
```bash
cd selfcare-platform/mobile/selfcare-app
npm test
```

### Mobile E2E (Detox)
```bash
cd selfcare-platform/mobile/selfcare-app
npm run e2e:ios       # or e2e:android
```

## Configuration

### Environment variables (backend)
Set in `~/.zshrc` or your shell profile, or use `.env` for docker:
```bash
export MONGODB_URI=mongodb://localhost:27017/selfcare_config
export MYSQL_URL=jdbc:mysql://localhost:3306/selfcare
export REDIS_URL=redis://localhost:6379
export KAFKA_BROKERS=localhost:9092
export ANTHROPIC_API_KEY=sk-ant-...
export OPENAI_API_KEY=sk-...
```

### Environment variables (mobile)
Edit `selfcare-platform/mobile/selfcare-app/.env.dev`:
```
OMOBIO_ENV=dev
MOBILE_ENV=development
MOBILE_API_BASE_URL=http://localhost:8080
MOBILE_TENANT_ID=dialog-lk
```

### Tenant configuration
All tenant config is in MongoDB. Use the admin portal to manage it.
To bootstrap a new tenant manually, copy a seed file and adjust.

## Debugging tips

### Backend
- Add `--debug` to spring-boot:run for remote JDWP
- Spring Boot DevTools is enabled in dev profile
- Use `?spring.profiles.active=local` to switch profiles

### Mobile
- Flipper is enabled in dev (set MOBILE_FLIPPER_ENABLED=true)
- Use Chrome DevTools at chrome://inspect for JS debugging
- React DevTools: `npm install -g react-devtools`

### AI gateway
- LLM requests are logged with full request/response (excluding PII)
- Set log level: `LOGGING_LEVEL_COM_OMOBIO_AI=DEBUG`
- Mock mode: leave `ANTHROPIC_API_KEY` and `OPENAI_API_KEY` empty to use fallback only

## Common issues

### "Port already in use"
Find and kill the process:
```bash
lsof -ti:8080 | xargs kill -9
```

### "JWT signature invalid"
Clear Redis: `redis-cli FLUSHDB`

### "MongoDB connection refused"
Check docker: `docker ps | grep mongo`
Restart: `docker compose restart mongodb`

### "Mobile app can't reach backend"
- Android emulator: use `http://10.0.2.2:8080` (not `localhost`)
- iOS simulator: `localhost:8080` works
- Real device: use the host's LAN IP

### "AI chat returns fallback"
This is expected behavior when no API key is configured. To enable real LLM:
- Set `ANTHROPIC_API_KEY` in environment
- Restart the AI gateway

## Where to go from here

- [Architecture Overview](architecture/ARCHITECTURE.md)
- [ADRs](adrs/)
- [AI Features Reference](backend/ai-gateway/AI_FEATURES.md)
- [Runbooks](observability/runbooks/)
- [Mobile SDK Reference](mobile/selfcare-app/MOBILE_SDK.md)
- [API Reference](api/API.md)
