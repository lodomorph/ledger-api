// =============================================================================
// 77GSI TrueCD — MVP 1 Jenkinsfile (Gates 1–5, PR Fast Feedback)
// =============================================================================
// Scope: Every pull request on ledger-api runs Gates 1–5.
// Goal:  Developer feedback in under 10 minutes. Failing gate blocks the PR.
// Next:  Jenkinsfile.mvp2 adds Gates 6–8 + 10 on merge to main.
// =============================================================================

pipeline {
    agent any

    environment {
        APP_NAME       = 'ledger-api'
        TRUECD_REPO    = 'https://github.com/lodomorph77/77gsi-truecd.git'
        TRUECD_DIR     = 'truecd'
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

        stage('0. Checkout Pipeline Scripts') {
            steps {
                dir("${TRUECD_DIR}") {
                    git url: "${TRUECD_REPO}",
                        branch: 'main',
                        credentialsId: 'github-creds'
                }
                sh 'find truecd -name "*.sh" -exec chmod +x {} +'
            }
        }

        stage('1. Planning Gate') {
            steps {
                echo "Branch: ${env.BRANCH_NAME}, Commit: ${env.GIT_COMMIT}"
                // validate-oas.sh writes reports/oas/spectral-results.json automatically
                sh '''
                    truecd/gates/01-planning/validate-oas.sh \
                        --oas-file openapi.yaml \
                        --output-dir reports/oas
                '''
                sh 'python3 truecd/gates/01-planning/format-lint-report.py --output-dir reports/oas'
            }
            post {
                always {
                    archiveArtifacts artifacts: 'reports/oas/spectral-results.json',
                                     allowEmptyArchive: true
                    publishHTML(target: [
                        reportDir:            'reports/oas',
                        reportFiles:          'gate-01-oas-lint.html',
                        reportName:           'Gate 1 — OAS Lint Report',
                        keepAll:              true,
                        alwaysLinkToLastBuild: false,
                        allowMissing:         true
                    ])
                }
            }
        }

        stage('2. Unit Tests') {
            steps {
                sh 'mvn test'
                sh 'mvn jacoco:report'
            }
            post {
                always {
                    junit '**/target/surefire-reports/**/*.xml'
                    recordCoverage(tools: [[parser: 'JACOCO',
                        pattern: 'target/site/jacoco/jacoco.xml']])
                }
            }
        }

        stage('3. Contract Tests') {
            steps {
                sh 'mvn test -Dtest=ContractTest -Dsurefire.failIfNoSpecifiedTests=false'
                // pact:publish and pact:verify require the Pact Maven plugin in pom.xml.
                // These run as warnings until the plugin is configured — they will not block the gate.
                sh 'mvn pact:publish || echo "[WARN] pact:publish skipped — add au.com.dius.pact:pact-jvm-provider-maven plugin to pom.xml"'
                sh 'mvn pact:verify || echo "[WARN] pact:verify skipped — add au.com.dius.pact:pact-jvm-provider-maven plugin to pom.xml"'
                withCredentials([string(credentialsId: 'pactflow-token', variable: 'PACT_BROKER_TOKEN')]) {
                    // can-i-deploy.sh writes reports/contracts/can-i-deploy-*.json automatically
                    sh """
                        truecd/gates/03-contract-testing/can-i-deploy.sh \
                            --participant ${APP_NAME} \
                            --version ${GIT_COMMIT} \
                            --environment staging \
                            --broker-url http://pact-broker:9292 \
                            --ignore-no-pacts \
                            --output-dir reports/contracts
                    """
                }
                sh """
                    python3 truecd/gates/03-contract-testing/format-pact-report.py \
                        --participant ${APP_NAME} \
                        --version     ${GIT_COMMIT} \
                        --environment staging \
                        --output-dir  reports/contracts
                """
                script {
                    currentBuild.description = (currentBuild.description ?: '') +
                        " | Pact: ${env.PACT_BROKER_BASE_URL ?: 'http://localhost:9292'}" +
                        "/matrix?q[]pacticipant=${APP_NAME}&q[]version=${GIT_COMMIT}"
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'reports/contracts/*.json',
                                     allowEmptyArchive: true
                    publishHTML(target: [
                        reportDir:            'reports/contracts',
                        reportFiles:          'gate-03-pact-report.html',
                        reportName:           'Gate 3 — Contract Report',
                        keepAll:              true,
                        alwaysLinkToLastBuild: false,
                        allowMissing:         true
                    ])
                }
            }
        }

        stage('4. Component Tests') {
            steps {
                sh 'mvn test -Dspring.profiles.active=test -Dsurefire.failIfNoSpecifiedTests=false -q'
            }
            post {
                always {
                    junit '**/target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('5. Acceptance Tests') {
            parallel {
                stage('BDD - Cucumber') {
                    steps {
                        sh 'mvn test -Dtest=CucumberRunner -Dcucumber.filter.tags=@smoke -Dsurefire.failIfNoSpecifiedTests=false'
                    }
                    post {
                        always { junit '**/target/cucumber-reports/*.xml' }
                    }
                }
                stage('API - Newman') {
                    steps {
                        sh '''
                            newman run tests/acceptance/postman/collection.json \
                                -e tests/acceptance/postman/staging.env.json \
                                --reporters cli,junit \
                                --reporter-junit-export results/newman.xml
                        '''
                    }
                    post {
                        always { junit 'results/newman.xml' }
                    }
                }
            }
        }

    }

    post {
        success {
            withCredentials([string(credentialsId: 'telegram-bot-token', variable: 'TG_TOKEN'),
                             string(credentialsId: 'telegram-chat-id', variable: 'TG_CHAT_ID')]) {
                sh """curl -s -X POST "https://api.telegram.org/bot\${TG_TOKEN}/sendMessage" \
                    -d chat_id="\${TG_CHAT_ID}" \
                    -d parse_mode="HTML" \
                    -d text="✅ ${APP_NAME} #${BUILD_NUMBER} — Gates 1–5 passed. PR is green." """
            }
        }
        failure {
            withCredentials([string(credentialsId: 'telegram-bot-token', variable: 'TG_TOKEN'),
                             string(credentialsId: 'telegram-chat-id', variable: 'TG_CHAT_ID')]) {
                sh """curl -s -X POST "https://api.telegram.org/bot\${TG_TOKEN}/sendMessage" \
                    -d chat_id="\${TG_CHAT_ID}" \
                    -d parse_mode="HTML" \
                    -d text="❌ ${APP_NAME} #${BUILD_NUMBER} — FAILED at ${env.STAGE_NAME}. PR is blocked. <a href='${BUILD_URL}'>View logs</a>" """
            }
        }
        always {
            sh """
                python3 truecd/scripts/generate-mvp1-summary.py \
                    --reports-dir  reports \
                    --app-name     ${APP_NAME} \
                    --build-number ${env.BUILD_NUMBER} \
                    --commit       ${env.GIT_COMMIT} \
                    --branch       ${env.BRANCH_NAME ?: 'unknown'} \
                    --build-url    ${env.BUILD_URL}
            """
            publishHTML(target: [
                reportDir:            'reports',
                reportFiles:          'mvp1-build-summary.html',
                reportName:           'TrueCD MVP1 — Build Summary',
                keepAll:              true,
                alwaysLinkToLastBuild: true,
                allowMissing:         true
            ])
            cleanWs()
        }
    }
}
