# Multi-stage build for Spring Boot application

# Stage 1: Build the application
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Install Gradle
RUN apk add --no-cache wget unzip && \
    wget https://services.gradle.org/distributions/gradle-8.14-bin.zip && \
    unzip gradle-8.14-bin.zip && \
    mv gradle-8.14 /opt/gradle && \
    rm gradle-8.14-bin.zip

ENV GRADLE_HOME=/opt/gradle
ENV PATH=$PATH:$GRADLE_HOME/bin

# Copy gradle files
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
COPY gradlew.bat ./

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code
COPY src ./src

# Build the application
RUN gradle bootJar --no-daemon

# Stage 2: Run the application
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
