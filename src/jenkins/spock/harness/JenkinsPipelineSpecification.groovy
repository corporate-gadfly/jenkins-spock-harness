package jenkins.spock.harness

import spock.lang.Specification

/**
 * Lightweight jenkins-spock-style base specification for this shared library.
 *
 * Key behaviors preserved from the pre-JDK-21 test harness:
 * - pipeline steps are mocked by their direct name, e.g. `stage`
 * - shared-library variables invoked like functions are mocked by `<name>.call`, e.g. `postBuildNotify.call`
 * - shared-library variables accessed as symbols/properties are mocked by `<name>`
 * - any pipeline step whose last argument is a Closure executes that Closure automatically
 * - `parallel` executes each closure value from the provided map
 */
abstract class JenkinsPipelineSpecification extends Specification {
    /**
     * Deliberately avoids a single varargs call(Object... args) signature.
     *
     * In experiments Spock records varargs invocations as a single packed
     * Object[] argument, which breaks existing interaction definitions such as:
     *
     *   1 * getPipelineMock('stage')('Build', _)
     *   1 * getPipelineMock('sh')('./gradlew test')
     *
     * Explicit overloads preserve the historical interaction style.
     */
    interface PipelineCallable {
        Object call()
        Object call(Object arg1)
        Object call(Object arg1, Object arg2)
        Object call(Object arg1, Object arg2, Object arg3)
        Object call(Object arg1, Object arg2, Object arg3, Object arg4)
        Object call(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5)
        Object call(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6)
        Object call(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7)
        Object call(Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8)
    }

    /**
     * Cache of mocks keyed by their canonical pipeline name.
     *
     * Examples:
     * - pipeline step: {@code stage}
     * - shared-library variable invocation: {@code postBuildNotify.call}
     * - shared-library method call: {@code emailNotification.call}
     */
    protected Map<String, Object> pipelineMocks = [:]
    protected List<String> script_class_path = []

    /** Binding attached to the parsed pipeline script under test. */
    protected Binding testBinding

    /** Variable names explicitly declared by the test as pipeline variables. */
    protected Set<String> explicitVariableNames = [] as Set

    /** Variable names discovered from shared-library {@code vars/*.groovy} scripts. */
    protected Set<String> sharedLibraryVariableNames = [] as Set
    private boolean sharedLibraryVariablesDiscovered

    def setup() {
        pipelineMocks.clear()
        explicitVariableNames.clear()
        sharedLibraryVariableNames.clear()
        sharedLibraryVariablesDiscovered = false
        testBinding = null
    }

    def methodMissing(String name, Object args) {
        return dispatchPipelineMethod(name, args)
    }

    def propertyMissing(String name) {
        return explicitlyMockPipelineVariable(name)
    }

    /**
     * Returns an existing mock or lazily creates one for the requested pipeline symbol.
     *
     * Names first pass through {@link #canonicalMockName(String)} so tests can refer to
     * ordinary steps as either {@code sh} or {@code sh.call}, while shared-library
     * variables retain the explicit {@code .call} suffix used by their impersonator.
     */
    protected Object getPipelineMock(String name) {
        String mockName = canonicalMockName(name)

        if (!pipelineMocks.containsKey(mockName)) {
            if (mockName.contains('.')) {
                return createStepMock(mockName)
            }
            if (isVariableName(mockName)) {
                return explicitlyMockPipelineVariable(mockName)
            }
            return createStepMock(mockName)
        }
        return pipelineMocks[mockName]
    }

    /**
     * Explicitly registers a pipeline step mock by step name.
     *
     * This is mainly useful in test setup when a script is expected to reference a Jenkins
     * step directly and the test wants that mock to exist before the script executes.
     */
    protected PipelineCallable explicitlyMockPipelineStep(String stepName) {
        return (PipelineCallable) getPipelineMock(stepName)
    }

    /**
     * Marks a name as a pipeline variable and returns an impersonator for it.
     *
     * The impersonator allows tests and scripts to treat shared-library vars like regular
     * Groovy objects while internally routing calls to canonical mock names such as
     * {@code postBuildNotify.call} or {@code someVar.someMethod}.
     */
    protected PipelineVariableImpersonator explicitlyMockPipelineVariable(String varName) {
        explicitVariableNames << varName
        if (!(pipelineMocks[varName] instanceof PipelineVariableImpersonator)) {
            pipelineMocks[varName] = new PipelineVariableImpersonator(varName, this)
        }
        return (PipelineVariableImpersonator) pipelineMocks[varName]
    }

