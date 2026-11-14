# syntax=docker/dockerfile:1

# ==============================================================================
# PromptForge - production image
#   build :  docker build -t prompt-forge:1.0.0 .
#   run   :  docker run --rm -p 8080:8080 --env-file deploy/prompt-forge.env prompt-forge:1.0.0
# ==============================================================================

# ----------------------------------------------------------------- build stage
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Resolve dependencies in their own layer so that source-only changes do not
# re-download the world.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

# The tests run inside the image build: only tested code can reach production.
COPY src ./src
RUN mvn -B -ntp clean package

# --------------------------------------------------------------- runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime

# curl is used by the health check only. BusyBox wget would also work and would
# remove the need for this package, but curl keeps the check identical on every base.
RUN apk add --no-cache curl \
    && addgroup -S -g 1001 promptforge \
    && adduser -S -u 1001 -G promptforge -h /app -s /sbin/nologin promptforge

WORKDIR /app

COPY --from=build --chown=promptforge:promptforge /build/target/prompt-forge-*.jar /app/prompt-forge.jar

USER promptforge

EXPOSE 8080

# MaxRAMPercentage lets the JVM size its heap from the container limit instead of
# the host memory. ExitOnOutOfMemoryError makes the orchestrator restart it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError" \
    SERVER_PORT=8080 \
    SPRING_MAIN_BANNER_MODE=off

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/api/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/prompt-forge.jar"]
