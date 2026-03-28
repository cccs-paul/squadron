# Squadron Gateway - Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron
COPY squadron-gateway/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "app.jar"]