    /**
     * Loads a shared-library script, binds it to a fresh {@link Binding}, and installs the
     * dynamic dispatch hooks that redirect unknown methods and properties into the mock layer.
     */
    protected Script loadPipelineScriptForTest(String scriptPath) {
        discoverSharedLibraryVariables()

        File scriptFile = resolveScriptFile(scriptPath)
        if (!scriptFile.exists()) {
            throw new FileNotFoundException("Script '${scriptPath}' not found in paths: ${script_class_path}")
        }

        Binding binding = new Binding()
        testBinding = binding

        GroovyShell shell = new GroovyShell(binding)
        Script script = shell.parse(scriptFile)
        installPipelineDispatch(script, binding)
        return script
    }

    /**
     * Wires Groovy metaclass hooks so unresolved pipeline-style method and property access on
     * the parsed script are delegated to this specification's mock dispatch.
     */
    protected void installPipelineDispatch(Object target, Binding binding = null) {
        Closure methodMissingImpl = { String name, Object args -> dispatchPipelineMethod(name, args) }

        Closure propertyMissingGetter = { String name ->
            return explicitlyMockPipelineVariable(name)
        }

        Closure propertyMissingSetter = { String name, Object value ->
            if (binding != null) {
                binding.setVariable(name, value)
            }
            return value
        }

        target.metaClass.methodMissing = methodMissingImpl
        target.metaClass.propertyMissing = propertyMissingGetter

        if (binding != null) {
            binding.metaClass.methodMissing = methodMissingImpl
            binding.metaClass.propertyMissing = propertyMissingGetter
        }

        target.metaClass.setProperty = { String name, Object value ->
            if (binding != null && delegate.binding?.hasVariable(name)) {
                delegate.binding.setVariable(name, value)
                return value
            }
            MetaProperty metaProperty = delegate.metaClass.getMetaProperty(name)
            if (metaProperty != null) {
                metaProperty.setProperty(delegate, value)
                return value
            }
            propertyMissingSetter(name, value)
        }
    }

    /**
     * Emulates Jenkins step behavior for body-style steps.
     *
     * - for {@code parallel}, each closure value in the supplied map is executed
     * - for other steps, a trailing closure argument is invoked automatically
     */
    protected static void executeStepBodies(String stepName, Object[] args) {
        if (stepName == 'parallel') {
            for (Object arg : args) {
                if (!(arg instanceof Map)) {
                    continue
                }

                Map parallelBranches = (Map) arg
                for (Object branch : parallelBranches.values()) {
                    if (branch instanceof Closure) {
                        ((Closure) branch).call()
                    }
                }
            }
            return
        }

        // Mirror Jenkins body-step behavior for steps like stage('Build') { ... }.
        if (args.length > 0 && args[args.length - 1] instanceof Closure) {
            ((Closure) args[args.length - 1]).call()
        }
    }

    /**
     * Central dispatch used by {@code methodMissing} to route calls either to a shared-library
     * variable impersonator or a regular pipeline step mock.
     */
    protected Object dispatchPipelineMethod(String name, Object args) {
        if (isVariableName(name)) {
            Object[] normalizedArgs = normalizeArgs(args)
            return invokeCallable(explicitlyMockPipelineVariable(name), normalizedArgs)
        }

        Object[] normalizedArgs = normalizeStepArgs(args)
        PipelineCallable mock = (PipelineCallable) getPipelineMock(name)
        Object result = invokeCallable(mock, normalizedArgs)
        executeStepBodies(name, normalizedArgs)
        return result
    }

    protected boolean isVariableName(String name) {
        discoverSharedLibraryVariables()
        return explicitVariableNames.contains(name) ||
                sharedLibraryVariableNames.contains(name)
    }

