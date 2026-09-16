# Selfcare Platform — Build & Deploy Commands

Complete reference for building, deploying, and operating the platform on
Docker Desktop (docker-compose or Kubernetes) plus the local CI stack
(Jenkins + SonarQube). Run commands from the `selfcare-platform/` directory
unless stated otherwise.

---

## 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Docker Desktop | 4.x, Kubernetes ENABLED | `docker version` , `kubectl config get-contexts` |
| kubectl | bundled with Docker Desktop | `kubectl version` |
| Java | 25 (only for builds outside Docker) | `java -version` |
| Maven | 3.9.9 via build image | — |

One-time environment check:

```bash
scripts/setup-k8s.sh        # enable K8s in Docker Desktop + set context
kubectl config use-context docker-desktop
kubectl get nodes
```

---

## 2. Build images

### Option A — Build all 18 services (shell)

```bash
scripts/build-images.sh              # tags: selfcare/<service>:1.0.0
scripts/build-images.sh --no-cache   # full rebuild
```

### Option B — Build all 18 services (Windows batch)

```bat
scripts\build-images.bat             # tags: localhost:5000/selfcare/<service>:1.0.0
```

### Option C — Via docker-compose

```bash
docker compose build                 # images: selfcare-platform-<service>:latest
docker compose build --no-cache
```

### Option D — Single service

```bash
docker build -t selfcare/approval-service:1.0.0 \
  -f backend/approval-service/Dockerfile \
  --build-arg MODULE=approval-service .
```

### Retag an existing build for the K8s manifests

The Kubernetes manifests reference the `:local-v2` / `:local-v4` tags:

```bash
docker tag selfcare/approval-service:1.0.0  selfcare/approval-service:local-v2
docker tag selfcare/api-gateway:1.0.0       selfcare/api-gateway:local-v4
```

---

## 3. Deploy — Docker Compose (infrastructure ONLY)

The compose file holds ONLY stateful infrastructure + database admin UIs:
MongoDB, Mongo-Express, Redis, MySQL, PHPMyAdmin, Zookeeper, Kafka.
Microservices NEVER run in compose.

```bash
scripts/start-local.sh                 # or: scripts\start-local.bat
docker compose ps
docker compose logs -f kafka
docker compose up -d mongodb redis kafka zookeeper mysql   # infra only
docker compose down                    # stop (keep volumes)
docker compose down -v                 # stop + delete volumes
```

Access (local only — never publish these ports to the internet):
- Mongo Express http://localhost:8081 (admin/Selfcare_M0ng0Expr3ss_Pa55w0rd!2026)
- PHPMyAdmin    http://localhost:8080 (server: mysql / user: selfcare)

No application service exposes a port in compose — the 19 microservices and
the admin portal run on Kubernetes (section 4).

---

## 4. Deploy — Kubernetes (Docker Desktop)

Infrastructure lives in compose (section 3) and must be running. Deploy the
19 microservices + admin portal ONE BY ONE via Helm (`deploy.isolated=true`):

```bash
# All microservices sequentially + admin portal
scripts/deploy-k8s.sh --env dev --local

# Promote ONE service (Jenkins does exactly this per service)
scripts/deploy-k8s.sh --env dev --local --service config-tenant-service
```

The baseline K8s objects (namespace `selfcare-dev`, `selfcare-infra-creds`
Secret, ConfigMaps) are applied automatically by the script from
`deploy/kubernetes/`.

Monitor / debug:

```bash
kubectl get pods -n selfcare-dev
kubectl get svc -n selfcare-dev
kubectl logs -n selfcare-dev deployment/api-gateway --tail=100
kubectl rollout status deployment/api-gateway -n selfcare-dev --timeout=180s
kubectl describe pod -n selfcare-dev -l app.kubernetes.io/name=api-gateway
kubectl port-forward -n selfcare-dev svc/api-gateway 8080:8080
```

### Build & deploy a single service (Docker Desktop K8s — recommended)

Docker Desktop's K8s runs a separate containerd store, so re-tagging an image
with the same tag never propagates to pods. Always use a **new unique tag**
per rebuild:

```bash
# Build from the fat jar in <service>/target (run `mvn install` first)
docker build -t selfcare/<service>:k8s-N -f C:\Users\sangiya\AppData\Local\Temp\opencode\generic.Dockerfile backend\<service>\target

# Roll the deployment onto the new image
kubectl set image deployment/<service> <service>=selfcare/<service>:k8s-N -n selfcare-dev
kubectl rollout status deployment/<service> -n selfcare-dev --timeout=180s
```

Build one service from Maven (no tests / jacoco):

```powershell
& C:\Users\sangiya\AppData\Local\Temp\opencode\maven\apache-maven-3.9.9\bin\mvn.cmd "-f" backend\pom.xml "-pl" <service> "install" "-Dmaven.test.skip=true" "-Djacoco.skip=true" "-B" "-s" backend\settings.xml
```

`generic.Dockerfile` (runtime jar → image):

```dockerfile
FROM eclipse-temurin:25-jre-alpine
COPY *.jar /app/app.jar
WORKDIR /app
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Access (NodePort):
- API Gateway  http://localhost:30080
- Studio       http://localhost:30081
- Support svc  http://localhost:30098

Smoke test:

```bash
scripts/verify.sh
```

Tear down:

```bash
kubectl delete ns selfcare-dev
```

---

## 5. CI stack — Jenkins + SonarQube (Kubernetes via Helm)

Jenkins and SonarQube run on Kubernetes via the `ci/helm` chart (not
docker-compose). Jenkins performs the one-by-one microservice promotion with
the full pipeline in `ci/jenkins/Jenkinsfile`.

```bash
# 1. Configure credentials (ci/.env.ci)
Copy-Item ci\.env.ci.example ci\.env.ci
#    set JENKINS_ADMIN_PASSWORD, SONAR_ADMIN_PASSWORD, SONAR_TOKEN

# 2. Deploy CI stack to K8s
helm upgrade --install selfcare-ci ci/helm -n selfcare-ci --create-namespace
```

Access:
- SonarQube  http://localhost:9000  (first login admin/Selfcare_S0n4r_Adm1n_Pa55w0rd!2026 → create token)
- Jenkins    http://localhost:8080  (admin / Selfcare_J3nk1ns_Adm1n_Pa55w0rd!2026)

Jenkins job: create a Pipeline job from the GitHub repo with script path
`Jenkinsfile`. Jenkins has kubectl + docker.sock mounted so it can deploy to
the `docker-desktop` K8s context.

---

## 6. Maven builds (outside Docker)

```bash
cd backend
mvn clean install -DskipTests        # build everything
mvn test                             # run tests
mvn -P ci verify                     # full CI gate: checkstyle, spotbugs, pmd, owasp, jacoco
mvn -P sonar sonar:sonar             # sonar scan
```

---

## 7. Admin portal / Mobile

```bash
make admin-install  && make admin-run    # React dev server
make admin-build                        # production build
make mobile-install && make mobile-run  # React Native
```

---

## 8. Image / volume cleanup

```bash
docker image prune -a                  # remove unused images
docker system prune -a                 # images + containers + build cache
docker system df                       # disk usage
docker images --format "{{.Repository}}:{{.Tag}}"
```

Keep the images referenced by `deploy/kubernetes/*.yaml`:

```bash
kubectl get deployments -n selfcare -o jsonpath="{.items[*].spec.template.spec.containers[*].image}"
```

---

## 9. Useful aliases

```bash
alias k='kubectl -n selfcare'
alias kl='kubectl -n selfcare logs --tail=50'
```