# syntax=docker/dockerfile:1

# ---------- Stage 1: Build ----------
FROM gradle:8.14-jdk21-alpine AS builder
WORKDIR /app

# Copiar solo archivos de build primero -> reutiliza cache de dependencias
# mientras el código fuente cambie pero no las dependencias
COPY build.gradle* settings.gradle* gradle.properties* ./

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon || true

# Ahora el código fuente (invalida cache solo si esto cambia)
COPY src ./src

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle build -x test --no-daemon

# Copiar el jar ejecutable, excluyendo el "-plain.jar" que genera Spring Boot
RUN JAR=$(find build/libs -maxdepth 1 -name "*.jar" ! -name "*-plain.jar" | head -n1) && \
    test -n "$JAR" && cp "$JAR" app.jar

# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN apk update && apk upgrade --no-cache && \
    apk add --no-cache tini curl && \
    addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

COPY --from=builder --chown=spring:spring /app/app.jar app.jar

USER spring:spring

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+ExitOnOutOfMemoryError \
    -Djava.security.egd=file:/dev/./urandom"

# Health check via Spring Boot Actuator endpoint
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["tini", "--"]
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]