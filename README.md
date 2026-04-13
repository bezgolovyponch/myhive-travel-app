# Trivlu Travel

Travel booking platform — Spring Boot 4.0 backend + React 19 frontend.

## Quick Start

```bash
# Backend (port 8080)
cd myhive-backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend (port 3000)
cd myhive-react-app
npm install && npm start
```

Dev uses H2 in-memory database with sample data (5 destinations, activities, bookings).

## Project Structure

```
myhive-backend/          Spring Boot 4.0, Java 25, Gradle 9.3
myhive-react-app/        React 19, CRA, BrowserRouter, Bootstrap 5
```

### Backend Packages

| Package       | Purpose                                                                        |
|---------------|--------------------------------------------------------------------------------|
| `controller/` | REST endpoints (Destination, Activity, Booking, Blog, Contact, Admin)          |
| `service/`    | Business logic + EmailService                                                  |
| `entity/`     | JPA entities (UUID PKs): Destination, Activity, Booking, BookingItem, BlogPost |
| `dto/`        | Request/response objects                                                       |
| `config/`     | Security (Auth0 OIDC), CORS, rate limiting, R2, email templates                |

### API Endpoints

**Public** (no auth):

- `GET /destinations`, `GET /destinations/{id}`
- `GET /activities`, `GET /activities/{id}`, `GET /activities/paged`
- `GET /blog`, `GET /blog/{id}`
- `POST /bookings`, `POST /bookings/trip`, `GET /bookings/{id}`, `GET /bookings?email=`
- `POST /contact`

**Admin** (Auth0 JWT, ADMIN/MANAGER role):

- `/admin/bookings/**`, `/admin/destinations/**`, `/admin/activities/**`, `/admin/blog/**`, `/admin/upload`

## Services

| Service | Provider      | Purpose                              |
|---------|---------------|--------------------------------------|
| Hosting | Render.com    | Backend + frontend + PostgreSQL 16   |
| CDN/DNS | Cloudflare    | Proxy, DDoS protection, caching, SSL |
| Email   | SendGrid      | SMTP relay (noreply@trivlu.com)      |
| Images  | Cloudflare R2 | S3-compatible object storage         |
| Auth    | Auth0         | OIDC/OAuth2, roles: ADMIN, MANAGER   |
| Domain  | Namecheap     | Registrar + email forwarding         |

## Environment Variables

| Variable                 | Required    | Default                  |
|--------------------------|-------------|--------------------------|
| `SPRING_PROFILES_ACTIVE` | yes         | `dev`                    |
| `DATABASE_URL`           | prod        | H2 in dev                |
| `SENDGRID_API_KEY`       | for email   | -                        |
| `EMAIL_FROM`             | for email   | `noreply@trivlu.com`     |
| `EMAIL_CONTACT_TO`       | for email   | `info@trivlu.com`        |
| `EMAIL_ENABLED`          | no          | `false`                  |
| `CORS_ALLOWED_ORIGINS`   | yes         | `http://localhost:3000`  |
| `R2_ACCESS_KEY`          | for uploads | -                        |
| `R2_SECRET_KEY`          | for uploads | -                        |
| `R2_BUCKET_NAME`         | for uploads | -                        |
| `R2_ENDPOINT`            | for uploads | -                        |
| `R2_PUBLIC_URL`          | for uploads | -                        |
| `AUTH0_ISSUER_URI`       | yes         | -                        |
| `AUTH0_AUDIENCE`         | yes         | `https://api.trivlu.com` |
| `REACT_APP_API_URL`      | frontend    | `http://localhost:8080`  |

## Testing

```bash
cd myhive-backend
./gradlew test                                    # all tests
./gradlew test --tests '*ContactControllerTest'   # single class
```

Tests use JUnit 5 + Spring Boot Test + H2. Auth0 JwtDecoder is mocked via `TestSecurityConfig`.

## Docker

```bash
# Dev
cd myhive-backend
docker build --build-arg SPRING_PROFILE=dev -t myhive-backend:dev .
docker run -d -p 8080:8080 myhive-backend:dev

# Prod (used by Render.com)
docker build -t myhive-backend:prod .
docker run -d -p 8080:8080 --env-file .env myhive-backend:prod
```

Dev docker-compose available: `docker-compose -f docker-compose.dev.yml up`

## Security

- Auth0 OIDC (OAuth2 Resource Server), stateless sessions
- Rate limiting: 100 req/min per IP
- Security headers: CSP, X-Frame-Options, X-Content-Type-Options
- CORS restricted to configured origins
- `/api` context path in prod
