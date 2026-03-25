import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    id("org.springframework.boot")
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1")
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.15.2")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")

    implementation(project(":shared"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}