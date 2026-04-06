// =============================================================================
// ledger-api — TrueCD MVP 1 Jenkinsfile (Gates 1–5, PR Fast Feedback)
// =============================================================================
// Commit this file to ledger-api/ as "Jenkinsfile".
// Jenkins SCM config: Script Path = Jenkinsfile, Branch = */main
//
// Pre-requisites (MVP 0 must be complete):
//   - Jenkins running at localhost:8080
//   - Pact Broker running at localhost:9292
//   - TRUECD_ROOT accessible from the Jenkins agent (see options below)
//   - Credentials loaded: pact-broker-url, telegram-bot-token, telegram-chat-id
//
// TRUECD_ROOT options:
//   A) Mount truecd repo into the agent container via docker-compose.local.yml volume
//   B) Add a 'Checkout TrueCD' stage (uncomment below) to clone it at build time
// =============================================================================

pipeline {
    agent any

    environment {
        APP_NAME       = 'ledger-api'
        OAS_FILE       = 'openapi.yaml'
        APP_PORT       = '8090'
        COVERAGE_MIN   = '60'
        // TRUECD_ROOT is set as a Jenkins Global Property — not declared here.
        // Value: /home/jenkins/agent/truecd
        // Set at: Manage Jenkins → Configure System → Global Properties → Environment variables

        PACT_BROKER_BASE_URL = credentials('pact-broker-url')
        TG_TOKEN             = credentials('telegram-bot-token')
        TG_CHAT_ID           = credentials('telegram-chat-id')
    }

    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    triggers {
        githubPush()
    }

    stages {

        // ---------------------------------------------------------------------
        // Optional: clone TrueCD scripts onto the agent at build time.
        // Use this if you are NOT mounting the truecd repo as a Docker volume.
        // Uncomment and set TRUECD_REPO to your fork/clone URL.
        // ---------------------------------------------------------------------
        // stage('Checkout TrueCD') {
        //     steps {
        //         dir("${TRUECD_ROOT}") {
        //             git url: 'https://github.com/lodomorph/truecd.git', branch: 'main'
        //         }
        //     }
        // }

        stage('1. Planning — OAS Validation') {
            steps {
                sh '''
                    bash ${TRUECD_ROOT}/gates/01-planning/validate-oas.sh \
                        --oas-file ${OAS_FILE}
                '''
            }
        }

        stage('2. Unit Tests + Coverage') {
            steps {
                // Start Hoverfly mock proxy seeded from the OAS spec
                sh '''
                    bash ${TRUECD_ROOT}/gates/02-unit-testing/setup-hoverfly.sh \
                        --oas-file ${OAS_FILE}
                '''
                // Run unit tests + generate JaCoCo report
                sh 'mvn verify -q'
                // Enforce coverage threshold (hard fail)
                sh '''
                    PYTHONIOENCODING=utf-8 \
                    bash ${TRUECD_ROOT}/gates/02-unit-testing/check-coverage.sh \
                        --report target/site/jacoco/jacoco.xml \
                        --line   ${COVERAGE_MIN} \
                        --branch ${COVERAGE_MIN}
                '''
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                    recordCoverage(tools: [[parser: 'JACOCO',
                        pattern: 'target/site/jacoco/jacoco.xml']])
                }
            }
        }

        stage('3. Contract Tests') {
            steps {
                // Publish provider contract (OAS) to Pact Broker
                sh '''
                    bash ${TRUECD_ROOT}/gates/03-contract-testing/publish-contract.sh \
                        --mode     provider \
                        --provider ${APP_NAME} \
                        --oas-file ${OAS_FILE} \
                        --version  $(git rev-parse --short HEAD) \
                        --branch   $(git rev-parse --abbrev-ref HEAD)
                '''
                // Gate: can-i-deploy to dev environment
                sh '''
                    bash ${TRUECD_ROOT}/gates/03-contract-testing/can-i-deploy.sh \
                        --participant ${APP_NAME} \
                        --version     $(git rev-parse --short HEAD) \
                        --environment dev
                '''
            }
        }

        stage('4. Component Tests') {
            steps {
                // Testcontainers spins up its own infra — no external Docker deps needed
                sh 'mvn test -Dspring.profiles.active=test -q'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('5. Acceptance Tests') {
            steps {
                // Generate Postman collection from OAS spec
                sh '''
                    bash ${TRUECD_ROOT}/gates/05-acceptance-testing/oas-to-postman.sh \
                        --oas-file   ${OAS_FILE} \
                        --output-dir tests/acceptance/postman
                '''
                // Run Newman against the generated collection (warn-only: hard gating
                // is deferred to MVP 2 once baseline contract content is authored)
                sh '''
                    bash ${TRUECD_ROOT}/gates/05-acceptance-testing/run-newman.sh \
                        --collection tests/acceptance/postman/collection.json \
                        --base-url   http://localhost:${APP_PORT} \
                        --warn-only
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'newman-results/*.xml'
                }
            }
        }

    }

    post {
        success {
            sh '''
                curl -s -X POST "https://api.telegram.org/bot${TG_TOKEN}/sendMessage" \
                    --data-urlencode "text=✅ ledger-api #${BUILD_NUMBER} — Gates 1-5 passed. PR is green. (${GIT_BRANCH}@$(git rev-parse --short HEAD))" \
                    -d chat_id="${TG_CHAT_ID}" \
                || true
            '''
        }
        failure {
            sh '''
                curl -s -X POST "https://api.telegram.org/bot${TG_TOKEN}/sendMessage" \
                    --data-urlencode "text=❌ ledger-api #${BUILD_NUMBER} — FAILED. PR is blocked. ${BUILD_URL}" \
                    -d chat_id="${TG_CHAT_ID}" \
                || true
            '''
        }
        always {
            cleanWs()
        }
    }
}
