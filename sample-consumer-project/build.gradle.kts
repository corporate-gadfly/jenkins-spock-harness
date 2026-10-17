plugins {
    groovy
}

repositories {
    mavenCentral()
}

sourceSets {
    main { groovy.srcDirs("src") }
    test { groovy.srcDirs("test") }
}

dependencies {
    implementation(libs.groovy)
    testImplementation(libs.jenkins.spock.harness)
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
    }
}
