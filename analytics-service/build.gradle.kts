import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

apply(plugin = "com.diffplug.spotless")

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    kotlin {
        target("**/*.kt")
        ktlint("1.7.1")
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint("1.7.1")
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
    extensions.configure(JacocoTaskExtension::class) {
        destinationFile = file("${layout.buildDirectory.get()}/jacoco/test.exec")
    }
}

val integrationTest by tasks.registering(Test::class) {
    useJUnitPlatform {
        includeTags("integration")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    extensions.configure(JacocoTaskExtension::class) {
        destinationFile = file("${layout.buildDirectory.get()}/jacoco/integrationTest.exec")
    }
}

tasks.jacocoTestReport {
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("jacoco/*.exec")
        },
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<JacocoCoverageVerification> {
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("jacoco/*.exec")
        },
    )
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.apache.kafka:kafka-streams")
    implementation(project(":shared")) {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-data-jpa")
    }

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("io.mockk:mockk:1.14.9")
    testImplementation("com.ninja-squad:springmockk:5.0.1")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.awaitility:awaitility:4.2.2")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.4"))
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
}
