# Verbaria Spring Boot server image.
#
# Multi-stage build: stage 1 compiles the executable JAR with Maven; stage 2
# is a slim JRE runtime. Build from the repo root:
#
#   docker build -t verbaria-server .
#
# At runtime, point VERBARIA_DB_HOST / VERBARIA_DB_USER / VERBARIA_DB_PASSWORD
# (and the rest of VERBARIA_DB_*) at a reachable Postgres. See README.md for
# an example `docker run`.

FROM docker.io/library/maven:3.9-eclipse-temurin-25 AS build
WORKDIR /src
COPY . .
RUN mvn --no-transfer-progress -B -DskipTests -Pproduction -pl server/zanata-server-spring -am package

FROM docker.io/library/eclipse-temurin:25-jre
WORKDIR /opt/verbaria
COPY --from=build /src/server/zanata-server-spring/target/zanata-server-spring-*.jar /opt/verbaria/verbaria-server.jar

ENV SPRING_PROFILES_ACTIVE=production
ENV VERBARIA_PORT=8080
ENV VERBARIA_TIMEZONE=UTC
ENV VERBARIA_DB_HOST=localhost
ENV VERBARIA_DB_PORT=5432
ENV VERBARIA_DB_NAME=verbaria
ENV VERBARIA_DB_USER=postgres
ENV VERBARIA_DB_PASSWORD=1234
ENV VERBARIA_DB_OPTIONS=""
ENV VERBARIA_ADMIN_LOGIN=""
ENV VERBARIA_ADMIN_PASSWORD=""
ENV JAVA_OPTS="-XX:+UseG1GC -XX:HeapDumpPath=/opt/verbaria"

EXPOSE ${VERBARIA_PORT}

ENTRYPOINT ["sh", "-c", "export TZ=$VERBARIA_TIMEZONE && exec java $JAVA_OPTS -Duser.timezone=$VERBARIA_TIMEZONE -jar /opt/verbaria/verbaria-server.jar"]
