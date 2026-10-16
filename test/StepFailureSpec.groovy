import jenkins.spock.harness.JenkinsPipelineSpecification

class StepFailureSpec extends JenkinsPipelineSpecification implements WithFailedStep {

    def DefaultPipeline = null
    def Exec = null

    def setup() {
        script_class_path = ["vars"]
        DefaultPipeline = loadPipelineScriptForTest("/DefaultPipeline.groovy")
        DefaultPipeline.getBinding().setVariable("scm", null)
        getPipelineMock("libraryResource")(_) >> {
            return "Dummy Message"
        }
        Exec = loadPipelineScriptForTest("/exec.groovy")
    }

    def "should notify Slack and propagate StepFailedException when test step fails using trait"() {
        given: "Stub test command to fail using WithFailedStep trait"
        getPipelineMock("sh")("docker run --entrypoint python whole-pipeline -m unittest discover") >> { failStep("Unit tests failed") }

        when:
        DefaultPipeline()

        then:
        def ex = thrown(StepFailedException)
        ex.message == "Unit tests failed"

        and: "Slack notification is sent for the test failure"
        1 * getPipelineMock("slackSend")([color: 'error', message: 'Dummy Message'])

        and: "Downstream stages are skipped"
        0 * getPipelineMock("sh")("docker push whole-pipeline")
        0 * getPipelineMock("Deployer.call")(_)
    }

    def "should propagate StepFailedException when build step fails using trait"() {
        given: "Stub docker build step to fail using trait"
        getPipelineMock("sh")("docker build --tag whole-pipeline .") >> { failStep("Docker build failed") }

        when:
        DefaultPipeline()

        then:
        def ex = thrown(StepFailedException)
        ex.message == "Docker build failed"

        and: "Downstream test and deployment steps are not invoked"
        0 * getPipelineMock("sh")("docker run --entrypoint python whole-pipeline -m unittest discover")
        0 * getPipelineMock("Deployer.call")(_)
    }

    def "should propagate StepFailedException when deployment step fails using trait"() {
        given: "Configure master branch for production deployment and stub failure"
        DefaultPipeline.getBinding().setVariable("BRANCH_NAME", "master")
        getPipelineMock("Deployer.call")("test") >> { failStep("Deployment to test environment failed") }

        when:
        DefaultPipeline()

        then:
        def ex = thrown(StepFailedException)
        ex.message == "Deployment to test environment failed"

        and: "Production deploy is not attempted after test deploy failure"
        0 * getPipelineMock("Deployer.call")("production")
    }

    def "should propagate StepFailedException when exec step fails on Linux"() {
        given: "Configure unix environment and stub sh execution failure"
        getPipelineMock("isUnix")() >> true
        getPipelineMock("sh")("npm test") >> { failStep("Command npm test exited with status 1") }

        when:
        Exec("npm test")

        then:
        def ex = thrown(StepFailedException)
        ex.message == "Command npm test exited with status 1"
        0 * getPipelineMock("bat")(_)
    }

    def "should propagate StepFailedException when exec step fails on Windows"() {
        given: "Configure windows environment and stub bat execution failure"
        getPipelineMock("isUnix")() >> false
        getPipelineMock("bat")("npm test") >> { failStep("Command npm test exited with code 1") }

        when:
        Exec("npm test")

        then:
        def ex = thrown(StepFailedException)
        ex.message == "Command npm test exited with code 1"
        0 * getPipelineMock("sh")(_)
    }
}
