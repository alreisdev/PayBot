# PayBot - AI-Powered Bill Payment Chatbot

A conversational chatbot for managing and paying bills using natural language, powered by Google Gemini.

## Architecture

```
┌─────────────────┐  POST /api/chat  ┌──────────────────┐
│  Angular UI     │─────────────────▶│  Spring Boot API │
│  (Chat Interface)│   202 Accepted   │  (REST + STOMP)  │
│  localhost:4200 │                  └────────┬─────────┘
└────────┬────────┘                           │
         │                           ┌────────▼─────────┐
         │  WebSocket                │    RabbitMQ      │
         │  /topic/messages          │  (Message Queue) │
         │                           └────────┬─────────┘
         │                                    │
         │                           ┌────────▼─────────┐
         │◀──────────────────────────│  ChatTaskListener │
         │                           │  (Async Worker)  │
                                     └────────┬─────────┘
                                              │
                                     ┌────────▼─────────┐
                                     │   Gemini LLM     │
                                     │  (Function Calling)│
                                     └────────┬─────────┘
                                              │
                                     ┌────────▼─────────┐
                                     │   PostgreSQL     │
                                     │  (Bills/Payments)│
                                     └──────────────────┘
```

**Async Flow:**
1. User sends message via REST (`POST /api/chat`)
2. Backend queues request in RabbitMQ and returns `202 Accepted`
3. `ChatTaskListener` processes the message with Gemini LLM
4. Response sent to frontend via WebSocket (`/topic/messages`)

## Prerequisites

- **Docker & Docker Compose** (recommended) OR
- **Java 17+** (Amazon Corretto, OpenJDK, etc.)
- **Node.js 18+** and npm (for Angular frontend)
- **Gemini API Key** - Get one free from [Google AI Studio](https://aistudio.google.com/apikey)

## Quick Start with Docker (Recommended)

```bash
# Set your Gemini API key
set GEMINI_API_KEY=your-api-key-here   # Windows CMD
$env:GEMINI_API_KEY="your-api-key"     # PowerShell
export GEMINI_API_KEY="your-api-key"   # Linux/Mac

# Build and run
docker-compose up --build
```

Access the app at **http://localhost:4200**

The Docker setup includes:
- **Backend**: Spring Boot on port 8080
- **Frontend**: Angular served via Nginx on port 4200
- **Database**: PostgreSQL 16 with persistent volume
- **Message Broker**: RabbitMQ with management UI on port 15672
- Health checks and automatic service dependencies
- Nginx proxy routing `/api/*` and `/ws-paybot` (WebSocket) to the backend
- Flyway database migrations run automatically on startup

### Docker Commands

```bash
# Start in background
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Rebuild after code changes
docker-compose up --build

# Access PostgreSQL database
docker exec -it paybot-postgres psql -U paybot -d paybotdb

# View tables
docker exec -it paybot-postgres psql -U paybot -d paybotdb -c "\dt"

# Access RabbitMQ Management UI
# Open http://localhost:15672 (login: paybot / paybot123)
```

> **Data Persistence**: The PostgreSQL data is stored in a Docker volume (`postgres_data`). Your data persists across container restarts. Use `docker-compose down -v` to delete the volume and reset the database.

## Quick Start (Manual)

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

> **Note**: Manual setup requires:
> - PostgreSQL running on port 5432 with database `paybotdb`
> - RabbitMQ running on port 5672
>
> For easier setup, use Docker instead.

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
├── docker-compose.yml           # Container orchestration (PostgreSQL, RabbitMQ, Backend, Frontend)
├── JavaPayBotService/           # Spring Boot Backend
│   ├── Dockerfile               # Multi-stage build
│   ├── .dockerignore
│   ├── src/main/java/com/agile/paybot/
│   │   ├── PayBotApplication.java
│   │   ├── config/
│   │   │   ├── ChatQueueConfig.java   # RabbitMQ queue/exchange setup
│   │   │   ├── WebSocketConfig.java   # STOMP WebSocket config
│   │   │   └── CorsConfig.java        # CORS settings
│   │   ├── controller/
│   │   │   ├── ChatController.java    # POST /api/chat (queues to RabbitMQ)
│   │   │   └── BillController.java    # GET /api/bills (debug)
│   │   ├── listener/
│   │   │   └── ChatTaskListener.java  # @RabbitListener (async processing)
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
│       ├── application.properties
│       └── db/migration/            # Flyway SQL migrations
│           ├── V1__Initial_Schema.sql
│           └── V2__Seed_Data.sql
│
└── paybot-ui/                   # Angular Frontend
    ├── Dockerfile               # Multi-stage build
    ├── .dockerignore
    ├── nginx.conf               # Nginx config (SPA + WebSocket proxy)
    └── src/app/
        ├── core/services/
        │   ├── chat.service.ts        # HTTP + WebSocket integration
        │   ├── websocket.service.ts   # STOMP WebSocket client
        │   └── message-store.service.ts
        └── features/chat/       # Chat components
```

## API Endpoints

### Chat Endpoint (Async)
```
POST /api/chat
Content-Type: application/json

{
  "message": "Show me my bills",
  "conversationHistory": []
}
```

### Response (Immediate)
```json
HTTP 202 Accepted
{
  "status": "Request accepted for processing"
}
```

### WebSocket Response (Async)
Connect to `/ws-paybot` and subscribe to `/topic/messages`:
```json
{
  "message": {
    "role": "assistant",
    "content": "Here are your unpaid bills..."
  },
  "metadata": {
    "model": "gemini-2.0-flash"
  }
}
```

### Health Check
```
GET /api/health
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| Backend | Spring Boot 4.0.3, Java 17 |
| LLM Integration | Spring AI 2.0.0-M2 + Google GenAI |
| Database | PostgreSQL 16 (Flyway migrations) |
| Message Broker | RabbitMQ 3 (async processing) |
| Real-time | WebSocket with STOMP over SockJS |
| Frontend | Angular 18 |
| Styling | SCSS |
| Containerization | Docker, Docker Compose |
| Web Server | Nginx (production) |

## Key Learnings

This project demonstrates:

1. **LLM Function Calling** - How to let an LLM call backend functions
2. **Spring AI** - Integrating AI into Spring applications
3. **Async Messaging** - Decoupling request/response with RabbitMQ
4. **WebSocket Communication** - Real-time responses with STOMP
5. **Conversational State** - Managing chat history across requests
6. **Tool/Function Design** - Defining clear tool contracts for LLMs
7. **Full-Stack Architecture** - Angular + Spring Boot + RabbitMQ + AI

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
