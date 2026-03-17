# PayBot - AI-Powered Bill Payment Chatbot

A conversational chatbot for managing and paying bills using natural language, powered by Google Gemini with Spring AI function calling. Split into two backend services communicating via REST and RabbitMQ (Choreography Saga pattern).

## Architecture

```
                                  ┌──────────────────────────┐
┌───────────────┐  POST /api/chat │  paybot-ai-service       │ :8080
│  Angular UI   │────────────────▶│  (LLM + Chat + WebSocket)│
│  :4200        │  WebSocket      │                          │
│               │◀────────────────│                          │
└───────────────┘                 └───────┬──────┬───────────┘
                                          │      │
                           REST (bills)   │      │ RabbitMQ (saga)
                                          │      │
                                  ┌───────▼──────▼───────────┐
                                  │ paybot-financial-service  │ :8081
                                  │ (Bills + Payments + DB)   │
                                  └───────────┬──────────────┘
                                              │
                                  ┌───────────▼──────────────┐
                                  │     PostgreSQL            │
                                  └──────────────────────────┘
```

**Saga Flows (async via RabbitMQ):**
1. **Payment**: Gemini calls `processPayment` → AI publishes `PaymentCommandEvent` → Financial processes (DB idempotency) → publishes `PaymentResultEvent` → AI pushes confirmation via WebSocket
2. **Schedule**: Gemini calls `schedulePayment` → AI publishes `SchedulePaymentCommandEvent` → Financial creates scheduled record → publishes `SchedulePaymentResultEvent` → AI pushes confirmation via WebSocket

**Inter-service REST** (via OpenFeign with Resilience4j fallbacks): bill queries, scheduled payment listings, cancellations

## Prerequisites

- **Docker & Docker Compose** (recommended) OR
- **Java 17+** (Amazon Corretto, OpenJDK, etc.)
- **Node.js 18+** and npm (for Angular frontend)
- **Google Cloud Service Account** with Gemini API access (credentials JSON)

## Quick Start with Docker (Recommended)

```bash
# Clean start (recommended if queues have changed)
docker-compose down -v

# Build and run
docker-compose up --build
```

Access the app:
- **Frontend**: http://localhost:4200
- **AI Service Health**: http://localhost:8080/api/health
- **Financial Service OpenAPI**: http://localhost:8081/swagger-ui.html
- **RabbitMQ Management**: http://localhost:15672 (paybot/paybot123)
- **Zipkin (Distributed Tracing)**: http://localhost:9411

The Docker setup includes:
- **AI Service**: Spring Boot on port 8080 (LLM orchestration, chat, WebSocket)
- **Financial Service**: Spring Boot on port 8081 (bills, payments, database)
- **Frontend**: Angular served via Nginx on port 4200
- **Database**: PostgreSQL 16 with Flyway migrations
- **Message Broker**: RabbitMQ 3 (async saga messaging)
- **Cache/Sessions**: Redis 7 (idempotency + chat history)
- **Tracing**: Zipkin on port 9411 (distributed trace visualization)
- Health checks and automatic service dependencies
- Nginx proxy routing `/api/*` and `/ws-paybot` (WebSocket) to the AI service

### Docker Commands

```bash
# Start in background
docker-compose up -d

# View logs
docker-compose logs -f

# View logs for a specific service
docker-compose logs -f paybot-ai-service

# Stop services
docker-compose down

# Rebuild after code changes
docker-compose up --build

# Access PostgreSQL database
docker exec -it paybot-postgres psql -U paybot -d paybotdb

# View tables
docker exec -it paybot-postgres psql -U paybot -d paybotdb -c "\dt"

# View queue depths (check DLQ for poison pills)
docker exec -it paybot-rabbitmq rabbitmqctl list_queues name messages

# Access Redis CLI
docker exec -it paybot-redis redis-cli

# View chat history keys
docker exec -it paybot-redis redis-cli KEYS "chat:history:*"

# View idempotency keys
docker exec -it paybot-redis redis-cli KEYS "chat:request:*"
```

