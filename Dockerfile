FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml pom.xml
COPY .mvn/settings.xml .mvn/settings.xml
COPY base-common base-common
COPY base-security base-security
COPY system-service system-service
COPY sql sql
RUN mvn -B -s .mvn/settings.xml -pl system-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/system-service/target/*-exec.jar app.jar

ENV SERVER_PORT=10000
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "app.jar"]
