# ZoneDrop Backend API

This document describes the REST and WebSocket APIs exposed by the ZoneDrop backend.

Base URL:

```text
http://localhost:8081
```

Authentication:

- Public endpoints: `/auth/**`, `/ws-zonedrop/**`
- Protected REST endpoints: everything under `/zonedrop/**`
- Protected REST calls must include `Authorization: Bearer <jwt>`
- WebSocket clients can also send the same header during STOMP `CONNECT`

## Auth APIs

### `POST /auth/signup`

Creates a new user account and immediately returns a JWT.

Request body:

```json
{
  "name": "Alice",
  "email": "alice@example.com",
  "password": "secret123"
}
```

Response: `201 Created`

```json
{
  "token": "<jwt>",
  "user": {
    "id": 1,
    "name": "Alice",
    "email": "alice@example.com"
  }
}
```

Validation and behavior:

- `name`, `email`, and `password` are required
- Email is normalized to lowercase
- Duplicate email returns `409 Conflict`
- JWT TTL is 24 hours

Usage:

- Call this when registering a new user
- Store the returned token and send it in the `Authorization` header for protected APIs

Example:

```bash
curl -X POST http://localhost:8081/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"secret123"}'
```

### `POST /auth/login`

Authenticates an existing user and returns a JWT.

Request body:

```json
{
  "email": "alice@example.com",
  "password": "secret123"
}
```

Response: `200 OK`

```json
{
  "token": "<jwt>",
  "user": {
    "id": 1,
    "name": "Alice",
    "email": "alice@example.com"
  }
}
```

Validation and behavior:

- `email` and `password` are required
- Email is normalized to lowercase
- Invalid credentials return `401 Unauthorized`

Usage:

- Call this when an existing user signs in
- Reuse the token for all protected REST endpoints and optional authenticated WebSocket connects

Example:

```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}'
```

## User APIs

All endpoints in this section require:

```text
Authorization: Bearer <jwt>
```

### `GET /zonedrop/users`

Returns all users.

Response: `200 OK`

```json
[
  {
    "id": 1,
    "name": "Alice",
    "email": "alice@example.com"
  }
]
```

Usage:

- Use this to populate a basic user directory or selector

Example:

```bash
curl http://localhost:8081/zonedrop/users \
  -H "Authorization: Bearer <jwt>"
```

### `GET /zonedrop/users/locations`

Returns all users with their latest stored live location, if any.

Response: `200 OK`

```json
[
  {
    "userId": 1,
    "name": "Alice",
    "email": "alice@example.com",
    "latitude": 12.9716,
    "longitude": 77.5946
  },
  {
    "userId": 2,
    "name": "Bob",
    "email": "bob@example.com",
    "latitude": null,
    "longitude": null
  }
]
```

Usage:

- Use this to load an initial map or roster showing which users already have a location

Example:

```bash
curl http://localhost:8081/zonedrop/users/locations \
  -H "Authorization: Bearer <jwt>"
```

### `POST /zonedrop/users`

Creates a user record directly.

Request body:

```json
{
  "name": "Bob",
  "email": "bob@example.com",
  "password": "secret123"
}
```

Response: `201 Created`

```json
{
  "id": 2,
  "name": "Bob",
  "email": "bob@example.com"
}
```

Behavior notes:

- This endpoint does not perform the same validation as `/auth/signup`
- The password is persisted from the request payload as-is in current service logic
- Prefer `/auth/signup` for end-user registration flows

Usage:

- Best suited for internal tooling or seed-like flows where a token is already available

Example:

```bash
curl -X POST http://localhost:8081/zonedrop/users \
  -H "Authorization: Bearer <jwt>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Bob","email":"bob@example.com","password":"secret123"}'
```

## File APIs

All endpoints in this section require:

```text
Authorization: Bearer <jwt>
```

### `POST /zonedrop/files`

Uploads a file for a user using `multipart/form-data`.

Form fields:

- `userId`: number
- `file`: binary file

Response: `201 Created`

```json
{
  "id": 10,
  "userId": 1,
  "fileName": "notes.pdf",
  "storageKey": "users/1/550e8400-e29b-41d4-a716-446655440000-notes.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": 24567
}
```

Validation and behavior:

- `userId` is required
- `file` is required and must be non-empty
- Unknown user returns `404 Not Found`
- File bytes are uploaded to Supabase storage before metadata is persisted
- If no original filename is present, the backend uses `upload.bin`
- If no content type is present, the backend uses `application/octet-stream`

Usage:

- Use this when a user uploads shareable content
- Save the returned metadata if the client wants to render upload confirmation immediately

Example:

```bash
curl -X POST http://localhost:8081/zonedrop/files \
  -H "Authorization: Bearer <jwt>" \
  -F "userId=1" \
  -F "file=@/path/to/notes.pdf"
```