> **Data Persistence**: PostgreSQL and Redis data are stored in Docker volumes. Your data persists across container restarts. Use `docker-compose down -v` to delete volumes and reset all data.

## Distributed Tracing with Zipkin

Both backend services export traces to Zipkin, allowing you to visualize request flows across the entire system.

**Access the Zipkin UI at http://localhost:9411**

### What You Can Visualize
- **Trace timeline** — full request flow across both services (AI service → Financial service)
- **Span details** — latency per service hop, including Feign REST calls and RabbitMQ messaging
- **Dependency graph** — auto-generated service topology showing how services communicate

### How to Use
1. Send a chat message via the UI at http://localhost:4200
2. Open Zipkin at http://localhost:9411
3. Select `paybot-ai-service` or `paybot-financial-service` from the service dropdown
4. Click **Run Query** to see recent traces
5. Click a trace to see the full span waterfall across services
6. Click **Dependencies** in the nav bar for the service topology graph

### Tracing Configuration

Spring Boot 4 removed the Zipkin tracing auto-configuration from the actuator module. Both services use a manual `TracingConfig.java` that wires the full OpenTelemetry → Zipkin pipeline:

| Bean | Purpose |
|------|---------|
| `ZipkinSpanExporter` | Sends spans to Zipkin at the configured endpoint |
| `SdkTracerProvider` | OTel SDK tracer with service name + batch span processor |
| `OpenTelemetry` | OTel SDK instance with B3 context propagation |
| `OtelTracer` → `Tracer` | Micrometer bridge to the OTel SDK |
| `TracingObservationHandlerRegistrar` | Registers tracing handlers with Spring's `ObservationRegistry` |

The Zipkin endpoint is configured via `management.zipkin.tracing.endpoint` in `application.properties` and overridden in Docker via the `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` environment variable.

## Sample Conversations

Try these prompts:

- "Show me my bills"
- "List my bills for this month"
- "I want to pay my electric bill"
- "How much is my internet bill?"
- "Pay my water bill"
- "Schedule my rent payment for next week"
- "Show my scheduled payments"
- "Cancel my scheduled payment"

## Project Structure

```
Paybot/
├── docker-compose.yml              # Container orchestration
├── paybot-shared/                   # Shared library (DTOs, enums, saga events)
│   └── src/main/java/com/agile/paybot/shared/
│       ├── dto/                     # BillDTO, ChatRequest, ChatResponse, etc.
│       ├── enums/                   # BillStatus, ScheduledPaymentStatus
│       └── event/                   # Saga events (command + result)
│
├── paybot-ai-service/               # AI orchestration service (:8080)
│   ├── Dockerfile
│   └── src/main/java/com/agile/paybot/
│       ├── PayBotApplication.java   # Main entry (@EnableFeignClients)
│       ├── client/                  # OpenFeign client + Resilience4j fallback
│       ├── config/                  # Queue, CORS, Redis, WebSocket, Security, Tracing
│       ├── controller/              # ChatController, GlobalExceptionHandler
│       ├── function/                # PayBotTools (@Tool methods for Gemini)
│       ├── listener/                # Chat, Payment result, Schedule result listeners
│       └── service/                 # ChatService (Gemini + Redis history)
│
├── paybot-financial-service/        # Financial data service (:8081)
│   ├── Dockerfile
│   └── src/main/java/com/agile/paybot/financial/
│       ├── FinancialServiceApplication.java  # Main entry (@EnableScheduling)
│       ├── config/                  # Queue, Flyway, Security, Tracing
│       ├── controller/              # InternalBillController, InternalPaymentController
│       ├── domain/entity/           # Bill, Payment, ScheduledPayment
│       ├── listener/                # Payment + Schedule saga participants
│       ├── repository/              # JPA repositories
│       ├── scheduler/               # ScheduledPaymentExecutor (5 min job)
│       └── service/                 # BillService, PaymentService, ScheduledPaymentService
│
└── paybot-ui/                       # Angular frontend (:4200)
    ├── Dockerfile
    ├── nginx.conf                   # Nginx (SPA + API/WebSocket proxy)
    └── src/app/
        ├── core/services/           # chat.service.ts, message-store.service.ts
        └── features/chat/           # Chat page, message list, chat input components
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| AI Service | Spring Boot 4.0.3, Java 17, Spring AI 2.0.0-M2 |
| LLM | Google Gemini 2.0 Flash via Spring AI |
| Inter-service REST | Spring Cloud OpenFeign + Resilience4j circuit breaker |
| Async Messaging | RabbitMQ 3 (Choreography Saga pattern) |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache/Sessions | Redis 7 (idempotency + chat history) |
| Real-time | WebSocket with STOMP over SockJS |
| Distributed Tracing | OpenTelemetry + Zipkin |
| Security | Spring Security (endpoint authorization) |
| Frontend | Angular 18 (Standalone Components, Signals) |
| Containerization | Docker, Docker Compose |
| Web Server | Nginx (production) |

## API Endpoints

### AI Service (:8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat` | Send chat message (queued via RabbitMQ) |
| GET | `/api/health` | Health check |

