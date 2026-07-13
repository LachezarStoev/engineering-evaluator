FROM maven:3.9.9-eclipse-temurin-21 AS build
ENV MAVEN_OPTS="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC"
WORKDIR /src
COPY pom.xml .
COPY src src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
ENV JAVA_TOOL_OPTIONS="-XX:TieredStopAtLevel=1 -XX:+UseSerialGC"
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 10001 evaluator
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER evaluator
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=20 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-jar","app.jar"]
