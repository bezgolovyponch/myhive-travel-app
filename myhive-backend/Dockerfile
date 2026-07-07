FROM gradle:9.3.1-jdk25-alpine AS build
WORKDIR /app

COPY build.gradle settings.gradle ./

# Download dependencies (cached separately from source code)
RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    gradle dependencies --no-daemon

COPY src ./src

RUN --mount=type=cache,target=/home/gradle/.gradle/caches \
    gradle bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# Build argument for Spring profile (default to prod)
ARG SPRING_PROFILE=prod
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILE}

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/actuator/health || exit 1

# Run the application with the specified profile.
# MaxRAMPercentage=70: the default (25% => ~128MB heap on the 512MB instance) is too small for
# image decoding — a single 20MP photo needs ~80MB of raster. 70% leaves ~150MB for metaspace,
# thread stacks and other native memory.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=70.0 -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