### Financial Service (:8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/internal/bills` | List bills (params: userId, billType, currentMonth) |
| GET | `/api/internal/bills/{id}` | Get bill by ID |
| GET | `/api/internal/bills/health` | Health check |
| POST | `/api/internal/payments` | Process payment (params: billId, amount, requestId) |
| GET | `/api/internal/payments/by-request-id` | Idempotency lookup |
| GET | `/api/internal/payments/scheduled` | List scheduled payments |
| POST | `/api/internal/payments/scheduled/{id}/cancel` | Cancel scheduled payment |

OpenAPI docs: http://localhost:8081/swagger-ui.html

### Chat Request Format
```json
{
  "message": "Show me my bills",
  "requestId": "unique-uuid",
  "sessionId": "session-uuid",
  "conversationHistory": []
}
```

### Response (Immediate)
```json
{
  "status": "Request accepted for processing"
}
```

### WebSocket Response (Async)
Connect to `/ws-paybot` and subscribe to `/topic/messages/{sessionId}`:
```json
{
  "message": {
    "role": "assistant",
    "content": "Here are your unpaid bills..."
  },
  "metadata": {
    "model": "gemini-2.0-flash",
    "sessionId": "session-uuid",
    "requestId": "unique-uuid",
    "processingTimeMs": 1250
  }
}
```

## Spring AI Integration

PayBot uses the `@Tool` annotation pattern for Gemini function calling:

```java
@Component
public class PayBotTools {
    private final FinancialClient financialClient;  // OpenFeign declarative client
    private final RabbitTemplate rabbitTemplate;     // For saga commands

    @Tool(description = "Get user's bills...")
    public String getBills(@ToolParam(description = "...") String billType) {
        // Calls FinancialClient (Feign) — fallback returns empty list if service is down
    }

    @Tool(description = "Process payment...")
    public String processPayment(@ToolParam(...) Long billId, ...) {
        // Publishes PaymentCommandEvent to RabbitMQ (async saga)
    }
}
```

Tools are registered with ChatClient via `.tools(payBotTools)`.

### Available Tools

| Tool | Type | Target |
|------|------|--------|
| `getBills` | Sync REST | Financial service |
| `getBillDetails` | Sync REST | Financial service |
| `processPayment` | Async Saga | RabbitMQ → Financial service |
| `schedulePayment` | Async Saga | RabbitMQ → Financial service |
| `getScheduledPayments` | Sync REST | Financial service |
| `cancelScheduledPayment` | Sync REST | Financial service |

### OpenFeign + Resilience4j

Inter-service REST uses Spring Cloud OpenFeign with circuit breaker fallbacks:

