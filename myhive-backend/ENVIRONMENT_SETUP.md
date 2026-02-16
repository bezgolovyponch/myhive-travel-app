# Environment Configuration Guide

## Development Environment

### Local Development

```bash
# Uses .env file (defaults to dev profile)
docker-compose -f docker-compose.dev.yml up -d
```

### Environment Variables

- `SPRING_PROFILES_ACTIVE=dev`
- `DB_PASSWORD=myhive_password`
- `CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000`

### Features

- SQL logging enabled
- Debug logging
- Local database
- Permissive CORS for frontend development

## Production Environment

### Production Deployment

```bash
# Uses environment variables and prod profile
docker-compose -f docker-compose.prod.yml up -d
```

### Required Environment Variables

- `SPRING_PROFILES_ACTIVE=prod`
- `DATABASE_URL=jdbc:postgresql://your-db-host:5432/myhive_prod_db`
- `DB_USERNAME=your_db_user`
- `DB_PASSWORD=your_secure_password`
- `CORS_ALLOWED_ORIGINS=https://yourdomain.com`

### Features

- SQL logging disabled
- Minimal logging
- External database
- Restricted CORS to domain
- Resource limits
- Health checks
- No database container (assumes external DB)

## Configuration Files

### application.properties

- Base configuration shared across environments
- Sets default profile to `dev`

### application-dev.properties

- Development-specific overrides
- Local database connection
- Debug logging enabled
- Localhost CORS

### application-prod.properties

- Production-specific overrides
- Environment variable driven
- Minimal logging
- Production CORS
- Google Sheets configuration

### Environment Files

- `.env` - Development variables
- `.env.prod` - Production template
- `.env.example` - Documentation template

## Switching Environments

### Method 1: Environment Variable

```bash
export SPRING_PROFILES_ACTIVE=prod
docker-compose up -d
```

### Method 2: Docker Compose Override

```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### Method 3: .env File

```bash
# Copy production template
cp .env.prod .env
# Edit .env with production values
docker-compose up -d
```
