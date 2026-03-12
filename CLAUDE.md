# PayBot - Conversational Bill Payment Chatbot

A natural language chatbot for managing and paying bills, powered by Google Gemini with Spring AI function calling. Split into two backend services communicating via REST and RabbitMQ (Choreography Saga pattern).

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

**Saga Payment Flow:**
1. Gemini calls `processPayment` tool → AI service publishes `PaymentCommandEvent` to RabbitMQ
2. Financial service consumes, processes payment (DB-level idempotency), publishes `PaymentResultEvent`
3. AI service consumes result, pushes confirmation to user via WebSocket

## Quick Start (Docker - Recommended)

```bash
docker-compose down -v    # Clean start if queues changed
docker-compose up --build
```

Access:
- Frontend: http://localhost:4200
- AI Service API: http://localhost:8080
- AI Service Health: http://localhost:8080/api/health
- Financial Service OpenAPI: http://localhost:8081/swagger-ui.html
- RabbitMQ Management: http://localhost:15672 (paybot/paybot123)

---

## Running the Application (Claude Workflow)

When asked to "run the application", "start the app", or similar:

### 1. Pre-flight Checks (parallel)
- `docker ps` - verify Docker is running
- Check ports 8080, 8081, 4200, 5432, 5672, 6379 are available
- Verify `credentials/` directory exists with service account JSON

### 2. Start Application
```bash
docker-compose up --build -d
```

### 3. Monitor Startup (3 min timeout)
```bash
docker-compose logs -f --tail=100
```

Watch for:
- "Started PayBotApplication" in `paybot-ai-service` (success)
- "Started FinancialServiceApplication" in `paybot-financial-service` (success)
- Error patterns (see below)

### 4. Health Verification
```bash
curl http://localhost:8080/api/health
curl http://localhost:8081/api/internal/bills/health
docker-compose ps
```

Expect 6 healthy containers: `postgres`, `rabbitmq`, `redis`, `paybot-financial-service`, `paybot-ai-service`, `frontend`.

### 5. Report Status
Generate a report with:
- Overall status (success/failure)
- Services status (postgres, rabbitmq, redis, financial-service, ai-service, frontend)
- Any errors found with proposed fixes

### 6. Error Handling
If errors found, **propose fixes but do NOT auto-apply**. Wait for user approval.

### Common Error Patterns

| Error Pattern | Detection | Proposed Fix |
|--------------|-----------|--------------|
| `Connection refused: postgres` | Log contains "Connection refused" + "5432" | Increase healthcheck retries |
| `Table not found` | "relation X does not exist" | Check Flyway migrations in financial service |
| `Bean creation error` | "BeanCreationException" | Check @Component annotations |
| `Port already in use` | "Address already in use" | Kill process or change port |
| `API key not set` | "API key" error | Set GEMINI_API_KEY env var |
| `Build failed` | Maven/npm errors | Parse and suggest fix |
| `Connection refused: financial service` | AI service can't reach :8081 | Check financial service health, `depends_on` order |
| `Queue args mismatch` | "inequivalent arg 'x-dead-letter-exchange'" | `docker-compose down -v` to reset queues |

### Fix Workflow (after user approval)
1. Stop containers: `docker-compose down`
2. Apply approved fixes
3. Rebuild: `docker-compose up --build -d`
4. Re-verify health
5. Report results

## Environment Variables

| Variable | Service | Description |
|----------|---------|-------------|
| `GOOGLE_APPLICATION_CREDENTIALS` | ai-service | Path to Gemini service account JSON |
| `FINANCIAL_SERVICE_URL` | ai-service | URL of financial service (default: `http://paybot-financial-service:8081`) |
| `SPRING_DATASOURCE_URL` | financial-service | PostgreSQL JDBC URL |
| `SPRING_RABBITMQ_HOST` | both | RabbitMQ hostname |
| `SPRING_DATA_REDIS_HOST` | ai-service | Redis hostname |

## Key Technologies

- **AI Service**: Spring Boot 4.0.3, Java 17, Spring AI 2.0.0-M2, RabbitMQ, Redis, WebSocket
- **Financial Service**: Spring Boot 4.0.3, Java 17, PostgreSQL 16, Flyway, RabbitMQ, springdoc-openapi
- **Shared Library**: `paybot-shared` JAR (DTOs, enums, saga events)
- **Frontend**: Angular 18, Standalone Components, Signals
- **LLM**: Google Gemini 2.0 Flash via Spring AI

## Project Structure

### Shared Library (`paybot-shared/`)
```
src/main/java/com/agile/paybot/shared/
├── dto/
│   ├── BillDTO.java, PaymentResultDTO.java, ScheduledPaymentDTO.java
│   ├── MessageDTO.java, ChatRequest.java, ChatResponse.java
├── enums/
│   ├── BillStatus.java, ScheduledPaymentStatus.java
└── event/
    ├── PaymentCommandEvent.java   # AI → Financial (saga command)
    └── PaymentResultEvent.java    # Financial → AI (saga result)
```

