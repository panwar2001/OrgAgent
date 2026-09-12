# ---------- build stage ----------
# Gradle 9.2.1 and JDK 25 come from this image on purpose: the Wrapper downloads its
# distribution from services.gradle.org, which is not reachable from every network, and a
# failed download breaks the build. The image's Gradle is the same version as the wrapper's.
FROM gradle:9.2.1-jdk25-alpine AS build

# The gradle image runs as an unprivileged user; the build writes into the workspace.
USER root
WORKDIR /workspace

# Dependency layer: only re-resolved when the build files change.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN gradle --no-daemon --quiet dependencies --configuration runtimeClasspath > /dev/null 2>&1 || true

COPY src ./src
RUN gradle --no-daemon clean bootJar -x test

# ---------- runtime stage ----------
FROM eclipse-temurin:25-jre-alpine AS runtime

RUN addgroup -S orgagent && adduser -S orgagent -G orgagent
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar /app/app.jar
RUN chown -R orgagent:orgagent /app
USER orgagent

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

# /v3/api-docs is public in the security configuration; /actuator/health would be denied
# because this service does not depend on the actuator starter.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/v3/api-docs > /dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
