# OMOBIO Selfcare Platform — Build & Deploy Commands

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
scripts/build-images.sh              # tags: omobio/<service>:1.0.0
scripts/build-images.sh --no-cache   # full rebuild
```

### Option B — Build all 18 services (Windows batch)

```bat
scripts\build-images.bat             # tags: localhost:5000/omobio/<service>:1.0.0
```

### Option C — Via docker-compose

```bash
docker compose build                 # images: selfcare-platform-<service>:latest
docker compose build --no-cache
```

### Option D — Single service

```bash
docker build -t omobio/approval-service:1.0.0 \
  -f backend/approval-service/Dockerfile \
  --build-arg MODULE=approval-service .
```

### Retag an existing build for the K8s manifests

The Kubernetes manifests reference the `:local-v2` / `:local-v4` tags:

```bash
docker tag omobio/approval-service:1.0.0  omobio/approval-service:local-v2
docker tag omobio/api-gateway:1.0.0       omobio/api-gateway:local-v4
```

---

## 3. Deploy — Docker Compose (local)

```bash
scripts/start-local.sh                 # or: scripts\start-local.bat
docker compose up -d                   # full stack incl. observability
docker compose up -d mongodb redis kafka zookeeper mysql   # infra only
docker compose ps
docker compose logs -f api-gateway
docker compose restart api-gateway
docker compose down                    # stop (keep volumes)
docker compose down -v                 # stop + delete volumes
```

Access:
- API Gateway  http://localhost:8080  (Swagger: /swagger-ui.html)
- Grafana      http://localhost:3000  (admin/admin)
- Prometheus   http://localhost:9090

Smoke test:

```bash
scripts/verify.sh
```

---

## 4. Deploy — Kubernetes (Docker Desktop)

Apply the manifests in order:

```bash
kubectl apply -f deploy/kubernetes/namespace.yaml            # ns: omobio
kubectl apply -f deploy/kubernetes/secrets.yaml
kubectl apply -f deploy/kubernetes/configmap.yaml
kubectl apply -f deploy/kubernetes/infra.yaml                # mongo/redis/mysql/kafka/zk
kubectl apply -f deploy/kubernetes/tenant-seeding-job.yaml   # seed dialog-lk
kubectl apply -f deploy/kubernetes/services.yaml             # 18 microservices
kubectl apply -f deploy/kubernetes/admin-portal.yaml         # selfcare-studio
```

Or use the all-in-one script (bash):

```bash
scripts/deploy-k8s.sh            # apply manifests only
scripts/deploy-k8s.sh --build    # build images first, then apply
```

Monitor / debug:

```bash
kubectl get pods -n omobio
kubectl get svc -n omobio
kubectl logs -n omobio deployment/api-gateway --tail=100
kubectl rollout status deployment/api-gateway -n omobio --timeout=180s
kubectl describe pod -n omobio -l app=api-gateway
kubectl port-forward -n omobio svc/api-gateway 8080:8080
```

### Build & deploy a single service (Docker Desktop K8s — recommended)

Docker Desktop's K8s runs a separate containerd store, so re-tagging an image
with the same tag never propagates to pods. Always use a **new unique tag**
per rebuild:

```bash
# Build from the fat jar in <service>/target (run `mvn install` first)
docker build -t omobio/<service>:k8s-N -f C:\Users\sangiya\AppData\Local\Temp\opencode\generic.Dockerfile backend\<service>\target

# Roll the deployment onto the new image
kubectl set image deployment/<service> <service>=omobio/<service>:k8s-N -n omobio
kubectl rollout status deployment/<service> -n omobio --timeout=180s
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
- Prometheus   http://localhost:30090
- Grafana      http://localhost:30300  (admin/admin)

Tear down:

```bash
kubectl delete ns omobio
```

---

## 5. CI stack — Jenkins + SonarQube (Docker Compose)

```bash
# 1. Configure (Windows PowerShell)
Copy-Item ci\.env.ci.example ci\.env.ci
#    set JENKINS_ADMIN_PASSWORD and SONAR_TOKEN in ci/.env.ci

# 2. Start (pulls SonarQube, builds Jenkins image, waits for health)
.\ci\start-local-ci.ps1
```

Manual equivalent:

```bash
docker compose --env-file ci/.env.ci -f ci/docker-compose.ci.yml up -d
```

Access:
- SonarQube  http://localhost:9000  (first login admin/admin → create token)
- Jenkins    http://localhost:8080  (admin / your JENKINS_ADMIN_PASSWORD)

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
kubectl get deployments -n omobio -o jsonpath="{.items[*].spec.template.spec.containers[*].image}"
```

---

## 9. Useful aliases

```bash
alias k='kubectl -n omobio'
alias kl='kubectl -n omobio logs --tail=50'
```