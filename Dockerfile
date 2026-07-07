# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=docker.io/library/maven:3.9.6-eclipse-temurin-17
ARG RUNTIME_IMAGE=docker.io/library/eclipse-temurin:17-jre-jammy

FROM ${MAVEN_IMAGE} AS builder
WORKDIR /app

COPY pom.xml .

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q clean package -DskipTests

FROM ${RUNTIME_IMAGE}
WORKDIR /app

RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

USER appuser
ENV SERVER_PORT=8081
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8081

ENTRYPOINT ["java", "-XX:+UseSerialGC", "-Xmx512m", "-Xss512k", "-jar", "app.jar"]
