@echo off
echo Stopping containers and removing volumes...
docker compose -f docker-compose.dev.yml down -v
docker rm -f myhive-backend-dev myhive-postgres-dev 2>nul

echo Building images...
docker compose -f docker-compose.dev.yml build --no-cache

echo Starting containers...
docker compose -f docker-compose.dev.yml up -d

echo.
echo Done! Backend running on http://localhost:8080
echo.
docker compose -f docker-compose.dev.yml logs -f
