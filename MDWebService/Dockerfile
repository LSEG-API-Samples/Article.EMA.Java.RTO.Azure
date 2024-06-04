FROM --platform=linux/amd64 maven:3.9.6-eclipse-temurin-11-focal as builder
LABEL authors="Developer Relations"
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean -e -B package

#FROM openjdk:11-jre-slim-bullseye
FROM --platform=linux/amd64 eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/MDWebService-0.0.1-SNAPSHOT.jar .
COPY EmaConfig.xml .

# run MDWebService-0.0.1-SNAPSHOT.jar with CMD
CMD ["java", "-jar", "./MDWebService-0.0.1-SNAPSHOT.jar"]
