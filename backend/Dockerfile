# syntax=docker/dockerfile:1
#
# Multi-stage build for the HelpDesk Spring Boot backend (Phase 5, Milestone 1
# — containerization only; no application/business-logic changes).
#
# Stage 1 builds the executable jar with the project's own Maven Wrapper, so
# the image always builds with the exact Maven version pinned in
# .mvn/wrapper/maven-wrapper.properties — never whatever Maven happens to be
# on the builder's PATH. Stage 2 copies only that jar into a minimal JRE
# image; the JDK, Maven wrapper, and full source tree never reach the
# runtime image.

########## Stage 1: build ##########
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# .mvn/wrapper/maven-wrapper.jar is deliberately gitignored in this repo
# ("auto-downloaded on first mvnw run, not committed") — mvnw bootstraps it
# itself via curl/wget on first invocation, so a fresh clone (no jar present)
# still builds. curl isn't in this base image by default.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Copy just the wrapper + POM first so dependency resolution is cached in
# its own layer and only re-runs when pom.xml actually changes, not on every
# source edit.
COPY mvnw pom.xml ./
COPY .mvn .mvn

# mvnw is checked into this repo with CRLF line endings (Windows checkout) —
# `#!/bin/sh\r` fails to execute on Linux, so normalize before first use.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Best-effort dependency pre-fetch to warm the layer cache; not fatal if it
# can't resolve every plugin dependency this way (`package` below always
# does the authoritative resolution).
RUN ./mvnw -B dependency:go-offline || true

COPY src src

# Tests already run in their own CI/dev workflow (see docs/06-Testing.md) —
# this image build packages the artifact, it does not re-verify it.
RUN ./mvnw -B clean package -DskipTests

########## Stage 2: runtime ##########
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Non-root runtime user (requirement: "run as a non-root user if practical").
RUN addgroup -S helpdesk && adduser -S helpdesk -G helpdesk

COPY --from=build --chown=helpdesk:helpdesk /workspace/target/*.jar app.jar

USER helpdesk

# Matches server.port's default (application.yml) — override both this and
# SERVER_PORT together if the app is ever configured to listen elsewhere.
EXPOSE 8080

# GET /api/v1/health is public (no auth) and checks real DB connectivity
# (HealthController/HealthService) — a truer readiness signal than a bare
# TCP connect. wget is BusyBox-provided on this alpine base, no extra package needed.
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=5 \
  CMD wget -q --spider http://localhost:8080/api/v1/health || exit 1

# SPRING_PROFILES_ACTIVE (and every other Spring property) is supplied at
# `docker run`/Compose level via env vars — nothing environment-specific is
# hardcoded into the image itself.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
