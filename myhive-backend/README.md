# MyHive Backend

## Dev Docker (rebuild from scratch)

### Windows

Double-click or run from `myhive-backend/`:

```
dev-restart.bat
```

### Manual (step by step)

Run from `myhive-backend/`:

```bash
docker compose -f docker-compose.dev.yml down -v
```

```bash
docker compose -f docker-compose.dev.yml build --no-cache
```

```bash
docker compose -f docker-compose.dev.yml up -d
```

```bash
docker compose -f docker-compose.dev.yml logs -f
```

Backend will be available at http://localhost:8081
