#FROM eclipse-temurin:17-jdk-jammy AS build
#
#WORKDIR /workspace
#
#COPY .mvn .mvn
#COPY mvnw pom.xml ./
#RUN ./mvnw --batch-mode -DskipTests dependency:go-offline
#
#COPY src src
#RUN ./mvnw --batch-mode -DskipTests package
#
#FROM eclipse-temurin:17-jre-jammy
#
#WORKDIR /app
#
#RUN groupadd --system wallet && useradd --system --gid wallet wallet
#
#COPY --from=build /workspace/target/*.jar app.jar
#
#USER wallet
#EXPOSE 8080
#
#ENTRYPOINT ["java", "-jar", "/app/app.jar"]


# Build stage
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Copy only the pom.xml first to leverage Docker layer caching for dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the source code and build the application
COPY src ./src
RUN mvn clean package -DskipTests -q

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Security: Run as a non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
COPY --from=builder /app/target/*.jar app.jar
USER appuser

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]