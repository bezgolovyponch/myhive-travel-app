# Trivlu — Frontend (React 19 SPA)

Customer-facing + admin single-page app for the Trivlu travel platform. Bootstrapped with
Create React App; talks to the Spring Boot backend in [`../myhive-backend`](../myhive-backend).

## Prerequisites

- Node 18+
- Backend running (defaults to `http://localhost:8080`)

## Scripts

| Command         | What it does                                                                                          |
|-----------------|-------------------------------------------------------------------------------------------------------|
| `npm install`   | Install dependencies                                                                                  |
| `npm start`     | Dev server on http://localhost:3000                                                                   |
| `npm test`      | Jest + React Testing Library (watch mode)                                                             |
| `npm run build` | Production build to `build/`; also copies `index.html` → `404.html` so SPA deep links work on static hosts |

## Environment Variables

Build-time only — CRA inlines `REACT_APP_*` at build time. Put them in `.env` (see `.env.example`).

| Variable                       | Required         | Default                    | Purpose                              |
|--------------------------------|------------------|----------------------------|--------------------------------------|
| `REACT_APP_API_URL`            | yes              | `http://localhost:8080`    | Backend base URL                     |
| `REACT_APP_SITE_URL`           | no               | `https://trivlu.com`       | Canonical URLs / SEO                 |
| `REACT_APP_OIDC_AUTHORITY`     | yes              | –                          | Auth0 tenant issuer URL              |
| `REACT_APP_OIDC_CLIENT_ID`     | yes              | –                          | Auth0 SPA client ID                  |
| `REACT_APP_OIDC_AUDIENCE`      | yes              | –                          | API audience (`https://api.trivlu.com`) |
| `REACT_APP_OIDC_REDIRECT_URI`  | no               | `<origin>/admin`           | OIDC redirect target                 |
| `REACT_APP_OIDC_ROLES_CLAIM`   | no               | `https://trivlu.com/roles` | JWT claim holding roles              |
| `REACT_APP_TURNSTILE_SITE_KEY` | for contact form | –                          | Cloudflare Turnstile site key        |

## Project Structure (`src/`)

| Path          | Purpose                                                                                              |
|---------------|------------------------------------------------------------------------------------------------------|
| `context/`    | `AppContext` (destinations / activities / trip-builder state via `useReducer`), `AuthContext` (Auth0 via `react-oidc-context`) |
| `services/`   | `api.js` (public endpoints), `adminApi.js` (admin endpoints, sends JWT), `config.js` (API/site URLs) |
| `pages/`      | Route-level components — Home, Destination, ActivityDetail, Blog, Contact, plus `pages/admin/*` and `pages/vote/*` |
| `components/` | Reusable UI — Header, Footer, Layout, AdminLayout, ProtectedRoute, TripBuilder, ChatPanel, `components/admin/*` |
| `hooks/`, `utils/`, `styles/` | Custom hooks, helpers, and styling                                                   |

## Routing & Auth

- **`BrowserRouter`** (clean URLs). Static hosting relies on the `404.html` fallback produced by `npm run build`.
- Admin routes are guarded by `ProtectedRoute`. Auth is **Auth0 OIDC** via `react-oidc-context` + `oidc-client-ts`;
  access token stored in `localStorage` (survives refresh / shared across tabs), silently renewed via hidden iframe.
  Roles: `ADMIN`, `MANAGER`.

## Key Libraries

React 19 · react-router-dom 7 · react-oidc-context + oidc-client-ts · Bootstrap 5 + react-bootstrap ·
@dnd-kit (drag-and-drop trip builder) · react-day-picker (date-range picker) · react-helmet-async (per-page SEO meta).

## Deployment

Served as a **static site on Render** (build output in `build/`). The build step copies `index.html` to
`404.html` so client-side deep links resolve on static hosting.
