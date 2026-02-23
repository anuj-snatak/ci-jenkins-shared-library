package opstree.ci.templates

import opstree.common.python_env
import opstree.common.script_loader
import opstree.common.credential_helper
import opstree.notification.slack_notify

class jenkins_user_provision_ci implements Serializable {

    def steps
    def config

    jenkins_user_provision_ci(steps, config) {
        this.steps = steps
        this.config = config
    }

    void execute() {

        steps.pipeline {
            agent any

            environment {
                JENKINS_URL = config.jenkinsUrl
                ADMIN_USER  = config.adminUser
                ADMIN_TOKEN = steps.credentials(config.adminCreds)
                CSV_PATH    = config.csvPath ?: "users.csv"
            }

            stages {

                stage('Checkout') {
                    steps {
                        checkout scm
                    }
                }

                stage('Load Script') {
                    steps {
                        script {
                            new script_loader(steps)
                                .load("python/provision_jenkins_users.py",
                                      "provision_jenkins_users.py")
                        }
                    }
                }

                stage('Setup Python') {
                    steps {
                        script {
                            new python_env(steps).setup()
                        }
                    }
                }

                stage('Provision Users') {
                    steps {
                        script {
                            def credHelper = new credential_helper(steps)

                            credHelper.withSMTP(config.smtpCreds) {
                                steps.sh '''
                                    . venv/bin/activate
                                    python provision_jenkins_users.py
                                '''
                            }
                        }
                    }
                }
            }

            post {
                success {
                    script {
                        new slack_notify(steps)
                            .send("Jenkins User Provisioning SUCCESS", "good")
                    }
                }
                failure {
                    script {
                        new slack_notify(steps)
                            .send("Jenkins User Provisioning FAILED", "danger")
                    }
                }
            }
        }
    }
}
