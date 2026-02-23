package opstree.common

class script_loader implements Serializable {

    def steps

    script_loader(steps) {
        this.steps = steps
    }

    void load(String resourcePath, String outputFile) {
        steps.writeFile file: outputFile,
            text: steps.libraryResource(resourcePath)
    }
}
