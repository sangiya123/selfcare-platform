#!/bin/bash
# Updates all backend service Dockerfiles to the proven api-gateway pattern.
# Strategy:
#   1. Install Maven 3.9.9 (Java 25 compatible)
#   2. Copy entire project (parent pom needs industry-packs to exist)
#   3. Install parent pom (non-recursive)
#   4. Install platform-common to local .m2
#   5. Build only the target service module
#
# Usage (from selfcare-platform/):
#   bash backend/update-dockerfiles.sh

set -e

SERVICES=(
  "account-entitlement-service"
  "admin-identity-service"
  "ai-gateway"
  "approval-service"
  "audit-service"
  "billing-service"
  "config-tenant-service"
  "content-service"
  "customer-identity-service"
  "dashboard-bff"
  "insurance-service"
  "journey-service"
  "notification-service"
  "payment-service"
  "product-service"
  "reporting-service"
  "usage-service"
)

TEMPLATE='# Multi-stage Dockerfile for OMOBIO microservices
# Build context: repository root (selfcare-platform)
# Strategy:
#   1. Install Maven 3.9.9 (Java 25 compatible — no Alpine package version skew)
#   2. Copy entire project tree (parent pom.xml needs industry-packs to exist)
#   3. Install parent pom (non-recursive) so child modules can find it
#   4. Install platform-common to local .m2 repo
#   5. Build only the target service module
#
# Usage (from selfcare-platform/):
#   docker build -t omobio/SERVICE_NAME:1.0.0 -f backend/SERVICE_NAME/Dockerfile .

FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

# Install Maven 3.9.9 from Apache archive
ARG MAVEN_VERSION=3.9.9
RUN wget -q https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz -O /tmp/mvn.tgz && \
    tar -xzf /tmp/mvn.tgz -C /opt && \
    ln -s /opt/apache-maven-${MAVEN_VERSION}/bin/mvn /usr/local/bin/mvn && \
    rm /tmp/mvn.tgz && \
    mvn --version

# Copy project tree. The .dockerignore at the project root excludes
# node_modules, target/, IDE files, and docs to keep the context small.
COPY . .

ARG MODULE=SERVICE_NAME

# Install parent pom (no modules) so child modules can find it
RUN mvn -s backend/settings.xml -f backend/pom.xml -N install -DskipTests -B \
  -Dmaven.repo.local=/build/.m2

# Install platform-common to the local .m2 repo (backend service dependency)
RUN mvn -s backend/settings.xml -f backend/pom.xml -pl platform-common install -DskipTests -B \
  -Dmaven.repo.local=/build/.m2

# Build ONLY the target service module
RUN mvn -s backend/settings.xml -f backend/pom.xml -pl ${MODULE} package -Dmaven.test.skip=true -B \
  -Dmaven.repo.local=/build/.m2

# ─── Stage 2: Runtime ──────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -g 1000 -S omobio && adduser -u 1000 -S omobio -G omobio

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ARG MODULE=SERVICE_NAME
COPY --from=builder /build/backend/${MODULE}/target/${MODULE}-*-SNAPSHOT.jar app.jar

USER omobio
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
'

for SERVICE in "${SERVICES[@]}"; do
  DOCKERFILE_PATH="backend/${SERVICE}/Dockerfile"
  if [ -f "$DOCKERFILE_PATH" ]; then
    echo "$TEMPLATE" | sed "s/SERVICE_NAME/${SERVICE}/g" > "$DOCKERFILE_PATH"
    echo "✓ Updated: $DOCKERFILE_PATH"
  else
    echo "✗ Skipped (not found): $DOCKERFILE_PATH"
  fi
done

echo ""
echo "All 17 service Dockerfiles updated to the proven pattern."
echo "Next step: run scripts/build-images.sh to build all images."
