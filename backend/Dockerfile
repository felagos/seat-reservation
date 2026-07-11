# Stage 1: Build
FROM gradle:8.14-jdk21-alpine AS builder
WORKDIR /app

COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]