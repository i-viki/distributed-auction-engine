# Distributed Real-Time Auction & Bidding Sequencer Engine

[![Java](https://img.shields.io/badge/Java-21%20LTS-18181b?logo=openjdk&logoColor=ED8B00&labelColor=101012)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-18181b?logo=springboot&logoColor=6DB33F&labelColor=101012)](https://spring.io)
[![Redis](https://img.shields.io/badge/Redis-7.2-18181b?logo=redis&logoColor=FF4438&labelColor=101012)](https://redis.io)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-18181b?logo=postgresql&logoColor=4169E1&labelColor=101012)](https://www.postgresql.org)
[![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-18181b?logo=socketdotio&logoColor=009688&labelColor=101012)](https://spring.io)
[![React](https://img.shields.io/badge/React-19-18181b?logo=react&logoColor=61DAFB&labelColor=101012)](https://react.dev)
[![Vite](https://img.shields.io/badge/Vite-8.x-18181b?logo=vite&logoColor=9135FF&labelColor=101012)](https://vite.dev)
[![Docker](https://img.shields.io/badge/Docker-Compose-18181b?logo=docker&logoColor=2496ED&labelColor=101012)](https://www.docker.com)
[![JWT](https://img.shields.io/badge/Security-JWT-18181b?logo=jsonwebtokens&logoColor=FFFFFF&labelColor=101012)](https://jwt.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

An enterprise-grade, **high-throughput distributed auction engine and real-time trading floor**, engineered for high-contention live bidding, deterministic microsecond sequencing, and sub-50ms state broadcasting. Replacing slow relational table locking (`SELECT FOR UPDATE`), the platform coordinates an **in-memory Redis Lua atomic sequencer**, **Optimistic Concurrency Control (OCC)**, **Redis Pub/Sub cluster distribution**, and **STOMP over WebSockets** to process 275+ bids/sec with zero dirty writes and zero lost updates.

---

## Key Architecture Pillars

- **In-Memory Lua Microsecond Sequencing (`backend/.../RedisBiddingSequencer.java`):**
  Validates, ranks, and assigns strictly monotonic sequence identifiers within Redis memory before touching persistence tiers, eliminating race conditions during closing-second bid wars.
- **Optimistic Concurrency Control with Auto-Retry (`backend/.../BidService.java`):**
  Guarantees ACID transactional integrity through versioned atomic SQL updates (`WHERE id = ? AND version = ?`), falling back to a bounded 3-tier exponential retry loop upon collision.
- **Real-Time Clustered State Broadcast (`backend/.../WebSocketConfig.java`):**
  Fans out accepted bids across stateless application nodes via Redis Pub/Sub (`auction:bids:broadcast`) and pushes updates to active browser rooms via native STOMP WebSockets in $< 50\text{ms}$.
- **Dynamic Anti-Sniping Protection (`backend/.../AuctionClosingScheduler.java`):**
  Automatically detects bids placed within the final 2-minute closing window and dynamically extends auction end time by +2 minutes ($T_{end} \leftarrow T_{end} + \Delta t$) to defeat predatory sniping bots.
- **Automated Proxy Bidding Agents (`backend/.../AutoBidService.java`):**
  Executes incremental outbidding on behalf of users up to pre-configured ceiling limits without exposing user budgets to competing bidders.
- **Zero-Trust Market Integrity (`backend/.../security/`):**
  Hardened against shill bidding (sellers blocked from bidding on their own lots), parameter tampering, and cross-site WebSocket hijacking via strict STOMP channel interceptors.

---

## System Architecture

```mermaid
flowchart TB
    subgraph CLIENT["Bidders & Trading Floor"]
        UI["React 19 Trading Floor<br/>Port: 5173"]
        BOTS["Proxy Bidding Bots<br/>Automated Agents"]
    end

    subgraph EDGE["Edge & Routing Tier"]
        NGINX["Nginx Load Balancer<br/>Ports: 80 / 5173"]
        SEC["Spring Security Filter<br/>Stateless JWT Validation"]
        UI --> NGINX
        BOTS --> NGINX
        NGINX --> SEC
    end

    subgraph GATEWAY["Stateless Application Cluster (Java 21 / Spring Boot)"]
        direction TB
        NODE1["Application Node 1<br/>Port: 8080<br/>• REST Controllers<br/>• STOMP Broker"]
        NODE2["Application Node 2<br/>Port: 8081<br/>• REST Controllers<br/>• STOMP Broker"]
        SEC --> NODE1
        SEC --> NODE2
    end

    subgraph ENGINE["Bidding & Sequencing Engine"]
        BID_SVC["BidService<br/>• OCC Versioning<br/>• Collision Retry Engine"]
        LUA_SEQ["RedisBiddingSequencer<br/>• Microsecond Lua Atomic Script<br/>• Monotonic Ranking"]
        AUTO_BID["AutoBidService<br/>• Ceiling Watcher<br/>• Outbid Trigger"]
        SCHED["AuctionClosingScheduler<br/>• Anti-Snipe Monitor<br/>• Status FSM"]
        NODE1 --> BID_SVC
        NODE2 --> BID_SVC
        BID_SVC <--> LUA_SEQ
        BID_SVC <--> AUTO_BID
        SCHED -.->|State Transitions| BID_SVC
    end

    subgraph PUB_SUB["Real-Time Event Distribution (Redis 7.2)"]
        REDIS_BUS[("Redis 7.2 Pub/Sub<br/>Channel: auction:bids:broadcast<br/>Port: 6379")]
        BID_SVC -->|Publish New High Bid| REDIS_BUS
        REDIS_BUS -->|Fanout Event| NODE1
        REDIS_BUS -->|Fanout Event| NODE2
    end

    subgraph STORAGE["Persistence Tier (PostgreSQL 16)"]
        PG[("PostgreSQL 16 Primary DB<br/>Port: 5432<br/>• auctions (OCC Versioned)<br/>• bids (Audit Trail)<br/>• users & autobids")]
        BID_SVC -->|Atomic Update| PG
        SCHED -->|Batch Transitions| PG
    end

    NODE1 -.->|STOMP Sub-50ms Push| UI
    NODE2 -.->|STOMP Sub-50ms Push| UI
```

---

## Concurrency & Optimistic Locking Invariants

The engine guarantees absolute market integrity under high contention by decoupling microsecond evaluation from persistent writebacks:

$$\Delta \text{Version} = 1 \iff \text{BidAmount} > \text{CurrentHighest} \land \text{ExpectedVersion} = \text{PersistedVersion}$$

$$T_{end}' = \begin{cases} T_{end} + 120\text{s} & \text{if } (T_{end} - T_{\text{bid}}) \le 120\text{s} \\ T_{end} & \text{otherwise} \end{cases}$$

| Operational Invariant | Technical Mechanism | Guarantee |
| :--- | :--- | :--- |
| **Strict Bid Ordering** | Redis Atomic Lua Script | Bids arriving within identical milliseconds receive monotonic sequences |
| **Zero Lost Updates** | PostgreSQL Optimistic Lock (`@Version`) | Collisions detected immediately; zero uncommitted writes |
| **Collision Resolution** | 3-Tier Exponential Backoff | Transparent retry re-reads state and validates if bid remains viable |
| **Anti-Shill Enforcement** | `auction.seller_id != bidder_id` | Sellers cannot artificially inflate their own auctions |
| **Anti-Snipe Defense** | Automated End-Time Extension | Extends countdown by +2 mins if bid lands in final 120 seconds |

---

## Quickstart & Platform Orchestration

### Prerequisites
- Docker Desktop (with Compose v2+)
- Java 21 LTS & Maven 3.9+
- Node.js 20+ & npm

---

### Option A: Automated Docker Compose Orchestration (Recommended)

Launch the distributed cluster (PostgreSQL 16, Redis 7.2, Spring Boot backend, and Nginx-powered React trading floor) in a single command:
```bash
cd docker
docker compose up --build -d
```
*Automatically starts PostgreSQL 16 (`:5432`), Redis 7.2 (`:6379`), Spring Boot API (`:8080`), and React Trading Floor (`:5173`).*

To cleanly shut down all containers and volumes:
```bash
docker compose down -v
```

---

### Option B: Step-by-Step Manual Launch

#### Step 1: Start Persistence & Cache Containers
```bash
cd docker
docker compose up -d postgres redis
```

#### Step 2: Launch Java 21 Auction Engine
```bash
cd backend
mvn clean spring-boot:run
# Backend REST & STOMP gateway active on :8080
```

#### Step 3: Launch React Trading Floor
```bash
cd frontend
npm install
npm run dev
# Trading floor active at: http://localhost:5173
```

---

## Decoupled Environment Strategy

All services extract hostnames, ports, and invariants into environment files with resilient local fallbacks:

| Service / Layer | Config File | Template Reference | Key Variables |
| :--- | :--- | :--- | :--- |
| **Trading Client** | `frontend/vite.config.js` | [`frontend/.env.example`](frontend/.env.example) | `VITE_API_BASE_URL`, `VITE_WS_ENDPOINT` |
| **Auction Backend** | `backend/src/main/resources/application.properties` | [`docker/.env.example`](docker/.env.example) | `BACKEND_PORT`, `POSTGRES_PORT`, `REDIS_PORT`, `JWT_SECRET` |
| **Engine Invariants** | `application.properties` | [`docker/.env.example`](docker/.env.example) | `ANTI_SNIPE_WINDOW_MINUTES`, `ANTI_SNIPE_EXTENSION_MINUTES`, `BID_MAX_RETRIES` |
| **Docker Compose** | `docker/docker-compose.yml` | [`docker/.env.example`](docker/.env.example) | Complete cluster port, container, and network definitions |

---

## Live Telemetry & Observability Hubs

| Hub / Service | Port | URL | Role & Live Features |
| :--- | :--- | :--- | :--- |
| **Trading Floor Web App** | `5173` | [http://localhost:5173](http://localhost:5173) | Live Interactive Trading Floor, STOMP room updates & bidding bot UI. |
| **Backend REST Gateway** | `8080` | [http://localhost:8080](http://localhost:8080) | Core auction state, proxy bot management, and user notifications. |
| **Interactive OpenAPI Docs**| `8080` | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | OpenAPI 3.0 interactive Swagger specification. |
| **Spring Boot Actuator** | `8080` | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) | Backend health checks, thread pools, and datasource status. |
| **STOMP WebSocket Hub** | `8080` | `ws://localhost:8080/ws` | Real-time bi-directional messaging channel for bid streaming. |

---

## Testing & Quality Assurance Suite

### In-Memory Concurrency Test Suite (Java 21)
```bash
cd backend
mvn test
# Executes ConcurrentBidTest asserting zero dirty writes under parallel threads.
```

### 1,000-Bid High-Contention Stress Benchmark (Python 3)
```bash
python tests/load_test_1000_bids.py
# Fired across 50 concurrent threads: asserts 0 unexpected errors, verifies version counter integrity.
```

### Sample Authenticated Bid Placement
```bash
curl -X POST http://localhost:8080/api/auctions/1/bids \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "amount": 750.00
  }'
```

---

## Author

**Jayavignesh**
- **Website:** [jayavignesh.dev](https://jayavignesh.dev)
- **GitHub:** [@i-viki](https://github.com/i-viki)
- **Email:** [hello@jayavignesh.dev](mailto:hello@jayavignesh.dev)

---

## License

This platform is distributed under the **MIT License**. See the [`LICENSE`](LICENSE) file for complete terms.
