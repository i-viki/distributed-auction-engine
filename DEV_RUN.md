# Distributed Auction Engine — Local Development Run Guide

## Option 1: 1-Click Launch (Recommended)

From the project root:
* **Command Prompt / Windows Explorer**: Double-click [`start-dev.bat`](start-dev.bat)
* **PowerShell**: Run [`.\start-dev.ps1`](start-dev.ps1)

This opens 3 designated terminal windows and starts all services in the proper order.

---

## Option 2: Manual Terminal Commands

### Terminal 1 — Redis Server
```powershell
redis-server --port 6379
```

### Terminal 2 — Spring Boot Backend
```powershell
cd d:\reverse\distributed-auction-engine\backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev"
```

### Terminal 3 — React Vite Frontend
```powershell
cd d:\reverse\distributed-auction-engine\frontend
npm run dev
```

---

## Service URLs
* **React Frontend**: [http://localhost:5173](http://localhost:5173)
* **Backend REST API**: [http://localhost:8080](http://localhost:8080)
* **Interactive Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **In-Memory H2 DB Console**: [http://localhost:8080/h2-console](http://localhost:8080/h2-console) (JDBC URL: `jdbc:h2:mem:livebid_db`)
* **Redis Server**: `localhost:6379`

