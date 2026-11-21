FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle build.gradle ./
COPY modules modules
COPY src src
RUN chmod +x gradlew && ./gradlew --no-daemon --console=plain bootJar

FROM eclipse-temurin:25-jdk-alpine
RUN addgroup -S rafaga && adduser -S rafaga -G rafaga
WORKDIR /app
COPY --from=build --chown=rafaga:rafaga /workspace/build/libs/*.jar /app/rafaga-backend.jar
USER rafaga
EXPOSE 3117
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:3117/actuator/health/liveness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/rafaga-backend.jar"]
