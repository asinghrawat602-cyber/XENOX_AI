FROM eclipse-temurin:27-jdk AS build

WORKDIR /app

RUN apt-get update && apt-get install -y maven && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:27-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 1313

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-1313} -jar app.jar"]
COPY index.html ./src/main/resources/static/index.html
