import jenkins.spock.harness.JenkinsPipelineSpecification

public class DeployerHelperScriptSpec extends JenkinsPipelineSpecification {

    def DeployerHelpScript = null

    def setup() {
        script_class_path = ["vars"]
        DeployerHelpScript = loadPipelineScriptForTest("/DeployerHelperScript.groovy")
    }

    def "deploy function deploys to TEST when asked" () {
        when:
        DeployerHelpScript.deploy( "test" )
        then:
        1 * getPipelineMock("sshagent")(["test-ssh"], _ as Closure)
        1 * getPipelineMock("sh")({it =~ /ssh deployer@app-test .*/})
    }

    def "deploy function deploys to PRODUCTION when asked" () {
        when:
        DeployerHelpScript.deploy( "production" )
        then:
        1 * getPipelineMock("sshagent")(["prod-ssh"], _ as Closure)
        1 * getPipelineMock("sh")({it =~ /ssh deployer@app-prod .*/})
    }
}
