# Frontend-Backend Integration Guide

## Overview

The React frontend is now fully integrated with the Spring Boot backend API running on port 8081.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   React Frontend                        │
│                  (localhost:3000)                       │
│                                                         │
│  ┌──────────────┐      ┌──────────────┐               │
│  │  AppContext  │◄─────┤  API Service │               │
│  │  (State Mgmt)│      │  (api.js)    │               │
│  └──────┬───────┘      └──────┬───────┘               │
│         │                     │                         │
│         ▼                     │                         │
│  ┌──────────────┐             │                         │
│  │  Components  │             │                         │
│  │  - HomePage  │             │                         │
│  │  - DestPage  │             │                         │
│  │  - Cards     │             │                         │
│  └──────────────┘             │                         │
└────────────────────────────────┼─────────────────────────┘
                                │
                                │ HTTP Requests
                                │
┌────────────────────────────────▼─────────────────────────┐
│              Spring Boot Backend                         │
│               (localhost:8081)                           │
│                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────┐  │
│  │ Controllers  │───►│   Services   │───►│  Repos   │  │
│  └──────────────┘    └──────────────┘    └────┬─────┘  │
│                                                │         │
└────────────────────────────────────────────────┼─────────┘
                                                │
                                                ▼
                                        ┌──────────────┐
                                        │  PostgreSQL  │
                                        │  (port 5432) │
                                        └──────────────┘