```java
@FeignClient(name = "financial-service", url = "${financial.service.url}",
             fallback = FinancialClientFallback.class)
public interface FinancialClient {
    @GetMapping("/api/internal/bills")
    List<BillDTO> getUnpaidBills(@RequestParam("userId") String userId);
}
```

**Fallback behavior** (when financial service is down):
- Read operations (bills, payments) → return empty list / null
- Write operations (cancel) → throw exception with user-friendly message
- Circuit breaker: opens after 50% failure rate in 10-call window, half-open after 10s

## RabbitMQ Queues

| Queue | Exchange | Routing Key | Purpose |
|-------|----------|-------------|---------|
| `chat.requests` | `chat.exchange` | `chat.request` | Chat messages → AI processing |
| `chat.requests.error` | `chat.requests.dlx` | `error` | DLQ for poison pills |
| `financial.payment.command` | `financial.exchange` | `payment.command` | AI → Financial (pay bill) |
| `financial.payment.result` | `financial.exchange` | `payment.result` | Financial → AI (payment confirmation) |
| `financial.schedule.command` | `financial.exchange` | `schedule.command` | AI → Financial (schedule payment) |
| `financial.schedule.result` | `financial.exchange` | `schedule.result` | Financial → AI (schedule confirmation) |

## Self-Healing Idempotency (Double-Check Pattern)

The `ChatTaskListener` implements a crash-resilient "Double-Check" pattern that handles the edge case where a worker dies **after** paying a bill but **before** acknowledging the RabbitMQ message or updating Redis.

### The Problem

In a distributed system, a worker can crash at any point during processing. If it crashes after executing a payment but before marking the request as `COMPLETED` in Redis, the message will be redelivered — potentially causing a **double payment**.

### The Solution: Three-State Flow

```
                    ┌───────────────────┐
  New Request ────▶ │  SETNX requestId  │
                    │  "PROCESSING"     │
                    └────────┬──────────┘
                             │
                   ┌─────────▼─────────┐
                   │  Key acquired?     │
                   └──┬─────────────┬───┘
                  YES │             │ NO
                      │             │
              ┌───────▼──────┐  ┌───▼───────────────┐
              │ Process      │  │ Read existing      │
              │ normally     │  │ value from Redis   │
              └───────┬──────┘  └───┬───────────┬────┘
                      │             │           │
                      │      "COMPLETED"   "PROCESSING"
                      │             │           │
                      │    ┌────────▼──┐  ┌─────▼──────────┐
                      │    │ Replay    │  │ DB Validation  │
                      │    │ cached    │  │ (PostgreSQL)   │
                      │    │ response  │  └──┬──────────┬──┘
                      │    └───────────┘     │          │
                      │               Payment Found  Not Found
                      │                      │          │
                      │             ┌────────▼──┐  ┌───▼──────────┐
                      │             │ Self-heal │  │ Re-acquire   │
                      │             │ from DB   │  │ lock and     │
                      │             │ record    │  │ re-process   │
                      │             └───────────┘  └──────────────┘
                      │
              ┌───────▼──────────────────┐
              │ On success:              │
              │ 1. Set Redis → COMPLETED │
              │ 2. Cache AI response     │
              │ 3. Send via WebSocket    │
              │ 4. ACK RabbitMQ message  │
              └──────────────────────────┘
```

### Error Recovery Behavior

| Scenario | Redis State | DB State | Action |
|----------|------------|----------|--------|
| Normal first request | Key doesn't exist | — | Process normally, set COMPLETED |
| Exact duplicate (already done) | COMPLETED | — | Replay cached response from Redis |
| Worker crashed after payment | PROCESSING (stale) | Payment exists | Self-heal: build response from DB, set COMPLETED |
| Worker crashed before payment | PROCESSING (stale) | No payment | Delete stale key, re-acquire lock, re-process |
| Processing error / exception | — | — | Delete Redis key, retry or route to DLQ |

## Dead Letter Queue (Poison Pill Protection)

