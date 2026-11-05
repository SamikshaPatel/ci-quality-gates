// The same contract on Jenkins: same scripts, same gate evaluator, same verdicts.
// Proves the quality gates belong to the project, not to one CI tool.
// Needs a Jenkins agent with Java 21, Maven, Docker and Docker Compose.
pipeline {
    agent any

    parameters {
        choice(name: 'DEMO_SCENARIO',
               choices: ['none', 'env-down', 'smoke-code', 'critical-flake', 'flake-breach', 'quarantine-cap'],
               description: 'Which failure path to prove (none = normal run)')
    }

    environment {
        DEMO_SCENARIO = "${params.DEMO_SCENARIO}"
        // Jenkins credentials store: never in the repo. Tests fall back to public demo defaults.
        JIRA_BASE_URL    = credentials('jira-base-url')
        JIRA_EMAIL       = credentials('jira-email')
        JIRA_API_TOKEN   = credentials('jira-api-token')
        JIRA_PROJECT_KEY = credentials('jira-project-key')
    }

    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Start API under test') {
            steps {
                sh 'docker compose -f docker-compose.ci.yml up -d --build --wait'
            }
        }
        stage('Build and unit tests') {
            steps {
                sh '''
                  mvn -B -ntp install -pl test-support,gate-evaluator -am
                  mvn -B -ntp test-compile -pl api-tests,ui-tests
                  mvn -B -ntp -q -pl ui-tests exec:java@install-browsers
                  mkdir -p gate-results && chmod +x scripts/*.sh
                '''
            }
        }
        stage('Demo: env-down') {
            when { environment name: 'DEMO_SCENARIO', value: 'env-down' }
            steps {
                sh 'docker compose -f docker-compose.ci.yml stop restful-booker'
            }
        }
        stage('Gate 1 — Smoke') {
            steps { sh 'scripts/run-gate.sh smoke' }
        }
        stage('Gate 2 — Contract') {
            steps { sh 'scripts/run-gate.sh contract' }
        }
        stage('Gate 3 — Critical tier') {
            steps { sh 'scripts/run-gate.sh critical' }
        }
        stage('Gate 4 — Non-critical tier') {
            steps { sh 'scripts/run-regression.sh' }
        }
    }

    post {
        always {
            sh 'java -jar gate-evaluator/target/gate-evaluator.jar report || true'
            junit allowEmptyResults: true, testResults: '*/target/reports/**/TEST-*.xml'
            archiveArtifacts allowEmptyArchive: true,
                artifacts: 'gate-results/**, quarantine.json, */target/reports/**, */target/allure-results/**, */target/traces/**'
            sh 'docker compose -f docker-compose.ci.yml down || true'
        }
    }
}