```

## Files Created/Modified

### New Files
1. **`src/services/api.js`** - API service layer with all backend endpoints
2. **`.env`** - Environment configuration for API URL

### Modified Files
1. **`src/context/AppContext.js`** - Fetch data from API on mount
2. **`src/pages/HomePage.js`** - Add loading/error states
3. **`src/pages/DestinationPage.js`** - Fetch destination-specific data
4. **`src/components/DestinationCard.js`** - Map backend data structure
5. **`src/components/ActivityCard.js`** - Handle backend field names

## API Service (`src/services/api.js`)

### Configuration
```javascript
const API_BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8081/api';
```

### Available Methods

#### Destinations
- `api.getDestinations()` - Get all destinations
- `api.getDestination(id)` - Get single destination by UUID

#### Activities
- `api.getActivities(destinationId?, category?)` - Get activities with optional filters
- `api.getActivity(id)` - Get single activity by UUID

#### Bookings
- `api.createBooking(bookingData)` - Create new booking
- `api.getBooking(id)` - Get booking by UUID
- `api.getBookingsByEmail(email)` - Get user's bookings
- `api.updateBookingStatus(id, status, stripeSessionId?)` - Update booking status

## Data Mapping

### Backend → Frontend Field Mapping

#### Destinations
| Backend Field | Frontend Usage | Notes |
|--------------|----------------|-------|
| `id` (UUID) | `id` | Used for routing |
| `name` | `name` | Display name |
| `description` | `description` | Card description |
| `country` | - | Not displayed yet |
| `city` | - | Not displayed yet |
| `imageUrl` | `image` | Falls back to Unsplash |
| `rating` | `badge` | ≥4.7 = "Popular" |

#### Activities
| Backend Field | Frontend Usage | Notes |
|--------------|----------------|-------|
| `id` (UUID) | `id` | Unique identifier |
| `name` | `title` | Activity name |
| `description` | `description` | Activity description |
| `price` (number) | `price` | Formatted as €XX |
| `duration` (minutes) | Display | Shown as "(XX min)" |
| `category` | `category` | Filter category |
| `imageUrl` | `image` | Falls back to default |
| `destinationId` | - | Used for filtering |
| `destinationName` | - | Available from backend |

## State Management

### AppContext State Structure
```javascript
{
  destinations: [],      // Array of destination objects from API
  activities: [],        // Array of activity objects from API
  packages: [],          // Array of package objects (future)
  currentPath: '/',      // Current route
  currentTab: 'activities', // Active tab
  tripItems: [],         // User's trip builder items
  chatOpen: false,       // Chat panel state
  chatMessages: [],      // Chat history
  loading: true,         // Initial data loading
  error: null            // Error message if fetch fails
}
```

### New Actions
- `SET_DESTINATIONS` - Update destinations from API
- `SET_ACTIVITIES` - Update activities from API
- `SET_PACKAGES` - Update packages from API
- `SET_LOADING` - Toggle loading state
- `SET_ERROR` - Set error message

## Component Updates

### HomePage
- Added loading state display
- Added error state display
- Fetches destinations on mount via AppContext

### DestinationPage
- Fetches destination details by UUID
- Fetches activities filtered by destination
- Shows loading spinner during fetch
- Dynamically displays destination name/description

### DestinationCard
- Maps `imageUrl` → `image`
- Uses `rating` to determine badge
- Only Tenerife (UUID: `b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22`) is clickable

### ActivityCard
- Maps `name` → `title`
- Formats numeric `price` as €XX
- Displays `duration` if available
- Falls back to default image if `imageUrl` missing

## Environment Configuration

### `.env` File
```env
REACT_APP_API_URL=http://localhost:8081/api
```

### Usage
- Development: Uses localhost:8081
- Production: Set `REACT_APP_API_URL` to production backend URL

## Testing the Integration

### 1. Start Backend
```bash
cd C:\Users\dijtb\IdeaProjects\myhive-travel-app\myhive-backend
docker-compose up -d
```

Verify backend is running:
```bash
Invoke-WebRequest -Uri http://localhost:8081/api/destinations -UseBasicParsing
```

### 2. Start Frontend
```bash
cd C:\Users\dijtb\IdeaProjects\myhive-travel-app\myhive-react-app
npm start
```

### 3. Verify Integration

**Homepage (http://localhost:3000)**
- Should show 5 destinations from database
- Loading state should appear briefly
- Destinations: Prague, Tenerife, Bali, Dubai, New York

**Tenerife Page (http://localhost:3000/destination/b1eebc99-9c0b-4ef8-bb6d-6bb9bd380a22)**
- Should show "Tenerife" as title
- Should display 4 activities:
  - Sunset Boat Party (€50, 180 min, nightlife)
  - Teide National Park Tour (€45, 240 min, adventure)
  - Jet Ski Adventure (€70, 60 min, adventure)
  - Luxury Spa Session (€90, 120 min, daytime)

## Error Handling

### Network Errors
- AppContext catches fetch errors
- Sets `error` state with message
- HomePage displays error message

### Missing Data
- Components use fallback values
- Default images for missing `imageUrl`
- Graceful degradation for optional fields

## CORS Configuration

Backend controllers have CORS enabled:
```java
@CrossOrigin(origins = "*")
```

For production, restrict to specific origins:
```java
@CrossOrigin(origins = "https://yourdomain.com")
```

## Future Enhancements

### Planned Features
1. **Packages Integration** - Fetch packages from backend
2. **Booking Creation** - Submit trip builder to backend
3. **User Authentication** - Add login/signup
4. **Real Images** - Upload destination/activity images
5. **Search & Filters** - Advanced filtering
6. **Pagination** - Handle large datasets
7. **Caching** - Reduce API calls with React Query

### API Improvements Needed
1. Add packages endpoint to backend
2. Add image upload functionality
3. Add user authentication endpoints
4. Add search/filter endpoints
5. Add pagination support

## Troubleshooting

### "Failed to fetch" Error
**Cause**: Backend not running or wrong URL
**Solution**: 
```bash
docker-compose up -d
docker logs myhive-backend
```

### Empty Destinations List
**Cause**: Database not initialized
**Solution**:
```bash
docker-compose down -v
docker-compose up -d
```

### CORS Errors
**Cause**: Backend CORS not configured
**Solution**: Already configured with `@CrossOrigin(origins = "*")`

### Wrong Port
**Cause**: Backend running on different port
**Solution**: Update `.env` file with correct port

## Development Workflow

### Making Changes

1. **Backend Changes**:
   ```bash
   # Rebuild backend
   docker-compose build backend
   docker-compose up -d backend
   ```

2. **Frontend Changes**:
   ```bash
   # Hot reload is automatic
   # Just save files
   ```

3. **Database Changes**:
   ```bash
   # Reset database
   docker-compose down -v
   docker-compose up -d
   ```

## Production Deployment

### Environment Variables
```env
# Production
REACT_APP_API_URL=https://api.myhive.com/api

# Staging
REACT_APP_API_URL=https://staging-api.myhive.com/api
```

### Build for Production
```bash
npm run build
```

### Deploy
- Frontend: Deploy `build/` folder to static hosting (Vercel, Netlify)
- Backend: Already containerized, deploy to cloud (AWS, GCP, Azure)
- Database: Use managed PostgreSQL service

## Summary

✅ API service layer created
✅ AppContext fetches from backend
✅ Components map backend data structure
✅ Loading and error states implemented
✅ Destinations display from database
✅ Activities filter by destination
✅ Ready for production deployment
