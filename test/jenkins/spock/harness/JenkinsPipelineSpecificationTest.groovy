import jenkins.spock.harness.JenkinsPipelineSpecification

class JenkinsPipelineSpecificationTest extends JenkinsPipelineSpecification {
    def "should execute a closure passed as the final step argument"() {
        given:
        boolean executed = false
        Closure body = { executed = true }

        when:
        containerStep('argument', body)

        then:
        1 * getPipelineMock('containerStep')('argument', body)
        executed
    }

    def "should not execute a closure unless it is the final step argument"() {
        given:
        boolean executed = false
        Closure body = { executed = true }

        when:
        pipelineStep(body, 'argument')

        then:
        1 * getPipelineMock('pipelineStep')(body, 'argument')
        !executed
    }

    def "should execute every closure branch passed to parallel"() {
        given:
        List<String> executedBranches = []
        Map branches = [
                first: { executedBranches << 'first' },
                ignored: 'not a closure',
                second: { executedBranches << 'second' }
        ]

        when:
        parallel(branches)

        then:
        1 * getPipelineMock('parallel')(branches)
        executedBranches == ['first', 'second']
    }

    def "should treat call suffix as an alias for a regular pipeline step"() {
        when:
        regularStep('argument')

        then:
        1 * getPipelineMock('regularStep.call')('argument')
        getPipelineMock('regularStep.call').is(getPipelineMock('regularStep'))
    }

    def "should preserve call suffix for an explicitly mocked pipeline variable"() {
        given:
        explicitlyMockPipelineVariable('sharedStep')

        when:
        sharedStep('first', 'second')

        then:
        1 * getPipelineMock('sharedStep.call')('first', 'second')
    }


    def "should delegate properties on a pipeline variable to the property mock"() {
        given:
        def variable = explicitlyMockPipelineVariable('sharedStep')

        when:
        def result = variable.status

        then:
        1 * getPipelineMock('sharedStep.getProperty')('status') >> 'ready'
        result == 'ready'
    }

    def "should report configured search paths when a pipeline script is missing"() {
        given:
        script_class_path = ['vars']

        when:
        loadPipelineScriptForTest('/missing-pipeline-script.groovy')

        then:
        def exception = thrown(FileNotFoundException)
        exception.message == 'Script \'/missing-pipeline-script.groovy\' not found in paths: [vars]'
    }

    def "should dispatch a pipeline step with eight arguments"() {
        when:
        eightArgumentStep(1, 2, 3, 4, 5, 6, 7, 8)

        then:
        1 * getPipelineMock('eightArgumentStep')(1, 2, 3, 4, 5, 6, 7, 8)
    }

    def "should reject a pipeline step with more than eight arguments"() {
        when:
        nineArgumentStep(1, 2, 3, 4, 5, 6, 7, 8, 9)

        then:
        def exception = thrown(IllegalArgumentException)
        exception.message == 'Unsupported argument count for pipeline mock: 9'
    }
}