### `GET /zonedrop/files`

Returns a catalog of all uploaded files.

Response: `200 OK`

```json
[
  {
    "userId": 1,
    "userName": "Alice",
    "fileName": "notes.pdf",
    "fileSize": 24567,
    "downloadUrl": "https://..."
  }
]
```

Usage:

- Use this to build a global file feed or admin catalog

Example:

```bash
curl http://localhost:8081/zonedrop/files \
  -H "Authorization: Bearer <jwt>"
```

### `GET /zonedrop/files/{userId}`

Returns files uploaded by a specific user.

Path parameters:

- `userId`: numeric user id

Response: `200 OK`

```json
[
  {
    "downloadUrl": "https://...",
    "fileName": "notes.pdf",
    "fileSize": 24567
  }
]
```

Validation and behavior:

- Unknown user returns `404 Not Found`

Usage:

- Use this for a user profile page or “my uploads” screen

Example:

```bash
curl http://localhost:8081/zonedrop/files/1 \
  -H "Authorization: Bearer <jwt>"
```

## WebSocket APIs

ZoneDrop also exposes STOMP-over-WebSocket messaging for live location features.

Connection details:

- WebSocket endpoint: `/ws-zonedrop`
- SockJS is enabled
- Client send prefix: `/app`
- Client subscribe prefixes: `/topic`, `/queue`

Optional authenticated connect:

- Add native STOMP header `Authorization: Bearer <jwt>` on `CONNECT`
- The server accepts unauthenticated connects too, but authenticated connects let the interceptor attach a Spring Security principal if the token is valid

### Send to `/app/location.update`

Updates a user’s live location, persists it, stores it in Redis GEO, and triggers broadcasts.

Message body:

```json
{
  "userId": 1,
  "latitude": 12.9716,
  "longitude": 77.5946
}
```

Server-side validation:

- `userId` is required
- `latitude` must be between `-90` and `90`
- `longitude` must be between `-180` and `180`
- Unknown user results in `404 Not Found`

Broadcasts triggered after a successful update:

- `/topic/location/{userId}` with the updated location
- `/topic/locations/all` with the same updated location
- `/topic/nearby/{userId}` with nearby users within the default radius of `5.0 km`

Broadcast payload for `/topic/location/{userId}` and `/topic/locations/all`:

```json
{
  "id": 5,
  "userId": 1,
  "latitude": 12.9716,
  "longitude": 77.5946
}
```

Broadcast payload for `/topic/nearby/{userId}`:

```json
[
  {
    "userId": 1,
    "name": "Alice",
    "email": "alice@example.com",
    "latitude": 12.9716,
    "longitude": 77.5946,
    "distanceKm": 0.0
  }
]
```

Usage:

- Use this when the client periodically publishes live GPS updates
- Subscribe to the related topics before sending updates if the UI needs immediate server push results

### Send to `/app/nearby.search`

Looks up nearby users around the sender’s already stored live location and publishes the result.

Message body:

```json
{
  "userId": 1,
  "radiusKm": 5.0
}
```

Behavior:

- The server reads the user’s saved live location from the database
- Nearby users are resolved from Redis GEO data
- Results are sent to `/topic/nearby/{userId}`
- Invalid requests are swallowed in current controller logic, so clients should rely on whether a topic update arrives

Broadcast payload sent to `/topic/nearby/{userId}`:

```json
[
  {
    "userId": 2,
    "name": "Bob",
    "email": "bob@example.com",
    "latitude": 12.975,
    "longitude": 77.6,
    "distanceKm": 0.82
  }
]
```

Validation performed by service logic:

- User must exist
- User must already have a stored live location
- `radiusKm` must be greater than `0`

Usage:

- Use this for an explicit “find people near me” action
- It is also updated automatically after each successful `/app/location.update`

## Typical Client Flow

1. Call `/auth/signup` or `/auth/login` and store the JWT.
2. Use the JWT for protected REST APIs under `/zonedrop/**`.
3. Connect to `/ws-zonedrop` with SockJS/STOMP.
4. Optionally include `Authorization: Bearer <jwt>` in the STOMP `CONNECT` headers.
5. Subscribe to `/topic/location/{userId}`, `/topic/locations/all`, and `/topic/nearby/{userId}` as needed.
6. Send `/app/location.update` messages as the user moves.
7. Optionally send `/app/nearby.search` for manual nearby discovery.

## Error Shape

This project does not define a custom global error format. For REST validation or lookup failures, Spring will typically return its default error response with fields such as:

```json
{
  "timestamp": "2026-03-23T12:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "path": "/auth/signup"
}
```

The exact shape can vary slightly by Spring Boot version and exception path, but the HTTP status codes documented above are the important contract.
