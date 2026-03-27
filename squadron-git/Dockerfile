# Squadron Git - Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./
COPY squadron-common/pom.xml ./squadron-common/
COPY squadron-git/pom.xml ./squadron-git/
COPY squadron-common/src ./squadron-common/src
COPY squadron-git/src ./squadron-git/src
RUN --mount=type=cache,target=/root/.m2 \
    apk add --no-cache maven && \
    mvn clean package -pl squadron-git -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S squadron && adduser -S squadron -G squadron && \
    apk add --no-cache git openssh-client
COPY --from=build /app/squadron-git/target/*.jar app.jar
RUN chown squadron:squadron app.jar
USER squadron
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
