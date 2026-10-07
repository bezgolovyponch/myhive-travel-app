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
#
# JVM sizing for the 512 MiB starter instance (1 vCPU => the JVM picks SerialGC), measured with
# Native Memory Tracking on 2026-10-07: outside the heap the process needs ~240 MiB (metaspace +
# class space ~127, code cache ~58 after JIT warm-up, symbols + CDS ~40, threads/arenas ~15) and
# that figure keeps creeping up for days as more code is compiled. The AI planner stack (Spring AI,
# OpenAI SDK, langgraph4j, Netty) added ~30 MiB of it. With MaxRAMPercentage=55 (282 MiB heap) the
# total reached ~520 MiB whenever the heap filled up to its max, and the CONTAINER was OOM-killed
# (2026-10-02, 2026-10-06 x5) without a single line in the logs. 25 % (128 MiB) was too small for
# image work, so:
#   40 %  => 205 MiB heap, ~65 MiB of headroom for the page cache and JIT growth.
#   MaxMetaspaceSize / ReservedCodeCacheSize cap the two areas that grow with uptime, so unbounded
#   growth becomes a logged java.lang.OutOfMemoryError (metaspace) or a code-cache flush instead of
#   a silent SIGKILL. With every cap hit the budget is 205 + 160 + 80 + ~40 (symbols, CDS) + thread
#   stacks; the ~15 MiB of stacks measured idle is the one figure that still grows with load (Tomcat
#   allows 200 request threads), so the "threads" count in the memory line is the one to watch.
# No ExitOnOutOfMemoryError on purpose: a heap OOME on one request thread is a 500 today and the
# JVM carries on, whereas exiting would drop the in-memory email queue with every such request.
# MemoryUsageLogger prints the live heap / metaspace / code-cache split every 10 minutes.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=40.0 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=80m -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE}"]
