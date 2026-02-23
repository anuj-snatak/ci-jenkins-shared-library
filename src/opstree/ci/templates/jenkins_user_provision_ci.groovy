package opstree.ci.templates

import opstree.common.python_env
import opstree.common.script_loader
import opstree.notification.slack_notify

class jenkins_user_provision_ci implements Serializable {

    def steps
    def config

    jenkins_user_provision_ci(steps, config) {
        this.steps = steps
        this.config = config
    }

    void execute() {

        // Checkout job repository
        steps.stage('Checkout') {
            steps.checkout steps.scm
        }

        // Load Python script from shared library resources
        steps.stage('Load Script') {
            new script_loader(steps)
                .load("python/provision_jenkins_users.py",
                      "provision_jenkins_users.py")
        }

        // Setup Python virtual environment
        steps.stage('Setup Python') {
            new python_env(steps).setup()
        }

        // Provision users
        steps.stage('Provision Users') {

            steps.withCredentials([
                steps.string(credentialsId: config.adminCreds,
                             variable: 'ADMIN_TOKEN'),
                steps.usernamePassword(
                    credentialsId: config.smtpCreds,
                    usernameVariable: 'SMTP_USER',
                    passwordVariable: 'SMTP_PASSWORD'
                )
            ]) {

                steps.withEnv([
                    "JENKINS_URL=${config.jenkinsUrl}",
                    "ADMIN_USER=${config.adminUser}",
                    "CSV_PATH=${config.csvPath ?: 'users.csv'}"
                ]) {

                    steps.sh '''
                        . venv/bin/activate
                        python provision_jenkins_users.py
                    '''
                }
            }
        }

        // Success notification
        steps.stage('Success Notification') {
            new slack_notify(steps)
                .send("Jenkins User Provisioning SUCCESS", "good")
        }
    }
}
