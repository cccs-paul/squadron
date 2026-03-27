# Squadron Workspace - Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY squadron-common/pom.xml ./squadron-common/
COPY squadron-workspace/pom.xml ./squadron-workspace/
COPY squadron-common/src ./squadron-common/src
COPY squadron-workspace/src ./squadron-workspace/src
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn clean package -pl squadron-workspace -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron
COPY --from=build /app/squadron-workspace/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
