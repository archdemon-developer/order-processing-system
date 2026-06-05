FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
RUN ./gradlew --version --no-daemon -Dorg.gradle.internal.http.socketTimeout=120000 -Dorg.gradle.internal.http.connectionTimeout=120000
COPY . .
RUN ./gradlew :notification-service:bootJar --no-daemon -Dorg.gradle.internal.http.socketTimeout=120000 -Dorg.gradle.internal.http.connectionTimeout=120000

FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/notification-service/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=extractor /app/app/dependencies/ ./
COPY --from=extractor /app/app/spring-boot-loader/ ./
COPY --from=extractor /app/app/snapshot-dependencies/ ./
COPY --from=extractor /app/app/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]