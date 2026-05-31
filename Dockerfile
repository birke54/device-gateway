# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
# Builds the Spring Boot fat jar with the project's Gradle wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy the wrapper and build scripts first so the (slow) dependency download
# is cached in its own layer and only re-runs when the build files change.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now the sources, then the build itself.
COPY src ./src
# Tests are skipped here on purpose: the @SpringBootTest spins up live UPnP/mDNS
# multicast discovery, which has no LAN to talk to inside the build sandbox.
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# gosu lets the entrypoint drop from root to an arbitrary uid/gid at startup.
RUN apt-get update \
    && apt-get install -y --no-install-recommends gosu \
    && rm -rf /var/lib/apt/lists/*

# Persisted state: pairing tokens and the log file (see the prod profile).
RUN mkdir -p /data
VOLUME ["/data"]

COPY --from=build /workspace/build/libs/*-SNAPSHOT.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# The Docker-oriented config lives in the "prod" Spring profile.
# PUID/PGID default to Unraid's nobody:users; override to match your appdata.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="" \
    SERVER_PORT=8080 \
    PUID=99 \
    PGID=100

# Informational only when running with host networking (which this app needs
# for LAN discovery); the embedded server listens on SERVER_PORT (default 8080).
EXPOSE 8080

# Liveness via the actuator health endpoint exposed by the prod profile.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD bash -c 'exec 3<>/dev/tcp/localhost/${SERVER_PORT:-8080} && echo -e "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q UP <&3' || exit 1

ENTRYPOINT ["docker-entrypoint.sh"]
