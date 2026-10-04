# syntax=docker/dockerfile:1
FROM eclipse-temurin:25.0.4_7-jdk-noble AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY .mvn .mvn
COPY --chmod=755 mvnw mvnw
COPY pom.xml .
# Cache dependencies in this layer without requiring a Railway-specific cache ID.
RUN ./mvnw --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode --no-transfer-progress verify

FROM eclipse-temurin:25.0.4_7-jre-noble
RUN groupadd --gid 10001 erro && useradd --uid 10001 --gid erro --no-create-home erro
WORKDIR /app
COPY --from=build --chown=erro:erro /app/target/backend-0.0.1-SNAPSHOT.jar app.jar
USER erro
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
