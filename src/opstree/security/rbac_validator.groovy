package opstree.security

class rbac_validator implements Serializable {

    def steps

    rbac_validator(steps) {
        this.steps = steps
    }

    void validateRole(String role) {
        def allowed = ['admin', 'developer', 'devops']
        if (!allowed.contains(role)) {
            steps.error("Invalid role detected in CSV: ${role}")
        }
    }
}
