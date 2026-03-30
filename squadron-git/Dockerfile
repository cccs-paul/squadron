# Squadron Git - Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron && \
    sed -i 's|https://|http://|g' /etc/apk/repositories && \
    apk add --no-cache git openssh-client
COPY squadron-git/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
