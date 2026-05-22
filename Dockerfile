# syntax=docker/dockerfile:1.7
###############################################################################
# Build stage — uses the committed Gradle wrapper so we don't depend on the
# Gradle base image keeping up with Java releases.
###############################################################################
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Cache dependencies first
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test

###############################################################################
# Runtime stage
###############################################################################
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as a non-root user
RUN groupadd --system app && useradd --system --gid app --home /app app

COPY --from=build /workspace/build/libs/wex-challenge.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_OPTS=""

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
