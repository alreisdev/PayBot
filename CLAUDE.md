# PayBot - Conversational Bill Payment Chatbot

A natural language chatbot for managing and paying bills, powered by Google Gemini with Spring AI function calling.

## Architecture

```
Angular Frontend  -->  Spring Boot Backend  -->  Gemini LLM
     (Chat UI)           (REST API)           (Function Calling)
                              |
                         PostgreSQL
                        (Bills/Payments)
```

## Quick Start (Docker - Recommended)

```bash
docker-compose up --build
```

Access:
- Frontend: http://localhost:4200
- Backend API: http://localhost:8080
- Health check: http://localhost:8080/api/health

### Manual Start (without Docker)

**Backend:**
```bash
cd JavaPayBotService
set GEMINI_API_KEY=your-api-key-here
mvn spring-boot:run
```

**Frontend:**
```bash
cd paybot-ui
npm install
npm start
```

---

## Running the Application (Claude Workflow)

When asked to "run the application", "start the app", or similar:

### 1. Pre-flight Checks (parallel)
- `docker ps` - verify Docker is running
- Check ports 8080, 4200, 5432 are available
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
- "Started PayBotApplication" (success)
- Error patterns (see below)

### 4. Health Verification
```bash
curl http://localhost:8080/api/health
docker-compose ps
```

### 5. Report Status
Generate a report with:
- Overall status (success/failure)
- Services status (postgres, backend, frontend)
- Any errors found with proposed fixes

### 6. Error Handling
If errors found, **propose fixes but do NOT auto-apply**. Wait for user approval.

### Common Error Patterns

| Error Pattern | Detection | Proposed Fix |
|--------------|-----------|--------------|
| `Connection refused: postgres` | Log contains "Connection refused" + "5432" | Increase healthcheck retries |
| `Table not found` | "relation X does not exist" | Check Flyway migrations |
| `Bean creation error` | "BeanCreationException" | Check @Component annotations |
| `Port already in use` | "Address already in use" | Kill process or change port |
| `API key not set` | "API key" error | Set GEMINI_API_KEY env var |
| `Build failed` | Maven/npm errors | Parse and suggest fix |

### Fix Workflow (after user approval)
1. Stop containers: `docker-compose down`
2. Apply approved fixes
3. Rebuild: `docker-compose up --build -d`
4. Re-verify health
5. Report results

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GEMINI_API_KEY` | Yes | Google Gemini API key |

## Key Technologies

- **Backend**: Spring Boot 4.0.3, Java 17, Spring AI 2.0.0-M2
- **Frontend**: Angular 18, Standalone Components, Signals
- **LLM**: Google Gemini 2.0 Flash via Spring AI
- **Database**: PostgreSQL 16 with Flyway migrations

## Project Structure

### Backend (`JavaPayBotService/`)
```
src/main/java/com/agile/paybot/
├── PayBotApplication.java       # Main entry, @EnableScheduling
├── config/
│   ├── CorsConfig.java          # CORS for localhost:4200
│   └── DataInitializer.java     # Seeds sample bills
├── controller/
│   └── ChatController.java      # POST /api/chat endpoint
├── domain/
│   ├── entity/                  # Bill, Payment, ScheduledPayment
│   └── dto/                     # ChatRequest, ChatResponse, etc.
├── function/
│   └── PayBotTools.java         # @Tool methods for Gemini
├── repository/                  # JPA repositories
├── scheduler/
│   └── ScheduledPaymentExecutor.java  # Background job (5 min)
└── service/                     # Business logic layer
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
        // Implementation
    }
}
```

Tools are registered with ChatClient via `.tools(payBotTools)`.

### Available Tools
| Tool | Purpose |
|------|---------|
| `getBills` | List bills (filter by type/month) |
| `getBillDetails` | Get specific bill details |
| `processPayment` | Pay a bill immediately |
| `schedulePayment` | Schedule future payment |
| `getScheduledPayments` | View scheduled payments |
| `cancelScheduledPayment` | Cancel pending payment |

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat` | Send chat message |
| GET | `/api/health` | Health check |
| GET | `/api/bills` | Debug: list all bills |

### Chat Request Format
```json
{
  "message": "Show me my bills",
  "conversationHistory": [
    {"role": "user", "content": "Hello"},
    {"role": "assistant", "content": "Hi! How can I help?"}
  ]
}
```

## Database

PostgreSQL 16 with Flyway-managed schema:
- `bills` - User bills
- `payments` - Payment records
- `scheduled_payments` - Scheduled payment jobs
- `flyway_schema_history` - Migration tracking

Access PostgreSQL:
```bash
docker exec -it paybot-postgres psql -U paybot -d paybotdb
```

## Testing

### Backend
```bash
cd JavaPayBotService
mvn test
```

### Frontend
```bash
cd paybot-ui
npm test
```

### Manual Testing (curl)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Show me my bills","conversationHistory":[]}'
```

## Code Conventions

### Backend
- Use DTOs at controller/presentation layer
- Use entities only within service layer
- Services should call other services, not repositories directly (except their own)
- Lombok for boilerplate reduction (@RequiredArgsConstructor, @Slf4j)

### Frontend
- Standalone components (no NgModules)
- Signals for state management
- Services in `core/services/`
- Feature components in `features/`

## Common Tasks

### Adding a New Tool
1. Add method to `PayBotTools.java` with `@Tool` annotation
2. Update `SYSTEM_PROMPT` in `ChatService.java`
3. Implement supporting service methods if needed

### Adding a New Entity
1. Create entity in `domain/entity/`
2. Create DTO in `domain/dto/`
3. Create repository in `repository/`
4. Create service in `service/`
5. Add seed data in `DataInitializer.java` if needed