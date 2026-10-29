# Containerfile / Dockerfile for OpenShift On-Premises Deployment
# Base image: Red Hat Universal Base Image (UBI 9) OpenJDK 21 Runtime
#
# Note for Jenkins CI (Nexus artifact pull):
# Replace COPY below with:
#   ARG URL_TO_ARTIFACTS
#   RUN curl -o /deployments/app.jar $URL_TO_ARTIFACTS

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

LABEL maintainer="Enterprise Architecture Team" \
      app="payment-service" \
      version="1.0.0" \
      description="On-Premises Payment Microservice"

USER 185
WORKDIR /deployments

COPY target/payment-service-*.jar /deployments/app.jar

ENV JAVA_APP_JAR="/deployments/app.jar" \
    JAVA_OPTIONS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom" \
    SERVER_PORT="8443"

EXPOSE 8443

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTIONS -jar $JAVA_APP_JAR"]
