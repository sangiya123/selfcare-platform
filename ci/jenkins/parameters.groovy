// ============================================================================
// Selfcare — Active Choices GUI Parameters
// ============================================================================
// Loads this shared function from Jenkinsfile via:
//   @Library('selfcare-pipeline') _
//
// Provides: Environment selector dropdown → dynamically shows toggleable
// checkboxes for each flow, pre-populated from the FLOWS matrix.
// User clicks checkboxes ON/OFF in the GUI → values flow into the pipeline.
//
// INSTALL PREREQUISITE: Jenkins → Manage Plugins → install "Active Choices"
//   https://plugins.jenkins.io/uno-choice/
// ============================================================================

def call() {
    def FLOWS = [
        compile:            [dev: true,  stg: true,  reg: true,  prod: true ],
        unit_test:          [dev: false, stg: true,  reg: true,  prod: true ],
        admin_qa:           [dev: false, stg: true,  reg: true,  prod: true ],
        mobile_qa:          [dev: false, stg: true,  reg: true,  prod: true ],
        sonar:              [dev: false, stg: false, reg: true,  prod: true ],
        security_scan:      [dev: false, stg: false, reg: false, prod: true ],
        docker_build_push:  [dev: true,  stg: true,  reg: true,  prod: true ],
        deploy_dev:         [dev: true,  stg: true,  reg: true,  prod: true ],
        deploy_stg:         [dev: false, stg: true,  reg: false, prod: false],
        deploy_prod:        [dev: false, stg: false, reg: false, prod: true ],
        api_automation:     [dev: false, stg: true,  reg: true,  prod: true ],
        zap_dast:           [dev: false, stg: false, reg: false, prod: true ],
        sit_regression:     [dev: false, stg: true,  reg: true,  prod: true ],
        uat_approval:       [dev: false, stg: false, reg: true,  prod: true ],
        canary:             [dev: false, stg: false, reg: false, prod: true ],
        monitoring:         [dev: false, stg: false, reg: false, prod: true ],
    ]

    properties([
        parameters([
            // --- Environment selector ---
            choice(
                name: 'DEPLOY_ENV',
                choices: ['dev', 'stg', 'reg', 'prod'],
                description: 'Target environment'
            ),
            // --- Release tag (required for reg/prod) ---
            string(
                name: 'RELEASE_TAG',
                defaultValue: '',
                description: 'Immutable release tag for reg/prod (e.g. 1.2.0). Ignored for dev/stg.'
            ),
            // --- Dynamic flow toggles (Active Choices reactive) ---
            activeChoice(
                name: 'FLOW_COMPILE',
                description: 'Compile backend',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']],
                    groovyScript: [
                        script: "return '${FLOWS.compile[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_UNIT_TEST',
                description: 'Run backend unit tests + JaCoCo',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.unit_test[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_ADMIN_QA',
                description: 'Admin portal typecheck + test',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.admin_qa[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_MOBILE_QA',
                description: 'Mobile app typecheck + test',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.mobile_qa[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_SONAR',
                description: 'SonarQube scan + quality gate',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.sonar[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_SECURITY_SCAN',
                description: 'Semgrep + Gitleaks + Trivy + OWASP',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.security_scan[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_DOCKER_BUILD_PUSH',
                description: 'Docker build + push to registry',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']],
                    groovyScript: [
                        script: "return '${FLOWS.docker_build_push[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_DEPLOY_DEV',
                description: 'Deploy to dev EKS namespace',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']],
                    groovyScript: [
                        script: "return '${FLOWS.deploy_dev[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'true']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_DEPLOY_STG',
                description: 'Deploy to stg via ArgoCD GitOps',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.deploy_stg[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_DEPLOY_PROD',
                description: 'Deploy to prod via ArgoCD GitOps',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.deploy_prod[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_API_AUTOMATION',
                description: 'Conformance API tests',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.api_automation[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_ZAP_DAST',
                description: 'OWASP ZAP baseline DAST scan',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.zap_dast[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_SIT_REGRESSION',
                description: 'SIT + regression smoke tests',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.sit_regression[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_UAT_APPROVAL',
                description: 'UAT approval gate',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.uat_approval[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_CANARY',
                description: 'Canary deploy via ArgoCD fan-out',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.canary[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
            activeChoice(
                name: 'FLOW_MONITORING',
                description: 'Sentry/Grafana/Prometheus verify',
                choiceType: 'PT_SINGLE_SELECT',
                script: [
                    fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']],
                    groovyScript: [
                        script: "return '${FLOWS.monitoring[params.DEPLOY_ENV ?: 'dev']}'",
                        fallbackScript: [returnType: 'PT_SINGLE_SELECT', script: [return 'false']]
                    ]
                ]
            ),
        ])
    ])
}
