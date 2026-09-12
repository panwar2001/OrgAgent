# ---------- build stage ----------
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /workspace

# Dependency layer: only re-resolved when the build files change.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon --quiet dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------- runtime stage ----------
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S orgagent && adduser -S orgagent -G orgagent
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown -R orgagent:orgagent /app
USER orgagent

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
