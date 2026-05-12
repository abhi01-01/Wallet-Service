FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw --batch-mode -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd --system wallet && useradd --system --gid wallet wallet

COPY --from=build /workspace/target/*.jar app.jar

USER wallet
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
