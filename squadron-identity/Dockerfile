# Squadron Identity - Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY squadron-common/pom.xml ./squadron-common/
COPY squadron-identity/pom.xml ./squadron-identity/
COPY squadron-common/src ./squadron-common/src
COPY squadron-identity/src ./squadron-identity/src
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn clean package -pl squadron-identity -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron
COPY --from=build /app/squadron-identity/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
