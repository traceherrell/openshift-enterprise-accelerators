# Containerfile / Dockerfile for OpenShift On-Premises Deployment
# Base image: Red Hat Universal Base Image (UBI 9) OpenJDK 21 Runtime
#
# Optional Multi-Stage Builder (if compiling inside container):
# FROM registry.access.redhat.com/ubi9/openjdk-21:latest AS builder
# USER root
# WORKDIR /build
# COPY payment-service/pom.xml .
# COPY payment-service/src ./src
# RUN mvn clean package -DskipTests

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

LABEL maintainer="Enterprise Architecture Team" \
      app="payment-service" \
      version="1.0.0" \
      description="On-Premises Payment Microservice"

USER 185
WORKDIR /deployments

COPY payment-service/target/payment-service-*.jar /deployments/app.jar

ENV JAVA_APP_JAR="/deployments/app.jar" \
    JAVA_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT="8443"

EXPOSE 8443

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTIONS -jar $JAVA_APP_JAR"]
