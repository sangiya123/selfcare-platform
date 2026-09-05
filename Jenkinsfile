def serviceNames = [
    'api-gateway', 'config-tenant-service', 'customer-identity-service',
    'admin-identity-service', 'account-entitlement-service', 'dashboard-bff',
    'product-service', 'usage-service', 'billing-service', 'payment-service',
    'notification-service', 'content-service', 'journey-service',
    'reporting-service', 'ai-gateway', 'audit-service', 'insurance-service',
    'approval-service'
]

properties([
    pipelineTriggers([
        githubPush(),
        pollSCM('H/5 * * * *')
    ])
])

pipeline {
    agent {
        node {
            customWorkspace '/workspace'
        }
    }

    options {
        skipDefaultCheckout(true)
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 120, unit: 'MINUTES')
    }

    environment {
        CI_NETWORK = 'omobio-ci'
        WORKSPACE_VOLUME = 'omobio_jenkins_workspace'
        MAVEN_VOLUME = 'omobio_maven_cache'
        KUBE_CONTEXT = 'docker-desktop'
        IMAGE_NAMESPACE = 'omobio'
        SONAR_HOST_URL = 'http://sonarqube:9000'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git rev-parse --short HEAD'
            }
        }

        stage('Backend Unit Tests') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    sh '''
                      docker run --rm --network "$CI_NETWORK" \
                        --mount source="$WORKSPACE_VOLUME",target=/workspace \
                        --mount source="$MAVEN_VOLUME",target=/root/.m2 \
                        -w /workspace/backend eclipse-temurin:25-jdk-alpine sh -lc \
                        'apk add --no-cache maven && mvn -B -fae -DskipITs=true -DfailIfNoTests=false test'
                    '''
                }
            }
        }

        stage('Admin QA') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    sh '''
                      docker run --rm --network "$CI_NETWORK" \
                        --mount source="$WORKSPACE_VOLUME",target=/workspace \
                        -w /workspace/admin/selfcare-studio node:22-alpine sh -lc \
                        'npm ci && npm run typecheck && npm test -- --run'
                    '''
                }
            }
        }

        stage('Mobile QA') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    sh '''
                      docker run --rm --network "$CI_NETWORK" \
                        --mount source="$WORKSPACE_VOLUME",target=/workspace \
                        -w /workspace/mobile/selfcare-app node:22-alpine sh -lc \
                        'npm ci && npm run typecheck && npm test -- --ci --coverage --maxWorkers=2'
                    '''
                }
            }
        }

        stage('SonarQube Scan') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('local-sonarqube') {
                        sh '''
                          docker run --rm --network "$CI_NETWORK" \
                            --mount source="$WORKSPACE_VOLUME",target=/workspace \
                            --mount source="$MAVEN_VOLUME",target=/root/.m2 \
                            -w /workspace/backend \
                            -e SONAR_HOST_URL -e SONAR_TOKEN \
                            eclipse-temurin:25-jdk-alpine sh -lc \
                            'apk add --no-cache maven && mvn -B -Psonar -DskipTests verify sonar:sonar \
                              -Dsonar.projectKey=omobio-selfcare-backend \
                              -Dsonar.projectName="OMOBIO Selfcare Backend" \
                              -Dsonar.qualitygate.wait=false'

                          docker run --rm --network "$CI_NETWORK" \
                            --mount source="$WORKSPACE_VOLUME",target=/workspace \
                            -w /workspace sonarsource/sonar-scanner-cli:latest sonar-scanner \
                            -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" \
                            -Dsonar.projectKey=omobio-selfcare-admin \
                            -Dsonar.projectName="OMOBIO Selfcare Admin" \
                            -Dsonar.sources=admin/selfcare-studio/src \
                            -Dsonar.exclusions=**/node_modules/**,**/dist/**,**/coverage/** \
                            -Dsonar.javascript.lcov.reportPaths=admin/selfcare-studio/coverage/lcov.info

                          docker run --rm --network "$CI_NETWORK" \
                            --mount source="$WORKSPACE_VOLUME",target=/workspace \
                            -w /workspace sonarsource/sonar-scanner-cli:latest sonar-scanner \
                            -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" \
                            -Dsonar.projectKey=omobio-selfcare-mobile \
                            -Dsonar.projectName="OMOBIO Selfcare Mobile" \
                            -Dsonar.sources=mobile/selfcare-app/src \
                            -Dsonar.exclusions=**/node_modules/**,**/android/**,**/ios/**,**/coverage/** \
                            -Dsonar.javascript.lcov.reportPaths=mobile/selfcare-app/coverage/lcov.info
                        '''
                    }
                }
            }
        }

        stage('Sonar Quality Gate') {
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('SBOM and Security Scan') {
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    sh '''
                      docker run --rm --network "$CI_NETWORK" \
                        --mount source="$WORKSPACE_VOLUME",target=/workspace \
                        --mount source="$MAVEN_VOLUME",target=/root/.m2 \
                        -w /workspace/backend eclipse-temurin:25-jdk-alpine sh -lc \
                        'apk add --no-cache maven && mvn -B org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DskipTests'

                      docker run --rm \
                        --mount source="$WORKSPACE_VOLUME",target=/workspace \
                        anchore/grype:latest sbom:/workspace/backend/target/bom.json \
                        --output json --file /workspace/backend/target/grype.json --fail-on critical

                      docker run --rm \
                        --mount source="$WORKSPACE_VOLUME",target=/workspace \
                        zricethezav/gitleaks:latest detect --source /workspace --no-banner --redact

                      docker run --rm \
                        --mount source="$WORKSPACE_VOLUME",target=/workspace \
                        aquasec/trivy:latest fs --scanners vuln,secret,misconfig \
                        --severity CRITICAL,HIGH --exit-code 1 /workspace
                    '''
                }
            }
        }

        stage('Package and Build Images') {
            when {
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                sh '''
                  docker run --rm --network "$CI_NETWORK" \
                    --mount source="$WORKSPACE_VOLUME",target=/workspace \
                    --mount source="$MAVEN_VOLUME",target=/root/.m2 \
                    -w /workspace/backend eclipse-temurin:25-jdk-alpine sh -lc \
                    'apk add --no-cache maven && mvn -B -T 1C package -Dmaven.test.skip=true -Djacoco.skip=true'
                '''
                script {
                    env.IMAGE_TAG = "ci-${env.BUILD_NUMBER}"
                    serviceNames.each { service ->
                        sh """
                          set -eu
                          mkdir -p .ci-images/${service}
                          jar=\$(find backend/${service}/target -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)
                          test -n \"\$jar\"
                          cp \"\$jar\" .ci-images/${service}/app.jar
                          cp backend/Dockerfile.image .ci-images/${service}/Dockerfile
                          docker build -t ${IMAGE_NAMESPACE}/${service}:${IMAGE_TAG} .ci-images/${service}
                        """
                    }
                }
            }
        }

        stage('Deploy Docker Desktop Kubernetes') {
            when {
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                sh '''
                  kubectl --context "$KUBE_CONTEXT" apply -f deploy/kubernetes
                  for service in api-gateway config-tenant-service customer-identity-service \
                    admin-identity-service account-entitlement-service dashboard-bff product-service \
                    usage-service billing-service payment-service notification-service content-service \
                    journey-service reporting-service ai-gateway audit-service insurance-service approval-service; do
                    kubectl --context "$KUBE_CONTEXT" -n omobio set image deployment/$service \
                      $service=$IMAGE_NAMESPACE/$service:$IMAGE_TAG
                    kubectl --context "$KUBE_CONTEXT" -n omobio rollout status deployment/$service --timeout=10m
                  done
                '''
            }
        }

        stage('Deployment Smoke QA') {
            when {
                expression { currentBuild.currentResult == 'SUCCESS' }
            }
            steps {
                sh '''
                  kubectl --context "$KUBE_CONTEXT" -n omobio get pods
                  kubectl --context "$KUBE_CONTEXT" -n omobio get deploy -o wide
                '''
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml,**/junit*.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'backend/target/bom.json,backend/target/grype.json,**/coverage/**'
        }
        cleanup {
            cleanWs(deleteDirs: false, disableDeferredWipeout: true)
        }
    }
}
