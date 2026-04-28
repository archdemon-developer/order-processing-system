plugins {
    kotlin("jvm")
    kotlin("plugin.jpa")
}

repositories {
    mavenCentral()
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

tasks.withType<JacocoCoverageVerification> {
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("jacoco/*.exec")
        },
    )
    classDirectories.setFrom(
        fileTree("${layout.buildDirectory.get()}/classes/kotlin/main") {
            exclude("**/outbox/OutboxEvent.class")
        },
    )
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.4"))
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation(platform("tools.jackson:jackson-bom:3.1.0"))
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.0")
    implementation("org.apache.kafka:kafka-clients:4.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testImplementation("io.mockk:mockk:1.14.9")
}
