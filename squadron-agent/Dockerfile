# Squadron Agent - Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY squadron-common/pom.xml ./squadron-common/
COPY squadron-agent/pom.xml ./squadron-agent/
COPY squadron-common/src ./squadron-common/src
COPY squadron-agent/src ./squadron-agent/src
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn clean package -pl squadron-agent -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron
COPY --from=build /app/squadron-agent/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
