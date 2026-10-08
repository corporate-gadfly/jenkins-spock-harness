import jenkins.spock.harness.JenkinsPipelineSpecification

public class DeployerSharedLibrarySpec extends JenkinsPipelineSpecification {

    def DeployerSharedLibrary = null

    def setup() {
        script_class_path = ["vars"]
        DeployerSharedLibrary = loadPipelineScriptForTest("/DeployerSharedLibrary.groovy")
    }

    def "deploy function deploys to TEST when asked" () {
        when:
        DeployerSharedLibrary( "test" )
        then:
        1 * getPipelineMock("sshagent")(["test-ssh"], _ as Closure)
        1 * getPipelineMock("sh")({it =~ /ssh deployer@app-test .*docker-compose/})
    }

    def "deploy function deploys to PRODUCTION when asked" () {
        when:
        DeployerSharedLibrary( "production" )
        then:
        1 * getPipelineMock("sshagent")(["prod-ssh"], _ as Closure)
        1 * getPipelineMock("sh")({it =~ /ssh deployer@app-prod .*docker-compose/})
    }
}
