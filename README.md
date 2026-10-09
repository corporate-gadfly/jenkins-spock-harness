# Jenkins Spock Harness

A lightweight, modern, JDK 21+ optimized framework for fast unit testing of Jenkins Shared Libraries using Spock Framework and Groovy 4.

[![CI](https://github.com/corporate-gadfly/jenkins-spock-harness/actions/workflows/ci.yml/badge.svg)](https://github.com/corporate-gadfly/jenkins-spock-harness/actions/workflows/ci.yml)
[![Publish to Maven Central](https://github.com/corporate-gadfly/jenkins-spock-harness/actions/workflows/release.yml/badge.svg)](https://github.com/corporate-gadfly/jenkins-spock-harness/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Prerequisites & Installation](#prerequisites--installation)
  - [Gradle (Kotlin DSL)](#gradle-kotlin-dsl)
  - [Gradle (Groovy DSL)](#gradle-groovy-dsl)
  - [Maven](#maven)
- [Directory Structure](#directory-structure)
- [Testing Shared Library Flavours](#testing-shared-library-flavours)
  - [1. Global Callable Step (`call(...)`)](#1-global-callable-step-call)
  - [2. Helper Script Object (`return this`)](#2-helper-script-object-return-this)
  - [3. Pipeline Template / Orchestrator](#3-pipeline-template--orchestrator)
- [Testing Mechanics & Conventions](#testing-mechanics--conventions)
  - [Pipeline Step Mocking (`getPipelineMock`)](#pipeline-step-mocking-getpipelinemock)
  - [Explicit Pipeline Variables (`explicitlyMockPipelineVariable`)](#explicit-pipeline-variables-explicitlymockpipelinevariable)
  - [Setting Pipeline Variables & Bindings (`.getBinding().setVariable`)](#setting-pipeline-variables--bindings-getbindingsetvariable)
  - [Shared Library Variable Invocations (`<name>.call`)](#shared-library-variable-invocations-namecall)
  - [Automatic Closure Execution (Block Steps & `parallel`)](#automatic-closure-execution-block-steps--parallel)
  - [Simulating Failures & Exceptions](#simulating-failures--exceptions)
  - [Stubbing Library Resources (`libraryResource`)](#stubbing-library-resources-libraryresource)
- [Running Tests](#running-tests)
- [License](#license)

---

## Overview

Unit testing Jenkins Shared Libraries often poses challenges due to dynamic runtime bindings, pipeline DSL steps (`sh`, `stage`, `node`, `withCredentials`), and complex variable resolution.

**Jenkins Spock Harness** provides a fast, zero-daemon, pure Spock-based testing harness compatible with **Java 21+** and **Groovy 4**:
- Dynamic step mocking and argument validation.
- Auto-discovery and explicit mocking of shared library variables located in `vars/`.
- Automatic closure invocation for container steps (`node`, `stage`, `dir`, etc.) and `parallel` branches.
- Full control over script bindings, global constants, and environmental variables.

---

## Key Features

- **JDK 21+ & Groovy 4 Ready:** Native support for modern JVMs without bytecode manipulation issues.
- **Fast Execution:** Pure in-memory unit tests that execute in milliseconds without spinning up a Jenkins instance or Docker container.
- **Natural Spock Assertions:** Full access to Spock's expressive interaction testing (`1 * getPipelineMock('sh')('...')`, argument constraints, regex matchers).
- **Closure Auto-Execution:** Automatically executes closures passed to nested steps (`stage`, `node`, `sshagent`, `dir`) and parallel execution maps.
- **Inter-Library Call Interception:** Calls between different `vars/` scripts are automatically routed and verified via `<scriptName>.call`.

---

## Prerequisites & Installation

- **JDK:** 21 or higher
- **Groovy:** 4.0+
- **Spock:** 2.4+ (Groovy 4 variant)

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    testImplementation("io.github.corporate-gadfly:jenkins-spock-harness:0.1.0")
    testImplementation("org.spockframework:spock-core:2.4-groovy-4.0")
    testImplementation("org.apache.groovy:groovy:4.0.33")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
```

### Gradle (Groovy DSL)

```groovy
repositories {
    mavenCentral()
}

dependencies {
    testImplementation 'io.github.corporate-gadfly:jenkins-spock-harness:0.1.0'
    testImplementation 'org.spockframework:spock-core:2.4-groovy-4.0'
    testImplementation 'org.apache.groovy:groovy:4.0.33'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

test {
    useJUnitPlatform()
}
```

### Maven

```xml
<dependency>
    <groupId>io.github.corporate-gadfly</groupId>
    <artifactId>jenkins-spock-harness</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.spockframework</groupId>
    <artifactId>spock-core</artifactId>
    <version>2.4-groovy-4.0</version>
    <scope>test</scope>
</dependency>
```

---

## Directory Structure

A consumer project using this library in Gradle or Maven typically follows the standard source tree hierarchy:

```text
├── src/
│   ├── main/groovy/                   # Companion Groovy/Java classes (optional)
│   └── test/groovy/                   # Spock test specifications
│       └── com/example/
│           └── HelloSpec.groovy
├── vars/                              # Jenkins global step scripts
│   └── hello.groovy
└── build.gradle.kts                   # Or build.gradle / pom.xml
```

---

## Testing Shared Library Flavours

Shared library scripts generally follow one of three main patterns.

### 1. Global Callable Step (`call(...)`)

A script in `vars/` implementing a `call(...)` method, invoked as a function in Jenkinsfiles (e.g. `exec("ls")` or `deployer("test")`).

```groovy
// vars/exec.groovy
void call(String command) {
    if (isUnix()) {
        sh command
    } else {
        bat command
    }
}
```

```groovy
// src/test/groovy/com/example/ExecSpec.groovy
package com.example

import jenkins.spock.harness.JenkinsPipelineSpecification

class ExecSpec extends JenkinsPipelineSpecification {
    def exec

    def setup() {
        script_class_path = ["vars"]
        exec = loadPipelineScriptForTest("/exec.groovy")
    }

    def "executes sh on Linux"() {
        given:
        getPipelineMock('isUnix')() >> true

        when:
        exec('ls -la')

        then:
        1 * getPipelineMock('sh')('ls -la')
        0 * getPipelineMock('bat')(_)
    }
}
```

---

### 2. Helper Script Object (`return this`)

A script in `vars/` exposing multiple related methods and ending with `return this`. In Jenkinsfiles, users call methods on the step (e.g. `deployerHelper.deploy("test")`).

```groovy
// vars/deployerHelper.groovy
def deploy(String env) {
    sshagent(["ssh-key"]) {
        sh "deploy --target ${env}"
    }
}

return this
```

```groovy
// src/test/groovy/com/example/DeployerHelperSpec.groovy
package com.example

import jenkins.spock.harness.JenkinsPipelineSpecification

class DeployerHelperSpec extends JenkinsPipelineSpecification {
    def deployerHelper

    def setup() {
        script_class_path = ["vars"]
        deployerHelper = loadPipelineScriptForTest("/deployerHelper.groovy")
    }

    def "deploys to test"() {
        when:
        deployerHelper.deploy("test")

        then:
        1 * getPipelineMock("sshagent")(["ssh-key"], _ as Closure)
        1 * getPipelineMock("sh")("deploy --target test")
    }
}
```

---

### 3. Pipeline Template / Orchestrator

Standardized end-to-end pipeline definitions that coordinate stages, bindings, and calls to other shared library steps.

```groovy
// vars/DefaultPipeline.groovy
def call() {
    node {
        stage("Build") { sh "docker build -t app ." }
        stage("Deploy") {
            Deployer(BRANCH_NAME == "master" ? "production" : "test")
        }
    }
}
```

```groovy
// src/test/groovy/com/example/DefaultPipelineSpec.groovy
package com.example

import jenkins.spock.harness.JenkinsPipelineSpecification

class DefaultPipelineSpec extends JenkinsPipelineSpecification {
    def pipeline

    def setup() {
        script_class_path = ["vars"]
        pipeline = loadPipelineScriptForTest("/DefaultPipeline.groovy")
    }

    def "deploys to production when on master branch"() {
        given:
        pipeline.getBinding().setVariable("BRANCH_NAME", "master")

        when:
        pipeline()

        then:
        1 * getPipelineMock("sh")("docker build -t app .")
        1 * getPipelineMock("Deployer.call")("production")
    }
}
```

---

## Testing Mechanics & Conventions

### Pipeline Step Mocking (`getPipelineMock`)

All standard Jenkins pipeline steps (`sh`, `bat`, `echo`, `error`, `checkout`, `slackSend`, `stage`, `node`, `withCredentials`, etc.) are lazily registered and accessed using `getPipelineMock(stepName)`:

```groovy
// Verify step interaction
1 * getPipelineMock('sh')('git status')

// Verify step with Spock argument matchers or regex
1 * getPipelineMock('sh')({ it =~ /docker build/ })

// Stub return values
getPipelineMock('isUnix')() >> true
getPipelineMock('sh')([script: 'hostname', returnStdout: true]) >> "worker-1"
```

---

### Explicit Pipeline Variables (`explicitlyMockPipelineVariable`)

`explicitlyMockPipelineVariable(String varName)` explicitly registers a pipeline variable name and returns a `PipelineVariableImpersonator`.

#### Why and When to Use It:
1. **Auto-Discovery vs Explicit Mocking:**
   - Files in `vars/*.groovy` are automatically discovered when `script_class_path = ["vars"]`.
   - If your pipeline references global variables, external shared libraries, or plugin steps that are **not** present in your local `vars/` directory, use `explicitlyMockPipelineVariable('varName')` to register them as pipeline variables.
2. **Mocking Custom Method Invocations on Variables:**
   - When a script invokes `myVar.customMethod('arg')`, the impersonator delegates to `getPipelineMock('myVar.customMethod')('arg')`.
3. **Mocking Property Access on Variables:**
   - When a script accesses `myVar.someProperty`, the impersonator delegates to `getPipelineMock('myVar.getProperty')('someProperty')`.
4. **Standalone Tests:**
   - In unit tests where `loadPipelineScriptForTest` is not called, it allows creating variable impersonators on the fly.

#### Usage Example:

```groovy
// src/test/groovy/com/example/CustomStepSpec.groovy
package com.example

import jenkins.spock.harness.JenkinsPipelineSpecification

class CustomStepSpec extends JenkinsPipelineSpecification {

    def "mocks external shared variable and its properties"() {
        given: "Register an external shared library variable not in local vars/"
        def notifyVar = explicitlyMockPipelineVariable('externalNotifier')

        and: "Stub a property on the variable"
        getPipelineMock('externalNotifier.getProperty')('channel') >> '#alerts'

        when: "The variable is accessed and called"
        def targetChannel = notifyVar.channel
        notifyVar('Deployment complete')
        notifyVar.sendUrgentAlert('Build failed')

        then: "Interactions are verified on canonical mock names"
        targetChannel == '#alerts'
        1 * getPipelineMock('externalNotifier.call')('Deployment complete')
        1 * getPipelineMock('externalNotifier.sendUrgentAlert')('Build failed')
    }
}
```

---

### Shared Library Variable Invocations (`<name>.call`)

When one shared library script invokes another shared library script (e.g. `DefaultPipeline` calling `Deployer("production")`), the harness automatically routes the invocation to `<varName>.call`:

```groovy
// In pipeline script: Deployer("production")
1 * getPipelineMock("Deployer.call")("production")

// In pipeline script calling a helper method: myHelper.run("test")
1 * getPipelineMock("myHelper.run")("test")
```

---

### Automatic Closure Execution (Block Steps & `parallel`)

- **Nested / Block Steps:** Any step receiving a trailing `Closure` (such as `node { ... }`, `stage('...') { ... }`, `dir('...') { ... }`, `sshagent(...) { ... }`) automatically executes that closure.
- **`parallel` Step:** Passing a map of closures to `parallel(...)` automatically executes every closure branch.

```groovy
// In script:
parallel(
    unitTests: { sh 'mvn test' },
    integrationTests: { sh 'mvn verify' }
)

// In Spock test:
1 * getPipelineMock('parallel')(_)
1 * getPipelineMock('sh')('mvn test')
1 * getPipelineMock('sh')('mvn verify')
```

---

### Setting Pipeline Variables & Bindings (`.getBinding().setVariable`)

Jenkins pipelines heavily rely on dynamic global runtime bindings injected by Jenkins plugins and the controller runtime (e.g. `BRANCH_NAME`, `env`, `params`, `scm`, `currentBuild`). 

When loaded with `loadPipelineScriptForTest(...)`, the returned pipeline instance is a Groovy `Script` object backed by a `Binding`. You can inject or override any global variable directly using `.getBinding().setVariable(name, value)`.

#### Common Use Cases:

1. **Initializing Global Defaults in `setup()`:**
   Supply baseline stubs for Jenkins objects that are referenced across tests:
   ```groovy
   def setup() {
       script_class_path = ["vars"]
       pipeline = loadPipelineScriptForTest("/DefaultPipeline.groovy")
       
       // Provide baseline pipeline variables
       pipeline.getBinding().setVariable("scm", null)
       pipeline.getBinding().setVariable("env", [BUILD_NUMBER: "101"])
   }
   ```

2. **Testing Conditional Branch Logic in `given:` Blocks:**
   Test branch-specific execution pathways by setting variables prior to executing the pipeline script:
   ```groovy
   def "deploys to production when on master branch"() {
       given: "Simulate running on the master branch"
       pipeline.getBinding().setVariable("BRANCH_NAME", "master")

       when:
       pipeline()

       then:
       1 * getPipelineMock("Deployer.call")("production")
   }

   def "does not deploy to production on feature branches"() {
       given: "Simulate running on a feature branch"
       pipeline.getBinding().setVariable("BRANCH_NAME", "feature/XYZ")

       when:
       pipeline()

       then:
       0 * getPipelineMock("Deployer.call")("production")
   }
   ```

---

### Simulating Failures & Exceptions

Test error handling, `catchError`, or notification blocks by throwing an exception from a mock:

```groovy
getPipelineMock("sh")("docker push my-image") >> {
    throw new RuntimeException("Registry connection timed out")
}
```

---

### Stubbing Library Resources (`libraryResource`)

For scripts loading template files or configurations via `libraryResource(...)`:

```groovy
getPipelineMock("libraryResource")("templates/notify.json") >> {
    return '{"status": "failure"}'
}
```

---

## Running Tests

Execute the unit tests using Gradle:

```bash
# Run all Spock tests
./gradlew test

# Run a specific test class
./gradlew test --tests "DefaultPipelineSpec"
```

---

## License

This project is licensed under the [MIT License](LICENSE).
