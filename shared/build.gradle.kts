plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("tools.jackson:jackson-bom:3.1.0"))
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.0")
    implementation("org.apache.kafka:kafka-clients:4.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.0.3")
    testImplementation("io.mockk:mockk:1.14.9")
}