# Quick Start Commands

## Start Everything (Development)

```bash
# 1. Start backend with database
cd myhive-backend
docker-compose -f docker-compose.dev.yml up -d

# 2. Start frontend (new terminal)
cd myhive-react-app
npm install
npm start
```

## Access Points

- Frontend: http://localhost:3000
- Backend API: http://localhost:8081/api
- Destinations API: http://localhost:8081/api/destinations

## Stop Everything

```bash
# Stop backend services
cd myhive-backend
docker-compose -f docker-compose.dev.yml down

# Stop frontend (Ctrl+C in terminal)
```

## Check Status

```bash
# Check backend containers
cd myhive-backend
docker-compose -f docker-compose.dev.yml ps

# Test API
curl http://localhost:8081/api/destinations
```

## Environment Switching

```bash
# Development (default)
docker-compose -f docker-compose.dev.yml up -d

# Development with explicit config
docker-compose -f docker-compose.dev.yml up -d

# Production (requires env vars)
docker-compose -f docker-compose.prod.yml up -d
```
