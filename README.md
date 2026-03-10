# MyHive Travel App

A full-stack travel booking platform with a React frontend and Spring Boot backend.

## Project Structure

```
myhive-travel-app/
├── myhive-backend/        # Spring Boot 4.0 API (Java 25)
├── myhive-react-app/      # React frontend
├── index.html             # Original static prototype
└── README.md
```

## Tech Stack

| Layer               | Technology                                        |
|---------------------|---------------------------------------------------|
| **Frontend**        | React, Context API                                |
| **Backend**         | Spring Boot 4.0, Spring Security, Spring Data JPA |
| **Auth**            | JWT (jjwt 0.13) with BCrypt passwords             |
| **Database (dev)**  | H2 in-memory (PostgreSQL compatibility mode)      |
| **Database (prod)** | PostgreSQL 16                                     |
| **Build**           | Gradle 9.3, Java 25                               |
| **Container**       | Docker (multi-stage, Eclipse Temurin Alpine)      |
| **Integrations**    | Google Sheets (prod only), Email (SMTP)           |

## Quick Start (Development)

### Prerequisites

- **Java 25** JDK
- **Node.js 18+**
- **Docker** (optional, for containerized run)

### Option A: Run with Gradle (no Docker needed)

```bash
# Backend
cd myhive-backend
./gradlew bootRun --args='--spring.profiles.active=dev'

# Frontend (new terminal)
cd myhive-react-app
npm install && npm start
```

### Option B: Run with Docker

```bash
# Build and run backend
cd myhive-backend
docker build --build-arg SPRING_PROFILE=dev -t myhive-backend:dev .
docker run -d --name myhive-backend-dev -p 8080:8080 myhive-backend:dev

# Frontend (new terminal)
cd myhive-react-app
npm install && npm start
```

### Verify

| URL                                   | Description                    |
|---------------------------------------|--------------------------------|
| http://localhost:3000                 | React frontend                 |
| http://localhost:8080                 | Backend API root               |
| http://localhost:8080/destinations    | All destinations               |
| http://localhost:8080/actuator/health | Health check                   |
| http://localhost:8080/h2-console      | H2 database browser (dev only) |

H2 console credentials: JDBC URL `jdbc:h2:mem:devdb`, user `sa`, no password.

## API Endpoints

### Public Endpoints

```
GET  /                          # Service info
GET  /destinations              # All destinations
GET  /destinations/{id}         # Destination by ID
GET  /activities                # All activities
GET  /activities?destinationId= # Activities by destination
GET  /activities?category=      # Activities by category
GET  /activities/{id}           # Activity by ID
POST /bookings                  # Create booking
GET  /bookings/{id}             # Booking by ID
GET  /bookings?email=           # Bookings by email
PATCH /bookings/{id}/status     # Update booking status
GET  /google-sheets/status      # Google Sheets integration status
GET  /actuator/health           # Health check
```

### Authentication

```
POST /auth/login                # Login, returns JWT token
GET  /auth/validate             # Validate JWT token
```

**Dev credentials:** `admin@myhive.com` / `admin123`

```bash
# Login
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@myhive.com","password":"admin123"}'

# Use token
curl http://localhost:8080/api/admin/bookings \
  -H "Authorization: Bearer <token>"
```

### Protected Admin Endpoints (JWT + ADMIN role required)

```
GET  /api/admin/bookings        # All bookings
GET  /api/admin/bookings/stats  # Booking statistics
```

## Environment Profiles

### `dev` (default)

- H2 in-memory database (auto-created, reset on restart)
- Sample data loaded from `data.sql` (5 destinations, 8 activities, 3 bookings)
- Debug logging, SQL query logging
- CORS: `localhost:3000`
- Google Sheets: disabled
- Email: disabled

### `prod`

- PostgreSQL (external, configured via env vars)
- Minimal logging
- CORS: restricted to production domain
- Google Sheets: optional (enabled via env vars)
- Email: configurable via SMTP env vars

### `test`

- H2 in-memory with `create-drop` schema
- Used for automated tests

## Environment Variables (Production)

```bash
# Required
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://host:5432/myhive_db
DB_USERNAME=your_user
DB_PASSWORD=your_password
PORT=8080
JWT_SECRET=your-secret-key
ADMIN_EMAIL=admin@yourdomain.com
ADMIN_PASSWORD=your-secure-password
CORS_ALLOWED_ORIGINS=https://yourdomain.com

# Optional: Google Sheets
GOOGLE_SHEETS_ENABLED=true
GOOGLE_SHEETS_CREDENTIALS_JSON={"type":"service_account",...}
GOOGLE_SHEETS_SPREADSHEET_ID=your-spreadsheet-id

# Optional: Email
EMAIL_USERNAME=your-email@gmail.com
EMAIL_PASSWORD=your-app-password
```

## Security

- **JWT Authentication** with 24-hour token expiration
- **BCrypt** password hashing
- **Role-based access control** (ADMIN role for `/api/admin/**`)
- **Rate limiting** at 100 requests/minute per IP
- **Security headers**: `X-Content-Type-Options`, `X-Frame-Options`, `X-XSS-Protection`, `Referrer-Policy`,
  `Content-Security-Policy`
- **CORS** restricted to configured origins
- **CSRF** disabled (stateless API)
- **Stateless sessions** (no server-side session state)

## Docker

### Build

```bash
cd myhive-backend

# Dev build (H2, sample data)
docker build --build-arg SPRING_PROFILE=dev -t myhive-backend:dev .

# Prod build (requires env vars at runtime)
docker build -t myhive-backend:prod .
```

### Run

```bash
# Dev
docker run -d --name myhive-backend-dev -p 8080:8080 myhive-backend:dev

# Prod
docker run -d --name myhive-backend \
  -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/db \
  -e DB_USERNAME=user \
  -e DB_PASSWORD=pass \
  -e JWT_SECRET=secret \
  -e ADMIN_EMAIL=admin@example.com \
  -e ADMIN_PASSWORD=password \
  myhive-backend:prod
```

## Development

### Backend

```bash
cd myhive-backend
./gradlew test              # Run tests
./gradlew build             # Build JAR
./gradlew bootRun           # Run locally (dev profile)
```

### Frontend

```bash
cd myhive-react-app
npm install                 # Install dependencies
npm start                   # Dev server on :3000
npm run build               # Production build
```

## Sample Data (dev profile)

Loaded automatically on startup:

- **5 destinations**: Prague, Tenerife, Bali, Dubai, New York
- **8 activities** across destinations (nightlife, adventure, daytime, culture)
- **3 bookings** with different statuses (PAID, CONFIRMED, PENDING)

## Troubleshooting

**Port in use:** Change `server.port` in `application-dev.properties` or use `-p <port>:8080` with Docker.

**JWT issues:** Tokens expire after 24 hours. Verify header format: `Authorization: Bearer <token>`.

**Docker logs:**
```bash
docker logs myhive-backend-dev
```

**Frontend can't reach backend:** Ensure backend is on `:8080` and `REACT_APP_API_URL` in `myhive-react-app/.env` is
correct.
