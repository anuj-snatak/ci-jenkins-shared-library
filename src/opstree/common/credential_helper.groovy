package opstree.common

class credential_helper implements Serializable {

    def steps

    credential_helper(steps) {
        this.steps = steps
    }

    void withSMTP(String credsId, Closure body) {
        steps.withCredentials([
            steps.usernamePassword(
                credentialsId: credsId,
                usernameVariable: 'SMTP_USER',
                passwordVariable: 'SMTP_PASSWORD'
            )
        ]) {
            body()
        }
    }
}
