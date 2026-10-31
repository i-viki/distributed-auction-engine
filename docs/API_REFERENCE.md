# LiveBid: REST API & STOMP Protocol Reference

**Author:** Jayavignesh ([jayavignesh.dev](https://jayavignesh.dev))  
**Version:** 1.0.0 Production  

---

## 1. Network Endpoints & Base URLs

* **Direct Backend (Application Server):** `http://localhost:8080`
* **Production Gateway (Nginx Reverse Proxy):** `http://localhost:5173` (or `http://localhost:80`)
* **Interactive OpenAPI Swagger UI:** `http://localhost:8080/swagger-ui.html`
* **Raw OpenAPI JSON Specification:** `http://localhost:8080/v3/api-docs`

---

## 2. Authentication & Authorization

All authenticated endpoints require an RFC 6750 Bearer Token in the HTTP Authorization header:
```http
Authorization: Bearer <signed-jwt-token>
```
Tokens are issued upon successful registration or login via `/api/auth/*`.

---

## 3. REST API Endpoints

### 3.1 Authentication (`/api/auth`)

#### `POST /api/auth/register`
Creates a new user account with BCrypt password encryption.

* **Request Body:**
  ```json
  {
    "name": "Jane Doe",
    "email": "jane@example.com",
    "password": "StrongPassword123!"
  }
  ```
* **Validation Rules:**
  * `name`: Required, max 100 characters.
  * `email`: Required, valid RFC 5322 email format, unique.
  * `password`: Required, minimum 8 characters (OWASP standard).
* **Response:** `201 Created`
  ```json
  {
    "id": 12,
    "name": "Jane Doe",
    "email": "jane@example.com",
    "createdAt": "2026-11-01T00:45:00"
  }
  ```

#### `POST /api/auth/login`
Authenticates user credentials and returns a signed JWT.

* **Request Body:**
  ```json
  {
    "email": "jane@example.com",
    "password": "StrongPassword123!"
  }
  ```
* **Response:** `200 OK`
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
    "id": 12,
    "name": "Jane Doe",
    "email": "jane@example.com",
    "role": "USER"
  }
  ```

---

### 3.2 Auction Catalog (`/api/auctions`)

#### `GET /api/auctions`
Retrieves a paginated list of auctions with optional filtering.

* **Query Parameters:**
  * `status` (string, optional): `SCHEDULED`, `LIVE`, or `CLOSED`.
  * `search` (string, optional): Keyword search matching title or description.
  * `minPrice` (decimal, optional): Filter by minimum current highest bid.
  * `maxPrice` (decimal, optional): Filter by maximum current highest bid.
  * `endingSoon` (boolean, default: `false`): Sort ascending by closing timestamp.
  * `page` (integer, default: `0`): Zero-indexed page number.
  * `size` (integer, default: `20`): Page size limit.
* **Response:** `200 OK`
  ```json
  [
    {
      "id": 1,
      "sellerId": 2,
      "title": "Rolex Submariner 2026",
      "description": "Mint condition with original box and papers",
      "imageUrl": "https://example.com/rolex.jpg",
      "startingPrice": 5000.00,
      "currentHighestBid": 5800.00,
      "currentHighestBidderId": 14,
      "startTime": "2026-11-01T00:00:00",
      "endTime": "2026-11-01T02:00:00",
      "status": "LIVE",
      "version": 9,
      "createdAt": "2026-11-01T00:00:00"
    }
  ]
  ```

#### `GET /api/auctions/{id}`
Retrieves full details of a single auction listing.

* **Response:** `200 OK` (schema matches list item above).

#### `POST /api/auctions` *(Authenticated)*
Schedules a new auction. The server calculates end time automatically based on `durationMinutes`.

* **Request Headers:** `Authorization: Bearer <token>`
* **Request Body:**
  ```json
  {
    "title": "Vintage Leica M6 Camera",
    "description": "Mechanical 35mm rangefinder with 50mm f/2 Summicron lens",
    "imageUrl": "https://example.com/leica.jpg",
    "startingPrice": 2200.00,
    "durationMinutes": 120
  }
  ```
* **Response:** `201 Created`

---

### 3.3 Bidding Engine (`/api/auctions/{id}/bids`)

#### `POST /api/auctions/{id}/bids` *(Authenticated)*
Submits a concurrent bid. Passes through Redis Lua sequencing, optimistic locking persistence, and STOMP broadcast.

* **Request Headers:** `Authorization: Bearer <token>`
* **Request Body:**
  ```json
  {
    "amount": 2300.00
  }
  ```
* **Security & Integrity Validations:**
  * Bidder must be authenticated.
  * Anti-shill bidding: Seller cannot bid on their own listing (`400 Bad Request`).
  * Bid amount must be strictly greater than current highest bid (`400 Bad Request`).
  * Auction status must be `LIVE` and current time $< \text{end\_time}$ (`400 Bad Request`).
* **Response:** `200 OK`
  ```json
  {
    "id": 104,
    "auctionId": 1,
    "bidderId": 12,
    "amount": 2300.00,
    "placedAt": "2026-11-01T00:52:14.391",
    "autoBid": false
  }
  ```

#### `GET /api/auctions/{id}/bids`
Fetches the immutable audit trail of all accepted bids placed on an auction, ordered descending by timestamp.

* **Response:** `200 OK`
  ```json
  [
    {
      "id": 104,
      "auctionId": 1,
      "bidderId": 12,
      "amount": 2300.00,
      "placedAt": "2026-11-01T00:52:14.391",
      "autoBid": false
    }
  ]
  ```

---

### 3.4 Automated Proxy Bidding (`/api/auctions/{id}/auto-bid`)

#### `POST /api/auctions/{id}/auto-bid` *(Authenticated)*
Configures an automated proxy agent to outbid competing bidders on behalf of the user up to a specified maximum ceiling.

* **Request Body:**
  ```json
  {
    "maxAmount": 3500.00
  }
  ```
* **Rules:**
  * Seller cannot configure auto-bids on their own auction.
  * `maxAmount` must exceed current highest bid.
* **Response:** `200 OK`

#### `DELETE /api/auctions/{id}/auto-bid` *(Authenticated)*
Deactivates the active auto-bidding proxy for the authenticated user on this auction.

* **Response:** `200 OK`

---

## 4. STOMP over WebSocket Specification

### 4.1 Connection Handshake
* **Native WebSocket URL:** `ws://localhost:5173/ws` (or `ws://localhost:8080/ws`)
* **SockJS Fallback URL:** `http://localhost:5173/ws-sockjs`

### 4.2 STOMP Frames

#### Connect:
```stomp
CONNECT
accept-version:1.1,1.2
heart-beat:10000,10000

^@
```

#### Connected (Broker Reply):
```stomp
CONNECTED
version:1.2
heart-beat:0,0

^@
```

#### Subscribe to Live Auction Room:
```stomp
SUBSCRIBE
id:sub-101
destination:/topic/auctions/1

^@
```

#### Inbound Broadcast Message (Sent by Server on Accepted Bid):
```stomp
MESSAGE
destination:/topic/auctions/1
content-type:application/json;charset=UTF-8
subscription:sub-101
message-id:msg-5501

{
  "auctionId": 1,
  "bidId": 104,
  "bidderId": 12,
  "amount": 2300.00,
  "placedAt": "2026-11-01T00:52:14.391",
  "autoBid": false,
  "currentHighestBid": 2300.00,
  "currentHighestBidderId": 12,
  "endTime": "2026-11-01T02:00:00"
}
^@
```

### 4.3 Security & Channel Interception
Clients are strictly forbidden from publishing directly to `/topic/**`. Attempting to issue a `SEND` frame results in an immediate STOMP `ERROR` frame and dropped connection:
```stomp
ERROR
message:Direct client message publishing to /topic is forbidden.

^@
```

