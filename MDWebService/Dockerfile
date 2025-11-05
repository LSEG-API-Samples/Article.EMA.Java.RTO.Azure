FROM docker.io/maven:3.9.11-eclipse-temurin-17-noble as builder
LABEL authors="Developer Relations"
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean -e -B package

#FROM openjdk:11-jre-slim-bullseye
#FROM docker.io/eclipse-temurin:11-jre-alpine
FROM docker.io/amazoncorretto:17.0.17-alpine3.20
WORKDIR /app
COPY --from=builder /app/target/MDWebService-0.0.1-SNAPSHOT.jar .
COPY EmaConfig.xml .

# run MDWebService-0.0.1-SNAPSHOT.jar with CMD
CMD ["java", "-jar", "./MDWebService-0.0.1-SNAPSHOT.jar"]
