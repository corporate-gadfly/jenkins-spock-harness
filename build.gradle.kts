import com.vanniktech.maven.publish.DeploymentValidation

plugins {
    // Apply the groovy plugin to add support for Groovy
    groovy
    // Apply the java-library plugin for API and implementation separation
    `java-library`
    // Apply the Maven Publish plugin to publish artifacts to Maven Central
    alias(libs.plugins.maven.publish)
    // Apply the Axion Release plugin for versioning and release management
    alias(libs.plugins.axion.release)
}

group = "io.github.corporate-gadfly"
project.version = scmVersion.version

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

scmVersion {
    tag {
        prefix.set("v")
    }
    checks {
        uncommittedChanges.set(true)
    }
    // Ensures stateless CI runs only forward tag updates cleanly
    repository {
        pushTagsOnly.set(true)
    }
}

sourceSets {
    main {
        groovy.srcDirs("src")
    }
    test {
        groovy.srcDirs("test")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.groovy)

    api(libs.spock.core)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

mavenPublishing {
    coordinates(project.group.toString(), project.name, project.version.toString())

    // Instead of automatic releases, allow Drop / Publish button to show up in Maven Central UI
    publishToMavenCentral(automaticRelease = false, validateDeployment = DeploymentValidation.VALIDATED)
    signAllPublications()

    pom {
        name = "Jenkins Spock Testing Harness"
        description = "A lightweight, JDK 21+ optimized framework for fast unit testing of Jenkins Shared Libraries using Spock."
        inceptionYear = "2026"
        url = "https://github.com/corporate-gadfly/jenkins-spock-harness"
        licenses {
            license {
                name = "The MIT License"
                url = "https://opensource.org/license/mit"
            }
        }
        developers {
            developer {
                id = "corporate-gadfly"
                name = "Haroon Rafique"
                url = "https://github.com/corporate-gadfly"
            }
        }
        scm {
            url = "https://github.com/corporate-gadfly/jenkins-spock-harness"
            connection = "scm:git:git://github.com/corporate-gadfly/jenkins-spock-harness.git"
            developerConnection = "scm:git:ssh://git@github.com/corporate-gadfly/jenkins-spock-harness.git"
        }
    }
}
