import jenkins.spock.harness.JenkinsPipelineSpecification

class HelloSpec extends JenkinsPipelineSpecification {

    def setup() {
        script_class_path = ['vars']
    }

    def "test harness loads"() {
        expect:
        true
    }

    def "should load shared library"() {
        when:
        def script = loadPipelineScriptForTest('vars/hello.groovy')

        then:
        script != null
    }
}
