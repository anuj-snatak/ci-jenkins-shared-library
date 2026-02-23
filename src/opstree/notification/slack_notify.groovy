package opstree.notification

class slack_notify implements Serializable {

    def steps

    slack_notify(steps) {
        this.steps = steps
    }

    void send(String message, String color = "good") {
        steps.slackSend(
            color: color,
            message: message
        )
    }
}
