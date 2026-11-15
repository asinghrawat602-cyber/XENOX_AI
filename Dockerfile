
FROM maven:3.9-eclipse-temurin-27 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:27-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 1313

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-1313} -jar app.jar"]
