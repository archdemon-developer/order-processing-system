FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :payment-service:bootJar --no-daemon

FROM eclipse-temurin:21-jdk-alpine AS extractor
WORKDIR /app
COPY --from=builder /app/payment-service/build/libs/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=extractor /app/app/dependencies/ ./
COPY --from=extractor /app/app/spring-boot-loader/ ./
COPY --from=extractor /app/app/snapshot-dependencies/ ./
COPY --from=extractor /app/app/application/ ./

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]