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

        steps.stage('Checkout') {
            steps.checkout steps.scm
        }

        steps.stage('Load Script') {
            new script_loader(steps)
                .load("python/provision_jenkins_users.py",
                      "provision_jenkins_users.py")
        }

        steps.stage('Setup Python') {
            new python_env(steps).setup()
        }

        steps.stage('Provision Users') {

            def credHelper = new credential_helper(steps)

            credHelper.withSMTP(config.smtpCreds) {
                steps.sh '''
                    . venv/bin/activate
                    python provision_jenkins_users.py
                '''
            }
        }

        steps.stage('Success Notification') {
            new slack_notify(steps)
                .send("Jenkins User Provisioning SUCCESS", "good")
        }
    }
}
