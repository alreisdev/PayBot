# PayBot - Gemini (Antigravity) Guide

This guide provides project-specific instructions, workflows, and conventions for the PayBot application, optimized for the Gemini (Antigravity) AI assistant.

## Architecture & System Overview

PayBot is a microservices-based AI payment assistant.
- **`paybot-ai-service`** (:8080): Spring AI + Google Gemini + WebSocket. Orchestrates chat and handles LLM tool calls.
- **`paybot-financial-service`** (:8081): PostgreSQL + JPA. Manages bills, payments, and scheduling.
- **`paybot-ui`** (:4200): Angular 18 frontend.
- **Messaging**: RabbitMQ (Choreography Saga pattern for async payments).
- **Cache**: Redis (Idempotency + Chat History).
- **Tracing**: Zipkin (Local) / Cloud Trace (GCP).

## Development Workflow

### 1. Starting the Application
Use the established Docker Compose setup for a consistent environment.

```bash
# Clean restart (resets database and queues)
docker-compose down -v
docker-compose up --build -d
```

### 2. Monitoring & Health
Verify the following endpoints are UP:
- **AI Service**: [http://localhost:8080/api/health](http://localhost:8080/api/health)
- **Financial Service**: [http://localhost:8081/api/internal/bills/health](http://localhost:8081/api/internal/bills/health)
- **RabbitMQ Management**: [http://localhost:15672](http://localhost:15672) (paybot/paybot123)

### 3. Critical Fixes Applied
- **Port 8081** is now exposed in `docker-compose.yml` to allow direct access to Financial Service health and Swagger UI.

## Gemini Commands & Workflows

### `/init`
Initializes the workspace for Gemini, ensuring environment variables and services are ready.
- Check `.env` file for credentials.
- Verify Docker status.
- Ensure `credentials/paybot-credentials.json` exists for Gemini API access.

### `/run`
Starts the entire stack and monitors logs for "Started PayBotApplication".

### `/test`
Runs unit and integration tests for both services.
```bash
# AI Service
cd paybot-ai-service && ./mvnw test
# Financial Service (requires Docker for Testcontainers)
cd paybot-financial-service && ./mvnw verify
```

## AI Tool Integration

Gemini uses the `@Tool` annotation in `paybot-ai-service/.../function/PayBotTools.java`.
- **Sync Tools (REST)**: Calls `FinancialClient` (Feign).
- **Async Tools (Saga)**: Publishes events to RabbitMQ.

### Adding a New Tool Pattern:
1. Define event in `paybot-shared`.
2. Add `@Tool` method in `PayBotTools.java`.
3. Update `SYSTEM_PROMPT` in `ChatService.java`.
4. Implement Saga participant in `paybot-financial-service`.

## Code Conventions

- **Shared Logic**: Always put DTOs, Enums, and Events in `paybot-shared`. Run `mvn clean install` after changes there.
- **Resilience**: Every `OpenFeign` client must have a `fallback` implementation.
- **Idempotency**: Use `requestId` from the frontend to guard against double payments in the `ChatTaskListener` (Redis + DB Double-Check).
- **Frontend**: Use Angular Signals for state management and Standalone Components.

## Directory Structure

```text
/paybot-shared            # Shared DTOs and Saga Events
/paybot-ai-service        # AI orchestration (:8080)
/paybot-financial-service # Database & Business Logic (:8081)
/paybot-ui                # Angular 18 SPA (:4200)
/terraform                # GCP Infrastructure (GKE, Cloud SQL)
/k8s                      # Kubernetes Manifests
```
