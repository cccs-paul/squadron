# Squadron Orchestrator - Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron
COPY squadron-orchestrator/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
