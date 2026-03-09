# PayBot - AI-Powered Bill Payment Chatbot

A conversational chatbot for managing and paying bills using natural language, powered by Google Gemini.

## Architecture

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────┐
│  Angular UI     │────▶│  Spring Boot API │────▶│  Gemini LLM │
│  (Chat Interface)│     │  (REST + Tools)  │     │  (Function  │
│  localhost:4200 │◀────│  localhost:8080  │◀────│   Calling)  │
└─────────────────┘     └────────┬─────────┘     └─────────────┘
                                 │
                        ┌────────▼─────────┐
                        │   H2 Database    │
                        │  (Bills/Payments)│
                        └──────────────────┘
```

## Prerequisites

- **Java 17+** (Amazon Corretto, OpenJDK, etc.)
- **Node.js 18+** and npm (for Angular frontend)
- **Gemini API Key** - Get one free from [Google AI Studio](https://aistudio.google.com/apikey)

## Quick Start

### 1. Set up Gemini API Key

```bash
# Windows (PowerShell)
$env:GEMINI_API_KEY="your-api-key-here"

# Windows (CMD)
set GEMINI_API_KEY=your-api-key-here

# Linux/Mac
export GEMINI_API_KEY="your-api-key-here"
```

### 2. Start the Backend

```bash
cd JavaPayBotService
./mvnw spring-boot:run
```

The backend will start at `http://localhost:8080`

- Health check: `GET http://localhost:8080/api/health`
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:paybotdb`)

### 3. Start the Frontend

```bash
cd paybot-ui
npm install
ng serve
```

The frontend will start at `http://localhost:4200`

## Sample Conversations

Try these prompts:

- "Show me my bills"
- "List my bills for this month"
- "I want to pay my electric bill"
- "How much is my internet bill?"
- "Pay my water bill"

## Project Structure

```
Paybot/
├── JavaPayBotService/           # Spring Boot Backend
│   ├── src/main/java/com/agile/paybot/
│   │   ├── PayBotApplication.java
│   │   ├── controller/
│   │   │   ├── ChatController.java    # POST /api/chat
│   │   │   └── BillController.java    # GET /api/bills (debug)
│   │   ├── service/
│   │   │   ├── ChatService.java       # LLM orchestration
│   │   │   ├── BillService.java       # Bill operations
│   │   │   └── PaymentService.java    # Payment processing
│   │   ├── function/
│   │   │   └── PayBotTools.java       # @Tool methods for Gemini
│   │   └── domain/
│   │       ├── entity/                # JPA entities
│   │       └── dto/                   # Data transfer objects
│   └── src/main/resources/
│       └── application.properties
│
└── paybot-ui/                   # Angular Frontend
    └── src/app/
        ├── core/services/       # ChatService, MessageStore
        └── features/chat/       # Chat components
```

## API Endpoints

### Chat Endpoint
```
POST /api/chat
Content-Type: application/json

{
  "message": "Show me my bills",
  "conversationHistory": []
}
```

### Response
```json
{
  "message": {
    "role": "assistant",
    "content": "Here are your unpaid bills..."
  },
  "metadata": {
    "model": "gemini-1.5-pro"
  }
}
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Spring Boot 4.0.3, Java 17 |
| LLM Integration | Spring AI 1.1.1 + Google GenAI |
| Database | H2 (in-memory) |
| Frontend | Angular 18 |
| Styling | SCSS |

## Key Learnings

This project demonstrates:

1. **LLM Function Calling** - How to let an LLM call backend functions
2. **Spring AI** - Integrating AI into Spring applications
3. **Conversational State** - Managing chat history across requests
4. **Tool/Function Design** - Defining clear tool contracts for LLMs
5. **Full-Stack Architecture** - Angular + Spring Boot + AI

## Troubleshooting

### "GEMINI_API_KEY not set"
Ensure you've set the environment variable before starting the backend.

### Build fails with dependency errors
```bash
cd JavaPayBotService
./mvnw clean install -U
```

### CORS errors
The backend is configured to allow requests from `http://localhost:4200`. If using a different port, update `CorsConfig.java`.

## License

For learning purposes only.
