---
description: Run the PayBot application using Docker Compose
---

This workflow starts the entire PayBot stack using Docker Compose.

1. Ensure common variables are set in `.env`
   - `POSTGRES_USER=paybot`
   - `POSTGRES_PASSWORD=paybot123`
   - `RABBITMQ_USER=paybot`
   - `RABBITMQ_PASSWORD=paybot123`
   - `REDIS_PASSWORD=paybot123`

2. Deploy the stack (build if necessary)
// turbo
3. `docker-compose up --build -d`

4. Monitor startup
// turbo
5. `docker-compose logs -f --tail 50`

Watch for:
- "Started PayBotApplication"
- "Started FinancialServiceApplication"

6. Verify health
// turbo
7. `curl http://localhost:8080/api/health`
// turbo
8. `curl http://localhost:8081/api/internal/bills/health`

Contact the user once all services are confirmed healthy.
