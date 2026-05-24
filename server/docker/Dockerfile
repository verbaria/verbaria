# Zanata Spring Boot server image.
#
# Multi-stage build: stage 1 compiles the executable JAR with Maven; stage 2
# is a slim JRE runtime. Build from the repo root:
#
#   docker build -t zanata-server -f server/docker/Dockerfile .
#
# At runtime, point ZANATA_DB_URL / ZANATA_DB_USER / ZANATA_DB_PASSWORD at a
# reachable Postgres (see server/docker/docker-compose.yml).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY . .
RUN mvn -B -DskipTests -pl server/zanata-server-spring -am package

FROM eclipse-temurin:21-jre
WORKDIR /opt/zanata
COPY --from=build /src/server/zanata-server-spring/target/zanata-server-spring-*.jar /opt/zanata/zanata-server.jar
EXPOSE 8080
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/opt/zanata"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /opt/zanata/zanata-server.jar"]
