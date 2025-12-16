# MyHive Backend API Testing Guide

## Prerequisites
- PostgreSQL running in Docker (port 5432)
- Spring Boot application running (port 8080)

## Start the Application

### Using IntelliJ IDEA
1. Open the project in IntelliJ IDEA
2. Right-click on `MyhiveBackendApplication.java`
3. Select "Run 'MyhiveBackendApplication'"

### Using Gradle (if JAVA_HOME is set)
```bash
cd C:\Users\dijtb\IdeaProjects\myhive-travel-app\myhive-backend
./gradlew bootRun
```

## API Endpoints

### 1. Destinations API

#### Get All Destinations
```bash
curl http://localhost:8080/api/destinations
```

**Expected Response:**
```json
[
  {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "name": "Prague",
    "description": "The City of a Hundred Spires",
    "country": "Czech Republic",
    "city": "Prague",
    "imageUrl": "images/prague.jpg",
    "rating": 4.75
  },
  {
    "id": "b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22",
    "name": "Tenerife",
    "description": "Volcanic adventures and beach parties",
    "country": "Spain",
    "city": "Santa Cruz de Tenerife",
    "imageUrl": "images/tenerife.jpg",
    "rating": 4.60
  }
]
```

#### Get Destination by ID
```bash
curl http://localhost:8080/api/destinations/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11
```

### 2. Activities API

#### Get All Activities
```bash
curl http://localhost:8080/api/activities
```

#### Get Activities by Destination
```bash
curl "http://localhost:8080/api/activities?destinationId=b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22"
```

#### Get Activities by Category
```bash
curl "http://localhost:8080/api/activities?category=adventure"
```

#### Get Activities by Destination and Category
```bash
curl "http://localhost:8080/api/activities?destinationId=b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22&category=nightlife"
```

#### Get Activity by ID
```bash
curl http://localhost:8080/api/activities/f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66
```

**Expected Response:**
```json
{
  "id": "f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66",
  "destinationId": "b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22",
  "destinationName": "Tenerife",
  "name": "Sunset Boat Party",
  "description": "Dance the night away on a catamaran.",
  "price": 50.00,
  "duration": 180,
  "category": "nightlife",
  "imageUrl": "images/tenerife-boat-party.jpg"
}
```

### 3. Bookings API

#### Create a Booking
```bash
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{
    "userEmail": "test@example.com",
    "activities": [
      {
        "activityId": "f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66",
        "quantity": 2
      },
      {
        "activityId": "f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77",
        "quantity": 1
      }
    ]
  }'
```

**Expected Response:**
```json
{
  "id": "generated-uuid",
  "userEmail": "test@example.com",
  "stripeSessionId": null,
  "totalAmount": 145.00,
  "status": "PENDING",
  "createdAt": "2025-12-16T16:30:00",
  "paidAt": null,
  "items": [
    {
      "id": "generated-uuid",
      "activityId": "f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66",
      "activityName": "Sunset Boat Party",
      "destinationName": "Tenerife",
      "price": 50.00,
      "quantity": 2
    },
    {
      "id": "generated-uuid",
      "activityId": "f6eebc99-9c0b-4ef8-bb6d-6bb9bd380a77",
      "activityName": "Teide National Park Tour",
      "destinationName": "Tenerife",
      "price": 45.00,
      "quantity": 1
    }
  ]
}
```

#### Get Booking by ID
```bash
curl http://localhost:8080/api/bookings/{booking-id}
```

#### Get Bookings by Email
```bash
curl "http://localhost:8080/api/bookings?email=test@example.com"
```

#### Update Booking Status
```bash
curl -X PATCH http://localhost:8080/api/bookings/{booking-id}/status \
  -H "Content-Type: application/json" \
  -d '{
    "status": "PAID",
    "stripeSessionId": "cs_test_123456"
  }'
```

## Testing with Postman

Import the following collection:

### Destinations
- **GET** `http://localhost:8080/api/destinations`
- **GET** `http://localhost:8080/api/destinations/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`

### Activities
- **GET** `http://localhost:8080/api/activities`
- **GET** `http://localhost:8080/api/activities?destinationId=b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22`
- **GET** `http://localhost:8080/api/activities?category=adventure`
- **GET** `http://localhost:8080/api/activities/f5eebc99-9c0b-4ef8-bb6d-6bb9bd380a66`

### Bookings
- **POST** `http://localhost:8080/api/bookings` (with JSON body)
- **GET** `http://localhost:8080/api/bookings?email=test@example.com`
- **PATCH** `http://localhost:8080/api/bookings/{id}/status` (with JSON body)

## Database Verification

Check the database after creating bookings:

```bash
docker exec myhive-postgres psql -U myhive_user -d myhive_db -c "SELECT * FROM bookings;"
docker exec myhive-postgres psql -U myhive_user -d myhive_db -c "SELECT * FROM booking_items;"
```

## Common Issues

### Port Already in Use
If port 8080 is already in use, change it in `application.properties`:
```properties
server.port=8081
```

### Database Connection Failed
Ensure PostgreSQL is running:
```bash
docker ps | grep myhive-postgres
```

If not running:
```bash
cd C:\Users\dijtb\IdeaProjects\myhive-travel-app\myhive-backend
docker-compose up -d
```

### CORS Issues
CORS is enabled with `@CrossOrigin(origins = "*")` on all controllers for development.
For production, configure specific origins in a WebConfig class.
