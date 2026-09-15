// ============================================================================
// Selfcare Platform — Master CI/CD Pipeline (AEE Flow Matrix)
// ============================================================================
//
// GUI: Jenkins "Build with Parameters" shows:
//   1. Environment dropdown  (dev / stg / reg / prod)
//   2. 15 flow checkboxes that AUTO-UPDATE when you pick the environment
//   3. User can flip any checkbox before clicking Build
//
// CONFIG: edit ci/jenkins/flow-config.json to change default ON/OFF per env.
//
// PREREQUISITE: Jenkins → Manage Plugins → install "Active Choices"
//   https://plugins.jenkins.io/uno-choice/
// ============================================================================

def serviceNames = [
    'api-gateway', 'config-tenant-service', 'customer-identity-service',
    'admin-identity-service', 'account-entitlement-service', 'dashboard-bff',
    'product-service', 'usage-service', 'billing-service', 'payment-service',
    'notification-service', 'content-service', 'journey-service',
    'reporting-service', 'ai-gateway', 'audit-service', 'insurance-service',
    'approval-service'
]

properties([
    parameters([
        // ─── Environment selector (triggers all reactive params) ──────────
        choice(
            name: 'DEPLOY_ENV',
            choices: ['dev', 'stg', 'reg', 'prod'],
            description: 'Pick environment — flow checkboxes below auto-update.'
        ),
        string(
            name: 'RELEASE_TAG',
            defaultValue: '',
            description: 'Immutable release tag for reg/prod (e.g. 1.2.0). Ignored for dev/stg.'
        ),

        // ─── Flow toggles (Active Choices Reactive — auto-populate per env)
        // Each reads ci/jenkins/flow-config.json for its default ON/OFF state.
        // User can override any toggle before clicking Build.

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_COMPILE',
         description: 'Compile backend (mvn package)',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.compile[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_UNIT_TEST',
         description: 'Run backend unit tests + JaCoCo',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.unit_test[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_ADMIN_QA',
         description: 'Admin portal typecheck + test',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.admin_qa[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_MOBILE_QA',
         description: 'Mobile app typecheck + test',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.mobile_qa[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_SONAR',
         description: 'SonarQube scan + quality gate',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.sonar[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_SECURITY_SCAN',
         description: 'Semgrep + Gitleaks + Trivy + OWASP',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.security_scan[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_DOCKER_BUILD_PUSH',
         description: 'Docker build + push to registry',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.docker_build_push[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_DEPLOY_DEV',
         description: 'Deploy to dev EKS namespace',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.deploy_dev[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_DEPLOY_STG',
         description: 'Deploy to stg via ArgoCD GitOps',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.deploy_stg[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_DEPLOY_PROD',
         description: 'Deploy to prod via ArgoCD GitOps',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.deploy_prod[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_API_AUTOMATION',
         description: 'Conformance API tests',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.api_automation[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_ZAP_DAST',
         description: 'OWASP ZAP baseline DAST scan',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.zap_dast[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_SIT_REGRESSION',
         description: 'SIT + regression smoke tests',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.sit_regression[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_UAT_APPROVAL',
         description: 'UAT approval gate',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.uat_approval[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_CANARY',
         description: 'Canary deploy via ArgoCD fan-out',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.canary[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

        [$class: 'ActiveChoiceReactiveParameter',
         name: 'FLOW_MONITORING',
         description: 'Sentry/Grafana/Prometheus verify',
         choiceType: 'PT_SINGLE_SELECT',
         script: [$class: 'GroovyScript',
             script: [script: """
                 def c = new groovy.json.JsonSlurper().parseText(
                     new File('ci/jenkins/flow-config.json').text)
                 def env = binding.variables.get('DEPLOY_ENV') ?: 'dev'
                 return c.flows.monitoring[env] ? 'true' : 'false'
             """, fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]],
             fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]]],

    ])
])

// ============================================================================
// Helper: resolve flow ON/OFF from the GUI checkbox values
// ============================================================================
def flowEnabled(flowName) {
    def paramName = "FLOW_${flowName.toUpperCase()}"
    def val = params[paramName]
    if (val == null || val.toString().isEmpty()) {
        // Fallback: read from flow-config.json
        try {
            def c = new groovy.json.JsonSlurper().parseText(
                new File('ci/jenkins/flow-config.json').text)
            return c.flows[flowName][params.DEPLOY_ENV] ?: false
        } catch (e) { return false }
    }
    return val.toString().toLowerCase() == 'true'
}

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
        buildDiscarder(logRotator(numToKeepStr: '50'))
        timeout(time: 240, unit: 'MINUTES')
    }

    environment {
        CI_NETWORK      = 'selfcare-ci'
        WORKSPACE_VOLUME = 'selfcare_jenkins_workspace'
        MAVEN_VOLUME    = 'selfcare_maven_cache'
        IMAGE_NAMESPACE = 'ghcr.io/sangiya123'
        GITOPS_REPO     = 'https://github.com/sangiya123/selfcare-platform'
        GITOPS_BRANCH   = 'main'
        SONAR_HOST_URL  = 'http://sonarqube:9000'
    }

    stages {

        // ====================================================================
        // INIT
        // ====================================================================
        stage('Init') {
            steps {
                script {
                    env.DEPLOY_ENV = params.DEPLOY_ENV
                    env.IMAGE_TAG = params.DEPLOY_ENV == 'prod' ? "v${params.RELEASE_TAG}" :
                                    params.DEPLOY_ENV == 'reg'  ? "reg-${env.BUILD_NUMBER}" :
                                    params.DEPLOY_ENV == 'stg'  ? "rc-${env.BUILD_NUMBER}" :
                                                                   "dev-${env.BUILD_NUMBER}"

                    if (['reg','prod'].contains(params.DEPLOY_ENV) &&
                        (params.RELEASE_TAG == null || params.RELEASE_TAG.trim().isEmpty())) {
                        error "RELEASE_TAG is required for reg/prod."
                    }

                    def flowNames = ['compile','unit_test','admin_qa','mobile_qa','sonar',
                        'security_scan','docker_build_push','deploy_dev','deploy_stg',
                        'deploy_prod','api_automation','zap_dast','sit_regression',
                        'uat_approval','canary','monitoring']
                    def active = flowNames.findAll { flowEnabled(it) }
                    def inactive = flowNames.findAll { !flowEnabled(it) }

                    echo """
╔══════════════════════════════════════════════════════════════╗
║  Selfcare — CI/CD Pipeline                           ║
║  Environment : ${params.DEPLOY_ENV.padRight(8)}                              ║
║  Image tag   : ${env.IMAGE_TAG.padRight(40)}  ║
║  ACTIVE (${active.size()}) : ${active.join(', ')}                    ║
║  INACTIVE (${inactive.size()}) : ${inactive.join(', ')}                    ║
╚══════════════════════════════════════════════════════════════╝
"""
                }
            }
        }

        stage('Checkout') {
            steps {
                checkout scm
                sh 'git rev-parse --short HEAD'
            }
        }

        // ====================================================================
        // 1. COMPILE
        // ====================================================================
        stage('Compile') {
            when { expression { flowEnabled('compile') } }
            steps {
                sh """
                  docker run --rm --network "\$CI_NETWORK" \
                    --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                    --mount source="\$MAVEN_VOLUME",target=/root/.m2 \
                    -w /workspace/backend eclipse-temurin:25-jdk-alpine sh -lc \
                    'apk add --no-cache maven && mvn -B -T 1C package \
                      -Dmaven.test.skip=true -Djacoco.skip=true'
                """
            }
        }

        // ====================================================================
        // 2. UNIT TEST
        // ====================================================================
        stage('Unit Test') {
            when { expression { flowEnabled('unit_test') } }
            steps {
                sh """
                  docker run --rm --network "\$CI_NETWORK" \
                    --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                    --mount source="\$MAVEN_VOLUME",target=/root/.m2 \
                    -w /workspace/backend eclipse-temurin:25-jdk-alpine sh -lc \
                    'apk add --no-cache maven && mvn -B -T 1C \
                      ${{ params.DEPLOY_ENV == 'prod' ? 'verify' : 'package' }}'
                """
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'backend/**/target/surefire-reports/*.xml'
                    archiveArtifacts allowEmptyArchive: true,
                        artifacts: 'backend/**/target/site/jacoco/**'
                }
            }
        }

        // ====================================================================
        // 3. ADMIN QA
        // ====================================================================
        stage('Admin QA') {
            when { expression { flowEnabled('admin_qa') } }
            steps {
                sh """
                  docker run --rm --network "\$CI_NETWORK" \
                    --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                    -w /workspace/admin/selfcare-admin node:22-alpine sh -lc \
                    'npm ci && npm run typecheck && npm test -- --run'
                """
            }
        }

        // ====================================================================
        // 4. MOBILE QA
        // ====================================================================
        stage('Mobile QA') {
            when { expression { flowEnabled('mobile_qa') } }
            steps {
                sh """
                  docker run --rm --network "\$CI_NETWORK" \
                    --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                    -w /workspace/mobile/selfcare-app node:22-alpine sh -lc \
                    'npm ci && npm run typecheck && npm test -- --ci --coverage --maxWorkers=2'
                """
            }
        }

        // ====================================================================
        // 5. SONARQUBE
        // ====================================================================
        stage('SonarQube') {
            when { expression { flowEnabled('sonar') } }
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('local-sonarqube') {
                        sh """
                          docker run --rm --network "\$CI_NETWORK" \
                            --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                            --mount source="\$MAVEN_VOLUME",target=/root/.m2 \
                            -w /workspace/backend -e SONAR_HOST_URL -e SONAR_TOKEN \
                            eclipse-temurin:25-jdk-alpine sh -lc \
                            'apk add --no-cache maven && mvn -B -Psonar -DskipTests verify sonar:sonar \
                              -Dsonar.projectKey=selfcare-backend \
                              -Dsonar.qualitygate.wait=true'
                        """
                    }
                }
            }
        }

        stage('Sonar Quality Gate') {
            when { expression { flowEnabled('sonar') } }
            steps {
                timeout(time: 15, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ====================================================================
        // 6. SECURITY SCAN
        // ====================================================================
        stage('Security Scan') {
            when { expression { flowEnabled('security_scan') } }
            steps {
                catchError(buildResult: 'FAILURE', stageResult: 'FAILURE') {
                    sh """
                      docker run --rm --network "\$CI_NETWORK" \
                        --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                        --mount source="\$MAVEN_VOLUME",target=/root/.m2 \
                        -w /workspace/backend eclipse-temurin:25-jdk-alpine sh -lc \
                        'apk add --no-cache maven && mvn -B org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom -DskipTests'

                      docker run --rm --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                        anchore/grype:latest sbom:/workspace/backend/target/bom.json \
                        --output json --file /workspace/backend/target/grype.json --fail-on critical

                      docker run --rm --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                        -w /workspace returntocorp/semgrep semgrep --config auto \
                        backend/ admin/selfcare-admin/src mobile/selfcare-app/src

                      docker run --rm --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                        zricethezav/gitleaks:latest detect --source /workspace --no-banner --redact

                      docker run --rm --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                        aquasec/trivy:latest fs --scanners vuln,secret,misconfig \
                        --severity CRITICAL,HIGH --exit-code 1 /workspace
                    """
                }
            }
        }

        // ====================================================================
        // 7. DOCKER BUILD + PUSH
        // ====================================================================
        stage('Docker Build + Push') {
            when { expression { flowEnabled('docker_build_push') } }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-registry',
                    usernameVariable: 'REGISTRY_USER',
                    passwordVariable: 'REGISTRY_PASSWORD')]) {
                    sh """
                      REGISTRY=\$(echo "\$IMAGE_NAMESPACE" | cut -d/ -f1)
                      echo "\$REGISTRY_PASSWORD" | docker login "\$REGISTRY" -u "\$REGISTRY_USER" --password-stdin || true
                      IMAGE_NAMESPACE="$IMAGE_NAMESPACE" VERSION="$IMAGE_TAG" \
                        ./scripts/build-images.sh --push
                    """
                }
            }
        }

        // ====================================================================
        // 8. DEPLOY DEV
        // ====================================================================
        stage('Deploy DEV (EKS)') {
            when { expression { flowEnabled('deploy_dev') } }
            steps {
                sh './scripts/deploy-k8s.sh --env dev --tag "$IMAGE_TAG" --registry "$IMAGE_NAMESPACE"'
            }
        }

        // ====================================================================
        // 9. DEPLOY STG
        // ====================================================================
        stage('Deploy STG (ArgoCD)') {
            when { expression { flowEnabled('deploy_stg') } }
            steps {
                sh 'GITOPS_REPO="$GITOPS_REPO" GITOPS_BRANCH="$GITOPS_BRANCH" ./scripts/argocd-sync.sh --env stg --tag "$IMAGE_TAG" --push --sync'
            }
        }

        // ====================================================================
        // 10. API AUTOMATION
        // ====================================================================
        stage('API Automation') {
            when { expression { flowEnabled('api_automation') } }
            steps {
                sh """
                  docker run --rm --network "\$CI_NETWORK" \
                    --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                    --mount source="\$MAVEN_VOLUME",target=/root/.m2 \
                    -w /workspace/tests/conformance eclipse-temurin:25-jdk-alpine sh -lc \
                    'apk add --no-cache maven && mvn -B -fae test \
                      -DTEST_BASE_URL=http://api-gateway.selfcare-dev.svc.cluster.local:8080 \
                      -DTEST_TENANT_ID=dialog-lk'
                """
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'tests/conformance/target/surefire-reports/*.xml'
                }
            }
        }

        // ====================================================================
        // 11. ZAP DAST
        // ====================================================================
        stage('ZAP DAST') {
            when { expression { flowEnabled('zap_dast') } }
            steps {
                sh """
                  docker run --rm --network "\$CI_NETWORK" \
                    --mount source="\$WORKSPACE_VOLUME",target=/workspace \
                    -w /workspace ghcr.io/zaproxy/zaproxy:latest zap-baseline.py \
                    -t http://api-gateway.selfcare-dev.svc.cluster.local:8080 \
                    -r /workspace/backend/target/zap-report.html -l WARN || true
                """
            }
        }

        // ====================================================================
        // 12. SIT + REGRESSION
        // ====================================================================
        stage('SIT + Regression') {
            when { expression { flowEnabled('sit_regression') } }
            steps {
                sh './scripts/smoke.sh --env dev --namespace selfcare-dev'
            }
        }

        // ====================================================================
        // 13. UAT APPROVAL
        // ====================================================================
        stage('UAT Approval') {
            when { expression { flowEnabled('uat_approval') } }
            steps {
                input message: "Approve UAT for ${params.DEPLOY_ENV} release v${params.RELEASE_TAG}",
                      ok: 'Approve',
                      submitterParameter: 'UAT_APPROVER',
                      parameters: [
                          string(defaultValue: '',
                                 description: 'Jira ticket / evidence of UAT sign-off',
                                 name: 'UAT_EVIDENCE')
                      ]
                echo "UAT approved by ${UAT_APPROVER} — ${UAT_EVIDENCE}"
            }
        }

        // ====================================================================
        // 14. DEPLOY PROD (CANARY)
        // ====================================================================
        stage('Deploy PROD (ArgoCD Canary)') {
            when { expression { flowEnabled('canary') } }
            steps {
                sh 'GITOPS_REPO="$GITOPS_REPO" GITOPS_BRANCH="$GITOPS_BRANCH" ./scripts/canary-deploy.sh --env prod --tag "$IMAGE_TAG" --sync'
            }
        }

        // ====================================================================
        // 15. MONITORING
        // ====================================================================
        stage('Monitoring') {
            when { expression { flowEnabled('monitoring') } }
            steps {
                sh './scripts/smoke.sh --env prod --namespace selfcare-prod'
                sh 'curl -fsS http://prometheus:9090/-/healthy && echo Prometheus-OK || true'
                sh 'curl -fsS http://grafana:3000/api/health    && echo Grafana-OK    || true'
                sh 'curl -fsS http://sentry:9000/_health/       && echo Sentry-OK     || true'
            }
        }

        // ====================================================================
        // SMOKE (always runs after any deploy)
        // ====================================================================
        stage('Deployment Smoke') {
            when { expression { flowEnabled('deploy_dev') || flowEnabled('deploy_stg') || flowEnabled('canary') } }
            steps {
                sh './scripts/smoke.sh --env "$DEPLOY_ENV" --namespace "selfcare-$DEPLOY_ENV" || true'
                sh 'kubectl get pods -n "selfcare-$DEPLOY_ENV" 2>/dev/null || true'
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true,
                testResults: '**/target/surefire-reports/*.xml,**/junit*.xml'
            archiveArtifacts allowEmptyArchive: true,
                artifacts: 'backend/target/bom.json,backend/target/grype.json,**/coverage/**,**/target/zap-report.html'
        }
        failure { echo "PIPELINE FAILED — ${params.DEPLOY_ENV} train." }
        cleanup { cleanWs(deleteDirs: false, disableDeferredWipeout: true) }
    }
}