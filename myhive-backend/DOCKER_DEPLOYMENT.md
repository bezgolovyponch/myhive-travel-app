# MyHive Backend - Docker Deployment Guide

## Overview

The MyHive backend is now fully containerized and can run in Docker alongside PostgreSQL.

## Architecture

```
┌─────────────────────────────────────────┐
│         Docker Network (bridge)         │
│                                         │
│  ┌──────────────┐    ┌──────────────┐  │
│  │  PostgreSQL  │    │   Backend    │  │
│  │   (port      │◄───┤  Spring Boot │  │
│  │    5432)     │    │  (port 8080) │  │
│  └──────────────┘    └──────────────┘  │
│         │                     │         │
└─────────┼─────────────────────┼─────────┘
          │                     │
    Host:5432              Host:8081
```

## Files Created

### 1. Dockerfile
Multi-stage build for optimized image size:
- **Stage 1 (build)**: Compiles the Spring Boot application using Gradle 8.14 and Java 21
- **Stage 2 (runtime)**: Runs the application using minimal JRE image

### 2. docker-compose.yml
Orchestrates both services:
- **postgres**: PostgreSQL 16 Alpine with health checks
- **backend**: Spring Boot application with dependency on healthy postgres

### 3. .dockerignore
Excludes unnecessary files from Docker build context

## Quick Start

### Start All Services
```bash
cd C:\Users\dijtb\IdeaProjects\myhive-travel-app\myhive-backend
docker-compose up -d
```

### Check Status
```bash
docker-compose ps
```

Expected output:
```
NAME              IMAGE                    STATUS                   PORTS
myhive-backend    myhive-backend-backend   Up (healthy)            0.0.0.0:8081->8080/tcp
myhive-postgres   postgres:16-alpine       Up (healthy)            0.0.0.0:5432->5432/tcp
```

### View Logs
```bash
# All services
docker-compose logs -f

# Backend only
docker logs -f myhive-backend

# PostgreSQL only
docker logs -f myhive-postgres
```

### Stop Services
```bash
docker-compose down
```

### Stop and Remove Volumes (Fresh Start)
```bash
docker-compose down -v
```

## Port Configuration

| Service    | Container Port | Host Port | URL                          |
|------------|----------------|-----------|------------------------------|
| PostgreSQL | 5432           | 5432      | localhost:5432               |
| Backend    | 8080           | 8081      | http://localhost:8081        |

**Note**: Backend runs on port 8081 on the host to avoid conflicts with local development instances.

## API Endpoints

### Test Destinations API
```bash
# PowerShell
Invoke-WebRequest -Uri http://localhost:8081/api/destinations -UseBasicParsing

# Or in browser
http://localhost:8081/api/destinations
```

### Test Activities API
```bash
Invoke-WebRequest -Uri "http://localhost:8081/api/activities?destinationId=b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22" -UseBasicParsing
```

### All Available Endpoints
- `GET http://localhost:8081/api/destinations`
- `GET http://localhost:8081/api/destinations/{id}`
- `GET http://localhost:8081/api/activities`
- `GET http://localhost:8081/api/activities?destinationId={id}`
- `GET http://localhost:8081/api/activities?category={category}`
- `GET http://localhost:8081/api/activities/{id}`
- `POST http://localhost:8081/api/bookings`
- `GET http://localhost:8081/api/bookings/{id}`
- `GET http://localhost:8081/api/bookings?email={email}`
- `PATCH http://localhost:8081/api/bookings/{id}/status`

## Environment Variables

The backend container uses these environment variables:

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/myhive_db
SPRING_DATASOURCE_USERNAME: myhive_user
SPRING_DATASOURCE_PASSWORD: myhive_password
SPRING_JPA_HIBERNATE_DDL_AUTO: validate
SERVER_PORT: 8080
```

## Rebuild After Code Changes

```bash
# Rebuild backend image
docker-compose build backend

# Restart with new image
docker-compose up -d backend
```

## Database Access

### Connect to PostgreSQL
```bash
docker exec -it myhive-postgres psql -U myhive_user -d myhive_db
```

### Run SQL Queries
```bash
docker exec myhive-postgres psql -U myhive_user -d myhive_db -c "SELECT * FROM destinations;"
```

## Troubleshooting

### Backend Won't Start
```bash
# Check logs
docker logs myhive-backend

# Common issues:
# 1. PostgreSQL not healthy - wait for health check
# 2. Port 8081 in use - change port in docker-compose.yml
```

### Database Connection Failed
```bash
# Verify PostgreSQL is running
docker exec myhive-postgres pg_isready -U myhive_user -d myhive_db

# Should output: accepting connections
```

### Port Already in Use
If port 8081 is in use, edit `docker-compose.yml`:
```yaml
ports:
  - "8082:8080"  # Change 8081 to any available port
```

### View Container Health
```bash
docker inspect myhive-backend | findstr Health
docker inspect myhive-postgres | findstr Health
```

## Development Workflow

### Option 1: Docker (Recommended for Production-like Testing)
```bash
docker-compose up -d
# API available at http://localhost:8081
```

### Option 2: Local (Recommended for Development)
```bash
# Start only PostgreSQL
docker-compose up -d postgres

# Run backend from IntelliJ or Gradle
./gradlew bootRun
# API available at http://localhost:8080
```

## Build Details

### Image Sizes
- **Build stage**: ~500MB (includes Gradle, JDK)
- **Runtime image**: ~200MB (JRE only)

### Build Time
- First build: ~2-3 minutes (downloads dependencies)
- Subsequent builds: ~30 seconds (cached layers)

### Java Version
- **Build**: Java 21 JDK
- **Runtime**: Java 21 JRE (Alpine)

### Gradle Version
- Gradle 8.14 (required for Spring Boot 4.0.0)

## Production Considerations

For production deployment, consider:

1. **Environment Variables**: Use secrets management
2. **Health Checks**: Already configured in Dockerfile
3. **Resource Limits**: Add to docker-compose.yml
4. **Logging**: Configure centralized logging
5. **Monitoring**: Add Prometheus/Grafana
6. **SSL/TLS**: Add reverse proxy (nginx)
7. **Database**: Use managed PostgreSQL service

## Next Steps

1. ✅ Backend running in Docker
2. ✅ PostgreSQL with sample data
3. ✅ API endpoints tested
4. 🔲 Add Stripe payment integration
5. 🔲 Connect React frontend to Docker backend
6. 🔲 Add CI/CD pipeline
7. 🔲 Deploy to cloud (AWS/GCP/Azure)
