import jenkins.spock.harness.JenkinsPipelineSpecification

class ErrorStepSpec extends JenkinsPipelineSpecification {

    def "should throw RuntimeException with provided message on error step execution"() {
        when:
        error("Deployment step failed")

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Deployment step failed"
    }

    def "should throw RuntimeException with default message when error step is called without arguments"() {
        when:
        error()

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Pipeline error"
    }

    def "should abort execution of subsequent steps when error step is called in a stage"() {
        given:
        boolean subsequentStepExecuted = false

        when:
        stage("Build") {
            echo("Starting build")
            error("Build aborted due to fatal error")
            subsequentStepExecuted = true
        }

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Build aborted due to fatal error"
        !subsequentStepExecuted
        1 * getPipelineMock("stage")("Build", _ as Closure)
        1 * getPipelineMock("echo")("Starting build")
    }

    def "should allow catching and handling errors raised by error step in a pipeline"() {
        given:
        boolean errorHandled = false
        String capturedMessage = null

        when:
        stage("Deploy") {
            try {
                error("Deployment target unreachable")
            } catch (RuntimeException ex) {
                errorHandled = true
                capturedMessage = ex.message
                echo("Handled error: ${ex.message}")
            }
        }

        then:
        noExceptionThrown()
        errorHandled
        capturedMessage == "Deployment target unreachable"
        1 * getPipelineMock("echo")("Handled error: Deployment target unreachable")
    }

    def "should execute error step when invoked on a loaded script"() {
        given:
        script_class_path = ["vars"]
        def script = loadPipelineScriptForTest("/exec.groovy")

        when:
        script.error("Fatal error in script execution")

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Fatal error in script execution"
    }

    def "should propagate error when called inside a parallel branch"() {
        when:
        parallel(
            branchA: { echo("Branch A running") },
            branchB: { error("Branch B failure") }
        )

        then:
        def ex = thrown(RuntimeException)
        ex.message == "Branch B failure"
        1 * getPipelineMock("echo")("Branch A running")
    }
}
