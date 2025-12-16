# MyHive Backend

Spring Boot backend for the MyHive Travel Application.

## Project Structure

```
src/main/java/com/myhive/backend/
├── entity/              # JPA Entities
│   ├── Destination.java
│   ├── Activity.java
│   ├── Booking.java
│   └── BookingItem.java
├── dto/                 # Data Transfer Objects
│   ├── DestinationDTO.java
│   ├── ActivityDTO.java
│   ├── BookingDTO.java
│   ├── BookingItemDTO.java
│   └── CreateBookingRequest.java
├── repository/          # Spring Data JPA Repositories
│   ├── DestinationRepository.java
│   ├── ActivityRepository.java
│   ├── BookingRepository.java
│   └── BookingItemRepository.java
├── service/            # Business Logic Layer
│   ├── DestinationService.java
│   ├── ActivityService.java
│   └── BookingService.java
├── controller/         # REST API Controllers
│   ├── DestinationController.java
│   ├── ActivityController.java
│   └── BookingController.java
└── MyhiveBackendApplication.java
```

## Database Schema

### Tables
- **destinations** - Travel destinations with country, city, rating
- **activities** - Activities available at destinations with pricing
- **bookings** - Customer bookings with Stripe integration
- **booking_items** - Line items for each booking

### Key Features
- UUID primary keys for all tables
- Foreign key relationships with proper constraints
- Indexes on frequently queried columns
- Timestamp tracking for audit trails

## Setup Instructions

### 1. Start PostgreSQL Database
```bash
cd C:\Users\dijtb\IdeaProjects\myhive-travel-app\myhive-backend
docker-compose up -d
```

### 2. Verify Database
```bash
docker exec myhive-postgres psql -U myhive_user -d myhive_db -c "\dt"
```

### 3. Run the Application

**Option A: Using IntelliJ IDEA**
1. Open project in IntelliJ IDEA
2. Right-click `MyhiveBackendApplication.java`
3. Select "Run"

**Option B: Using Gradle**
```bash
./gradlew bootRun
```

### 4. Test the API
```bash
curl http://localhost:8080/api/destinations
```

See [API_TESTING.md](API_TESTING.md) for detailed endpoint documentation.

## Configuration

### Database Connection
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/myhive_db
spring.datasource.username=myhive_user
spring.datasource.password=myhive_password
```

### Server Port
```properties
server.port=8080
```

## API Endpoints

### Destinations
- `GET /api/destinations` - Get all destinations
- `GET /api/destinations/{id}` - Get destination by ID

### Activities
- `GET /api/activities` - Get all activities
- `GET /api/activities?destinationId={id}` - Filter by destination
- `GET /api/activities?category={category}` - Filter by category
- `GET /api/activities/{id}` - Get activity by ID

### Bookings
- `POST /api/bookings` - Create new booking
- `GET /api/bookings/{id}` - Get booking by ID
- `GET /api/bookings?email={email}` - Get bookings by email
- `PATCH /api/bookings/{id}/status` - Update booking status

## Technologies

- **Spring Boot 4.0.0** - Application framework
- **Spring Data JPA** - Database access
- **PostgreSQL 16** - Database
- **Lombok** - Reduce boilerplate code
- **Hibernate** - ORM
- **Docker** - Database containerization

## Sample Data

The database is initialized with:
- 5 destinations (Prague, Tenerife, Bali, Dubai, New York)
- 6 activities (4 for Tenerife, 2 for Prague)
- Categories: nightlife, adventure, daytime, culture

## Development Notes

- All entities use UUID as primary keys
- DTOs are used for API responses (no entity exposure)
- Services handle business logic and DTO conversion
- Repositories use Spring Data JPA query methods
- CORS enabled for all origins (development only)
- Transaction management with `@Transactional`

## Next Steps

1. Add Stripe payment integration
2. Implement email notifications
3. Add authentication/authorization
4. Create admin endpoints for CRUD operations
5. Add validation annotations
6. Implement exception handling
7. Add API documentation (Swagger/OpenAPI)
