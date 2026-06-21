# Stage 1: Build the Maven application
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Maven wrapper configuration and dependencies info
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Fix line endings of mvnw (in case built on Windows) and make it executable
RUN tr -d '\r' < mvnw > mvnw_unix && mv mvnw_unix mvnw && chmod +x mvnw

# Download dependencies (cache layer)
RUN ./mvnw dependency:go-offline -B

# Copy source code and build package
COPY src ./src
RUN ./mvnw package -DskipTests -B

# Stage 2: Production runtime environment
FROM eclipse-temurin:25-jre
WORKDIR /app

# Install curl for docker healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Health check using the custom health endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
