# Squadron Gateway - Dockerfile
# Multi-stage build: Maven build -> lightweight JRE runtime
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy parent POM and module POMs for dependency caching
COPY pom.xml ./
COPY squadron-common/pom.xml ./squadron-common/
COPY squadron-gateway/pom.xml ./squadron-gateway/

# Copy source code
COPY squadron-common/src ./squadron-common/src
COPY squadron-gateway/src ./squadron-gateway/src

# Build the module (skip tests for Docker image builds)
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn clean package -pl squadron-gateway -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron
COPY --from=build /app/squadron-gateway/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "app.jar"]