### AI Service (`paybot-ai-service/`)
```
src/main/java/com/agile/paybot/
├── PayBotApplication.java          # Main entry
├── config/
│   ├── ChatQueueConfig.java        # Chat + saga RabbitMQ queues
│   ├── CorsConfig.java             # CORS for localhost:4200
│   ├── RedisConfig.java            # Redis + ObjectMapper
│   └── WebSocketConfig.java        # STOMP WebSocket
├── controller/
│   ├── ChatController.java         # POST /api/chat, GET /api/health
│   └── GlobalExceptionHandler.java
├── function/
│   └── PayBotTools.java            # @Tool methods (REST + saga commands)
├── listener/
│   ├── ChatTaskListener.java       # Chat queue consumer (idempotent)
│   └── PaymentResultListener.java  # Saga result → WebSocket push
└── service/
    ├── ChatService.java            # Gemini orchestration + Redis history
    └── FinancialServiceClient.java # REST client to financial service
```

### Financial Service (`paybot-financial-service/`)
```
src/main/java/com/agile/paybot/financial/
├── FinancialServiceApplication.java  # Main entry, @EnableScheduling
├── config/
│   ├── FinancialQueueConfig.java     # Saga RabbitMQ queues
│   ├── FlywayConfig.java            # Database migrations
│   └── DataInitializer.java         # Seeds sample bills (non-docker)
├── controller/
│   ├── InternalBillController.java   # GET /api/internal/bills
│   └── InternalPaymentController.java # POST /api/internal/payments
├── domain/entity/
│   ├── Bill.java, Payment.java, ScheduledPayment.java
├── exception/
│   ├── BillNotFoundException.java, BillAlreadyPaidException.java
├── listener/
│   └── PaymentCommandListener.java   # Saga participant (DB idempotent)
├── repository/
│   ├── BillRepository.java, PaymentRepository.java, ScheduledPaymentRepository.java
├── scheduler/
│   └── ScheduledPaymentExecutor.java # Background job (5 min)
└── service/
    ├── BillService.java, PaymentService.java, ScheduledPaymentService.java
```

### Frontend (`paybot-ui/`)
```
src/app/
├── core/services/
│   ├── chat.service.ts          # HTTP calls to /api/chat
│   └── message-store.service.ts # State management (Signals)
└── features/chat/
    ├── chat-page.component.ts   # Main container
    ├── message-list/            # Conversation display
    └── chat-input/              # User input
```

## Spring AI Integration

PayBot uses the `@Tool` annotation pattern for Gemini function calling:

```java
@Component
public class PayBotTools {
    @Tool(description = "Get user's bills...")
    public String getBills(@ToolParam(description = "...") String billType) {
        // Calls FinancialServiceClient (REST)
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

## RabbitMQ Queues

| Queue | Exchange | Routing Key | Purpose |
|-------|----------|-------------|---------|
| `chat.requests` | `chat.exchange` | `chat.request` | Chat messages → AI processing |
| `chat.requests.error` | `chat.requests.dlx` | `error` | DLQ for poison pills |
| `financial.payment.command` | `financial.exchange` | `payment.command` | AI → Financial (pay bill) |
| `financial.payment.result` | `financial.exchange` | `payment.result` | Financial → AI (confirmation) |

## Database

PostgreSQL 16 with Flyway-managed schema (owned by `paybot-financial-service`):
- `bills` - User bills
- `payments` - Payment records (with `request_id` for idempotency)
- `scheduled_payments` - Scheduled payment jobs
- `flyway_schema_history` - Migration tracking

Access PostgreSQL:
```bash
docker exec -it paybot-postgres psql -U paybot -d paybotdb
```

## Testing

### Manual Testing (curl)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Show me my bills","requestId":"test-1","sessionId":"sess-1","conversationHistory":[]}'
```

### Verify saga queues:
```bash
docker exec paybot-rabbitmq rabbitmqctl list_queues name messages consumers
```

### Frontend
```bash
cd paybot-ui
npm test
```

## Code Conventions

### Backend (both services)
- Shared DTOs/enums/events live in `paybot-shared` — never duplicate
- Use DTOs at controller/presentation layer
- Use entities only within service layer
- Services should call other services, not repositories directly (except their own)
- Lombok for boilerplate reduction (@RequiredArgsConstructor, @Slf4j)
- Inter-service communication: REST for queries, RabbitMQ for commands (saga)

### Frontend
- Standalone components (no NgModules)
- Signals for state management
- Services in `core/services/`
- Feature components in `features/`

## Common Tasks

### Adding a New Tool
1. Add method to `paybot-ai-service/.../PayBotTools.java` with `@Tool` annotation
2. Update `SYSTEM_PROMPT` in `ChatService.java`
3. For sync tools: add method to `FinancialServiceClient` + endpoint in financial service
4. For async tools: create new event in `paybot-shared`, publish/consume via RabbitMQ

### Adding a New Entity
1. Create entity in `paybot-financial-service/.../domain/entity/`
2. Create DTO in `paybot-shared/.../dto/`
3. Create repository in `paybot-financial-service/.../repository/`
4. Create service in `paybot-financial-service/.../service/`
5. Add Flyway migration in `paybot-financial-service/src/main/resources/db/migration/`
6. Expose REST endpoint in `paybot-financial-service/.../controller/`
7. Add `FinancialServiceClient` method in AI service if needed
8. Run `mvn clean install` in `paybot-shared` first if DTOs changed

### Adding a New Saga Flow
1. Create command + result event records in `paybot-shared/.../event/`
2. Add queue/binding declarations in both services' queue configs
3. Create listener in financial service (saga participant)
4. Create result listener in AI service (WebSocket push)
5. Publish command from `PayBotTools` or relevant AI service component
