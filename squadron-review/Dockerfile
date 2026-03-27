# Squadron Review - Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY squadron-common/pom.xml ./squadron-common/
COPY squadron-review/pom.xml ./squadron-review/
COPY squadron-common/src ./squadron-common/src
COPY squadron-review/src ./squadron-review/src
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn clean package -pl squadron-review -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron
COPY --from=build /app/squadron-review/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "app.jar"]
