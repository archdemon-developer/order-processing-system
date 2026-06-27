# --- Stage 1: Build ---
FROM gradle:jdk21-alpine AS builder
WORKDIR /app
COPY settings.gradle.kts build.gradle.kts ./
COPY shared/build.gradle.kts ./shared/
COPY order-service/build.gradle.kts ./order-service/
RUN gradle :order-service:dependencies --no-daemon || true
COPY . .
RUN gradle :order-service:bootJar --no-daemon

# --- Stage 2: Extract ---
FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/order-service/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted


# --- Stage 3: Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=extractor /app/extracted/dependencies/ ./
COPY --from=extractor /app/extracted/spring-boot-loader/ ./
COPY --from=extractor /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor /app/extracted/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]