# MyHive Travel App - Quick Start Guide

## 🚀 Quick Start (5 minutes)

```bash
# 1. Start backend with database (development)
cd myhive-backend
docker-compose -f docker-compose.dev.yml up -d

# 2. Start frontend (new terminal)
cd myhive-react-app
npm install
npm start

# 3. Open browser
# Frontend: http://localhost:3000
# Backend API: http://localhost:8081/api
```

That's it! The app is running with:

- ✅ React frontend with trip builder
- ✅ Spring Boot API with PostgreSQL
- ✅ Sample destinations and activities
- ✅ Real images from Unsplash

## Prerequisites

- Java 25 JDK installed
- Docker and Docker Compose installed
- Node.js 18+ installed (for frontend)
- Git

## Project Structure

```
myhive-travel-app/
├── myhive-backend/          # Spring Boot API
├── myhive-react-app/        # React frontend
└── index.html              # Original HTML reference
```

## Quick Start (Development)

### 1. Start Backend (with Database)

```bash
cd myhive-backend
docker-compose -f docker-compose.dev.yml up -d
```

This starts:

- PostgreSQL database on port 5432
- Spring Boot API on port 8081 (mapped to container port 8080)

### 2. Start Frontend

```bash
cd myhive-react-app
npm install
npm start
```

Frontend runs on http://localhost:3000

### 3. Access the Application

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8081/api
- **API Documentation**:
    - Destinations: http://localhost:8081/api/destinations
    - Activities: http://localhost:8081/api/activities
    - Bookings: http://localhost:8081/api/bookings

## Environment Configurations

### Development Environment

```bash
cd myhive-backend
docker-compose -f docker-compose.dev.yml up -d
```

Features:

- Local PostgreSQL database
- Debug logging enabled
- CORS allows localhost:3000
- SQL queries visible in logs

### Production Environment

```bash
cd myhive-backend
# Set environment variables first
export DATABASE_URL=jdbc:postgresql://your-db-host:5432/myhive_prod_db
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_secure_password
export CORS_ALLOWED_ORIGINS=https://yourdomain.com

docker-compose -f docker-compose.prod.yml up -d
```

Features:

- External database connection
- Minimal logging
- Restricted CORS to your domain
- Resource limits and health checks

## Environment Variables

Create a `.env` file in `myhive-backend/`:

```bash
# Application Profile
SPRING_PROFILES_ACTIVE=dev

# Database Configuration
DATABASE_URL=jdbc:postgresql://localhost:5432/myhive_db
DB_USERNAME=myhive_user
DB_PASSWORD=your_secure_password_here

# CORS Configuration
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000

# Google Sheets (future use)
GOOGLE_SHEETS_CREDENTIALS_PATH=path/to/service-account.json
GOOGLE_SHEETS_SPREADSHEET_ID=your_spreadsheet_id_here
```

## Available Features

### Frontend (React)

- ✅ Destination browsing
- ✅ Activity filtering and selection
- ✅ Trip builder with state persistence
- ✅ Breadcrumb navigation
- ✅ Tab-based destination pages
- ✅ Coming soon modals for unavailable destinations
- ⚠️ Chat bot (temporarily hidden)

### Backend (Spring Boot)

- ✅ RESTful API for destinations, activities, bookings
- ✅ PostgreSQL database with sample data
- ✅ Input validation and error handling
- ✅ Rate limiting (100 requests/minute)
- ✅ Security headers
- ✅ CORS configuration
- ✅ Environment-specific configurations

## API Endpoints

### Destinations

```http
GET    /api/destinations           # List all destinations
GET    /api/destinations/{id}      # Get specific destination
```

### Activities

```http
GET    /api/activities            # List all activities
GET    /api/activities?destinationId={id}    # Activities by destination
GET    /api/activities?category={category}  # Activities by category
GET    /api/activities/{id}       # Get specific activity
```

### Bookings

```http
POST   /api/bookings               # Create booking
GET    /api/bookings/{id}          # Get booking by ID
GET    /api/bookings?email={email} # Get bookings by email
PATCH  /api/bookings/{id}/status  # Update booking status
```

## Development Workflow

### 1. Backend Development

```bash
cd myhive-backend
# Run tests
./gradlew test

# Build application
./gradlew build

# Run locally (requires external database)
./gradlew bootRun
```

### 2. Frontend Development

```bash
cd myhive-react-app
# Install dependencies
npm install

# Start development server
npm start

# Build for production
npm run build
```

### 3. Docker Development

```bash
# Build backend image
cd myhive-backend
docker build -t myhive-backend .

# Run with database
docker-compose -f docker-compose.dev.yml up -d

# View logs
docker-compose -f docker-compose.dev.yml logs -f backend

# Stop services
docker-compose -f docker-compose.dev.yml down
```

## Database Setup

The application uses PostgreSQL with sample data automatically loaded via `init.sql`:

- **Database**: myhive_db
- **User**: myhive_user
- **Password**: myhive_password (development)

Sample data includes:

- 5 destinations (Prague, Tenerife, Bali, Dubai, New York)
- Activities for Tenerife and Prague
- Real Unsplash images for all destinations and activities

## Security Features

- **Rate Limiting**: 100 requests per minute per IP
- **Input Validation**: Email format, required fields, size limits
- **Security Headers**: XSS protection, content type options, CSP
- **CORS**: Restricted to allowed origins
- **Environment Variables**: Sensitive data not in code

## Troubleshooting

### Backend Issues

```bash
# Check container status
docker-compose -f docker-compose.dev.yml ps

# View logs
docker-compose -f docker-compose.dev.yml logs backend

# Restart services
docker-compose -f docker-compose.dev.yml restart backend

# Rebuild and restart
docker-compose -f docker-compose.dev.yml down -v && docker-compose -f docker-compose.dev.yml up -d
```

### Frontend Issues

```bash
# Clear node modules
rm -rf node_modules package-lock.json
npm install

# Check if backend is running
curl http://localhost:8081/api/destinations
```

### Database Issues

```bash
# Check PostgreSQL container
docker exec myhive-postgres-dev pg_isready -U myhive_user -d myhive_db

# Reset database
docker-compose -f docker-compose.dev.yml down -v && docker-compose -f docker-compose.dev.yml up -d
```

## Production Deployment

### Using Docker Compose (Production)

```bash
# Set production environment variables
export SPRING_PROFILES_ACTIVE=prod
export DATABASE_URL=jdbc:postgresql://your-db-host:5432/myhive_prod_db
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_secure_password
export CORS_ALLOWED_ORIGINS=https://yourdomain.com

# Deploy
docker-compose -f docker-compose.prod.yml up -d
```

### Environment Setup

1. Copy `.env.example` to `.env`
2. Update with your values
3. Set `SPRING_PROFILES_ACTIVE=prod` for production
4. Configure external database
5. Update CORS origins to your domain

## Support

For issues:

1. Check Docker containers are running: `docker-compose -f docker-compose.dev.yml ps`
2. Verify backend health: `curl http://localhost:8081/api/destinations`
3. Check logs: `docker-compose -f docker-compose.dev.yml logs backend`
4. Ensure environment variables are set correctly

## Next Steps

- Configure Google Sheets integration for trip exports
- Set up production database
- Configure domain and HTTPS
- Set up monitoring and logging
- Implement payment processing (Stripe integration exists)
