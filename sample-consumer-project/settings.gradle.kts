rootProject.name = "sample-consumer-project"

if (file("../settings.gradle.kts").exists()) {
    includeBuild("../") {
        dependencySubstitution {
            // Forces ANY version string requested by the consumer to route directly to root project
            substitute(module("io.github.corporate-gadfly:jenkins-spock-harness"))
                .using(project(":"))
        }
    }
}
