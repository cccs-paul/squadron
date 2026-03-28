# Squadron Review - Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron
COPY squadron-review/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
