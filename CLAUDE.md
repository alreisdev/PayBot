# PayBot - Conversational Bill Payment Chatbot

A natural language chatbot for managing and paying bills, powered by Google Gemini with Spring AI function calling.

## Architecture

```
Angular Frontend  -->  Spring Boot Backend  -->  Gemini LLM
     (Chat UI)           (REST API)           (Function Calling)
                              |
                         H2 Database
                        (Bills/Payments)
```

## Quick Start

### Backend (Spring Boot)
```bash
cd JavaPayBotService
set GEMINI_API_KEY=your-api-key-here
mvn spring-boot:run
```
Backend runs on: http://localhost:8080

### Frontend (Angular)
```bash
cd paybot-ui
npm install
npm start
```
Frontend runs on: http://localhost:4200

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `GEMINI_API_KEY` | Yes | Google Gemini API key |

## Key Technologies

- **Backend**: Spring Boot 4.0.3, Java 17, Spring AI 1.1.1
- **Frontend**: Angular 18, Standalone Components, Signals
- **LLM**: Google Gemini 2.0 Flash via Spring AI
- **Database**: H2 in-memory (auto-seeded with sample bills)

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
| GET | `/h2-console` | H2 database console |

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

H2 in-memory database with auto-created tables:
- `bills` - User bills
- `payments` - Payment records
- `scheduled_payments` - Scheduled payment jobs

Access H2 Console: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:paybotdb`
- Username: `sa`
- Password: (empty)

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