Messages that fail repeatedly are "poison pills" — they'll loop forever unless stopped. The DLQ pattern catches them after a configurable retry limit.

```
                  ┌────────────────┐
   Request ──────▶│  chat.requests │  (main queue)
                  │  x-dead-letter │──── on reject ────┐
                  │  -exchange:    │                    │
                  │  chat.requests │                    │
                  │  .dlx          │                    │
                  └───────┬────────┘                    │
                          │                             │
                    ┌─────▼──────┐               ┌─────▼──────────┐
                    │ Listener   │               │ chat.requests  │
                    │ processes  │               │ .dlx (exchange)│
                    └─────┬──────┘               └─────┬──────────┘
                          │                            │
                   Success? ──No──▶ retryCount < 3?    │
                     │                  │              │
                    Yes            Yes: NACK      No: NACK
                     │            (requeue=true)  (requeue=false)
                     │                  │              │
                    ACK            Back to        ┌────▼────────────┐
                                  main queue      │ chat.requests   │
                                                  │ .error (queue)  │
                                                  └─────────────────┘
```

### Retry Behavior

| Retry Count | Action | User Notification |
|-------------|--------|-------------------|
| 0 (first attempt) | Process normally | — |
| 1-2 | NACK + requeue, retry | — |
| 3+ (max reached) | NACK without requeue (routes to DLQ) | WebSocket: *"I'm having trouble processing this specific request. Please try rephrasing."* |

## Testing

### Manual Testing (curl)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Show me my bills","requestId":"test-1","sessionId":"sess-1","conversationHistory":[]}'
```

### Verify Saga Queues
```bash
docker exec paybot-rabbitmq rabbitmqctl list_queues name messages consumers
```

### Unit Tests
```bash
# AI Service
cd paybot-ai-service && ./mvnw test

# Financial Service
cd paybot-financial-service && ./mvnw test

# Frontend
cd paybot-ui && npm test
```

### Inspecting the DLQ
```bash
# Via RabbitMQ Management UI
# Open http://localhost:15672 → Queues → chat.requests.error

# Via CLI
docker exec -it paybot-rabbitmq rabbitmqctl list_queues name messages
```

## Environment Variables

| Variable | Service | Description |
|----------|---------|-------------|
| `GOOGLE_APPLICATION_CREDENTIALS` | ai-service | Path to Gemini service account JSON |
| `FINANCIAL_SERVICE_URL` | ai-service | URL of financial service (default: `http://paybot-financial-service:8081`) |
| `SPRING_DATASOURCE_URL` | financial-service | PostgreSQL JDBC URL |
| `SPRING_RABBITMQ_HOST` | both | RabbitMQ hostname |
| `SPRING_DATA_REDIS_HOST` | ai-service | Redis hostname |
| `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` | both | Zipkin span collector URL |

## Troubleshooting

### "Queue args mismatch" error
RabbitMQ queue arguments have changed. Reset with:
```bash
docker-compose down -v
docker-compose up --build
```

### Build fails with dependency errors
Rebuild the shared library first:
```bash
cd paybot-shared && mvn clean install
cd ../paybot-ai-service && ./mvnw clean package
cd ../paybot-financial-service && ./mvnw clean package
```

### Financial service unreachable from AI service
Check that the financial service is healthy and that `depends_on` ordering is correct in `docker-compose.yml`:
```bash
curl http://localhost:8081/api/internal/bills/health
docker-compose logs paybot-financial-service
```

### No traces appearing in Zipkin
Both services require the manual `TracingConfig.java` (Spring Boot 4 removed Zipkin auto-config). Verify:
1. Zipkin container is healthy: `docker ps --filter name=zipkin`
2. Send a request, wait 5-10 seconds, then check: `curl http://localhost:9411/api/v2/services`
3. Both `paybot-ai-service` and `paybot-financial-service` should appear in the response

### CORS errors
The AI service is configured to allow requests from `http://localhost:4200`. If using a different port, update `CorsConfig.java`.

## License

For learning purposes only.
