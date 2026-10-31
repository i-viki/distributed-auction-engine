# LiveBid Documentation Index

Technical documentation for the LiveBid Distributed Auction and Real-Time Bidding Sequencer Engine.

---

## Documentation Navigation

- **[System Architecture](ARCHITECTURE.md)**  
  High-level architecture topology, the concurrency hot-row bottleneck, two-phase memory-relational sequencing, Redis Lua internals, and horizontal STOMP clustering.

- **[REST API & STOMP Reference](API_REFERENCE.md)**  
  Complete REST endpoint documentation (Auth, Catalog, Bidding, Auto-bid), request/response schemas, security policies, and the native STOMP WebSocket frame protocol.

- **[Database Schema & Data Model](DATABASE_SCHEMA.md)**  
  Entity-Relationship diagram (ERD), PostgreSQL 16 table DDL, optimistic locking versioning mechanisms, and query-optimized indexing strategies.

- **[Concurrency & Stress Benchmark](CONCURRENCY_BENCHMARK.md)**  
  Performance metrics from 1,000 concurrent bids across 50 simultaneous worker threads (253.15 req/sec), latency percentiles, and zero-lost-update verification.

- **[Production Deployment & Operations Guide](DEPLOYMENT_GUIDE.md)**  
  Multi-container Docker Compose deployment, environment variable dictionary, Generational ZGC JVM tuning, Nginx reverse proxy configurations, and operational commands.
