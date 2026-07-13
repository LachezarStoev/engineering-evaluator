FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
RUN useradd -r -u 10001 evaluator
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER evaluator
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
