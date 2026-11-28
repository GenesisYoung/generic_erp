# syntax=docker/dockerfile:1
# ---- build ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY .mvn .mvn
RUN mvn -B -q dependency:go-offline || true
COPY src src
RUN mvn -B -q -DskipTests package \
 && cp target/*.jar /app.jar

# ---- runtime -------------------------------------------------------------
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=docker \
    FILE_PATH=/data/files \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
RUN apt-get update && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd -r erp && useradd -r -g erp erp \
 && mkdir -p /app/logs /data/files && chown -R erp:erp /app /data
WORKDIR /app
COPY --from=build /app.jar /app/app.jar
USER erp
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fs http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
