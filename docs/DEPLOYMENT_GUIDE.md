# LiveBid: Production Deployment & Operations Guide

**Author:** Jayavignesh ([jayavignesh.dev](https://jayavignesh.dev))  
**Version:** 1.0.0 Production  

---

## 1. Prerequisites

* **Docker Engine:** 24.0+
* **Docker Compose:** v2.20+
* **System Resources:** 2 CPU cores, 4 GB available RAM, 10 GB disk space

---

## 2. Quickstart Deployment

1. **Clone the repository:**
   ```bash
   git clone https://github.com/i-viki/distributed-auction-engine.git
   cd distributed-auction-engine
   ```

2. **Configure environment variables:**
   ```bash
   cp docker/.env.example docker/.env
   # Edit docker/.env to supply a production-grade JWT_SECRET and custom passwords
   ```

3. **Launch the entire distributed stack:**
   ```bash
   cd docker
   docker compose up -d --build
   ```

4. **Verify container health:**
   ```bash
   docker ps
   ```

---

## 3. Port Mappings & Service Discovery

| Service Container | Internal Port | Host Port | Role | Healthcheck |
| :--- | :--- | :--- | :--- | :--- |
| `livebid-frontend` | `80` | `5173` | Nginx reverse proxy + React SPA | Nginx PID check |
| `livebid-backend` | `8080` | `8080` | Spring Boot auction core | Actuator / Tomcat port check |
| `livebid-postgres` | `5432` | `5432` | PostgreSQL 16 ACID store | `pg_isready` |
| `livebid-redis` | `6379` | `6379` | Redis 7 in-memory sequencer | `redis-cli ping` |

---

## 4. Environment Variables Reference (`docker/.env`)

| Variable | Default Value | Description |
| :--- | :--- | :--- |
| `TZ` | `Asia/Kolkata` | System timezone applied across all containers |
| `POSTGRES_DB` | `livebid_db` | PostgreSQL database name |
| `POSTGRES_USER` | `postgres` | Database admin user |
| `POSTGRES_PASSWORD` | `postgrespassword` | Database password *(Change in production)* |
| `POSTGRES_PORT` | `5432` | Host port mapped to PostgreSQL |
| `REDIS_PORT` | `6379` | Host port mapped to Redis |
| `BACKEND_PORT` | `8080` | Host port mapped to Spring Boot API |
| `FRONTEND_PORT` | `5173` | Host port mapped to Nginx web gateway |
| `JWT_SECRET` | *32+ byte secret* | HS256 secret used to sign and verify Bearer tokens |
| `JWT_EXPIRATION_MS` | `86400000` | Token expiration lifetime in milliseconds (24 hours) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,...` | Comma-separated list of allowed browser origins |
| `ANTI_SNIPE_WINDOW_MINUTES` | `2` | Threshold before closing that triggers time extensions |
| `ANTI_SNIPE_EXTENSION_MINUTES` | `2` | Duration added to closing time when bid is placed in window |
| `BID_MAX_RETRIES` | `3` | Maximum optimistic locking retry attempts on contention |
| `AUTO_BID_INCREMENT` | `10.00` | Standard increment applied by proxy auto-bidding bots |
| `CLOSING_SCHEDULER_RATE_MS` | `10000` | Frequency of background auction lifecycle sweep (10s) |

---

## 5. Production Performance & JVM Tuning

The backend container runs on OpenJDK 21 configured with the **Generational Z Garbage Collector (ZGC)**:
```dockerfile
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:+ZGenerational", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
```

### Why Generational ZGC?
* **Sub-Millisecond Pauses:** ZGC maintains pause times strictly $< 1\text{ms}$ even under high allocation rates, preventing STW (Stop-The-World) latency spikes during bidding rushes.
* **Low Memory Footprint:** Bounded heap allocations (`-Xms256m -Xmx512m`) prevent JVM out-of-memory container evictions in constrained cloud environments.

---

## 6. Common Operations & Maintenance

### View Live Backend Logs:
```bash
docker logs -f livebid-backend
```

### Inspect Redis Bidding Activity:
```bash
docker exec -it livebid-redis redis-cli monitor
```

### Access PostgreSQL Interactive Shell:
```bash
docker exec -it livebid-postgres psql -U postgres -d livebid_db
```

### Run Live Validation & Security Tests:
```bash
python tests/test_live_api.py
python tests/test_stomp_e2e.py
```

### Execute Concurrency Load Test:
```bash
python tests/load_test_1000_bids.py
```