    protected void discoverSharedLibraryVariables() {
        if (sharedLibraryVariablesDiscovered) {
            return
        }

        script_class_path.each { String path ->
            File varsDirectory = resolveSharedLibraryDirectory(new File(path))
            if (varsDirectory?.exists()) {
                varsDirectory.eachFile { File file ->
                    if (file.isFile() && file.name.endsWith('.groovy')) {
                        sharedLibraryVariableNames << (file.name - '.groovy')
                    }
                }
            }
        }

        sharedLibraryVariablesDiscovered = true
    }

    protected static File resolveSharedLibraryDirectory(File path) {
        if (path.isDirectory() && path.name == 'vars') {
            return path
        }

        File varsDirectory = new File(path, 'vars')
        if (varsDirectory.isDirectory()) {
            return varsDirectory
        }

        return null
    }

    /**
     * Collapses redundant {@code .call} suffixes for ordinary steps while preserving them for
     * shared-library variables, whose callable behavior is represented explicitly in mock names.
     */
    protected String canonicalMockName(String name) {
        if (!name.endsWith('.call')) {
            return name
        }

        String baseName = name - '.call'
        return isVariableName(baseName) ? name : baseName
    }

    protected File resolveScriptFile(String scriptPath) {
        for (String path : script_class_path) {
            File candidate = new File("${path}${scriptPath}")
            if (candidate.exists()) {
                return candidate
            }
        }
        return new File(scriptPath)
    }

    protected static Object[] normalizeArgs(Object args) {
        if (args == null) {
            return [] as Object[]
        }
        if (args instanceof Object[]) {
            return (Object[]) args
        }
        return [args] as Object[]
    }

    protected static Object[] normalizeStepArgs(Object args) {
        if (args == null) {
            return [] as Object[]
        }
        if (args instanceof Object[]) {
            return (Object[]) args
        }
        if (args instanceof List) {
            return ((List) args).toArray()
        }
        return [args] as Object[]
    }

    protected static Object invokeCallable(def callable, Object[] args) {
        switch (args.length) {
            case 0:
                return callable.call()
            case 1:
                return callable.call(args[0])
            case 2:
                return callable.call(args[0], args[1])
            case 3:
                return callable.call(args[0], args[1], args[2])
            case 4:
                return callable.call(args[0], args[1], args[2], args[3])
            case 5:
                return callable.call(args[0], args[1], args[2], args[3], args[4])
            case 6:
                return callable.call(args[0], args[1], args[2], args[3], args[4], args[5])
            case 7:
                return callable.call(args[0], args[1], args[2], args[3], args[4], args[5], args[6])
            case 8:
                return callable.call(args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7])
            default:
                if (callable.metaClass.respondsTo(callable, 'call', args)) {
                    return callable.call(args)
                }
                throw new IllegalArgumentException("Unsupported argument count for pipeline mock: ${args.length}")
        }
    }

    protected PipelineCallable createStepMock(String name) {
        PipelineCallable mock = Mock(PipelineCallable, name: "getPipelineMock(\"${name}\")".toString())
        pipelineMocks[name] = mock
        return mock
    }

    /**
     * Lightweight stand-in for a Jenkins shared-library variable from {@code vars/*.groovy}.
     *
     * It supports:
     * - function-style invocation via {@code someVar(...)} -> {@code someVar.call}
     * - method-style invocation via {@code someVar.foo(...)} -> {@code someVar.foo}
     * - property access via {@code someVar.bar} -> {@code someVar.getProperty}
     */
    protected static class PipelineVariableImpersonator {
        private final String variableName
        private final JenkinsPipelineSpecification specification

        PipelineVariableImpersonator(String variableName, JenkinsPipelineSpecification specification) {
            this.variableName = variableName
            this.specification = specification
        }

        def call(Object... args) {
            PipelineCallable mock = (PipelineCallable) specification.getPipelineMock("${variableName}.call")
            return invokeCallable(mock, args)
        }

        def invokeMethod(String methodName, Object args) {
            PipelineCallable mock = (PipelineCallable) specification.getPipelineMock("${variableName}.${methodName}")
            return invokeCallable(mock, normalizeArgs(args))
        }

        def getProperty(String propertyName) {
            PipelineCallable mock = (PipelineCallable) specification.getPipelineMock("${variableName}.getProperty")
            return mock.call(propertyName)
        }

        String toString() {
            return variableName
        }
    }

}


