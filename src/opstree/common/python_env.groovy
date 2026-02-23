package opstree.common

class python_env implements Serializable {

    def steps

    python_env(steps) {
        this.steps = steps
    }

    void setup() {
        steps.sh '''
            python3 -m venv venv
            . venv/bin/activate
            python -m pip install --upgrade pip
            pip install requests urllib3
        '''
    }
}
