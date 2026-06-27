# --- Stage 1: Build ---
FROM gradle:jdk21-alpine AS builder
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY shared/build.gradle.kts ./shared/
COPY analytics-service/build.gradle.kts ./analytics-service/
RUN gradle :analytics-service:dependencies --no-daemon || true
COPY . .
RUN gradle :analytics-service:bootJar --no-daemon

# --- Stage 2: Extract ---
FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/analytics-service/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# --- Stage 3: Runtime (jammy for RocksDB native lib compatibility) ---
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=extractor /app/extracted/dependencies/ ./
COPY --from=extractor /app/extracted/spring-boot-loader/ ./
COPY --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor /app/extracted/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]