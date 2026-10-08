import jenkins.spock.harness.JenkinsPipelineSpecification

class ExecSpec extends JenkinsPipelineSpecification {

    def Exec

    def setup() {
        script_class_path = ["vars"]
        Exec = loadPipelineScriptForTest('/exec.groovy')
    }

    def "Sanity-Check isUnix"() {
        expect:
        isUnix() == null
    }

    def "Sanity-Check mocking isUnix"() {
        given:
        getPipelineMock('isUnix')() >> { return true }
        expect:
        isUnix() != null
    }

    def "Sanity-Check expecting isUnix"() {
        when:
        Exec('ls')
        then:
        _ * getPipelineMock('isUnix')() >> { return true }
        1 * getPipelineMock('sh') ('ls')
    }

    def "Test on Windows"() {
        given:
        getPipelineMock('isUnix')() >> false

        when:
        Exec('ls')
        then:
        1 * getPipelineMock('bat') ('ls')
        0 * getPipelineMock('sh') ('ls')
    }

    def "Test on Linux"() {
        given:
        getPipelineMock('isUnix')() >> true

        when:
        Exec('ls')
        then:
        1 * getPipelineMock('sh') ('ls')
        0 * getPipelineMock('bat') ('ls')
    }

    def "Test on Windows (alternate)"() {
        when:
        Exec('ls')
        then:
        1 * getPipelineMock('isUnix') () >> false
        1 * getPipelineMock('bat') ('ls')
        0 * getPipelineMock('sh') ('ls')
    }

    def "Test on Linux (alternate)"() {
        when:
        Exec('ls')
        then:
        1 * getPipelineMock('isUnix') () >> true
        1 * getPipelineMock('sh') ('ls')
        0 * getPipelineMock('bat') ('ls')
    }
}
