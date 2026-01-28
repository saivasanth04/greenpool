# Build stage
FROM maven:3.9.9-eclipse-temurin-21 AS build
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM ubuntu:22.04

# Install Java 21
RUN apt-get update && \
    apt-get install -y openjdk-21-jre && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create app directory and copy jar
WORKDIR /
COPY --from=build /target/backend-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]