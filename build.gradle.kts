plugins {
    // Apply the groovy plugin to add support for Groovy
    groovy
    // Apply the java-library plugin for API and implementation separation
    `java-library`
    // Apply the Maven Publish plugin to publish artifacts to Maven Central
    alias(libs.plugins.maven.publish)
}

group = "io.github.corporate-gadfly"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
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
