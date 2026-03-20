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

## GCP Deployment

PayBot includes Terraform infrastructure-as-code and Kubernetes manifests for deploying to Google Cloud Platform.

### GCP Architecture

```
                          ┌─────────────────────────────────────────────┐
                          │                GKE Cluster                  │
  Internet ──▶ Ingress ──▶│  ┌─────────────┐   ┌──────────────────┐   │
                          │  │  AI Service  │──▶│ Financial Service │   │
                          │  │  (2 pods)    │   │ (2 pods)         │   │
                          │  └──────┬───────┘   └────────┬─────────┘   │
                          │         │                    │             │
                          │  ┌──────▼───────┐            │             │
                          │  │  RabbitMQ    │◀───────────┘             │
                          │  │ (StatefulSet)│                          │
                          │  └──────────────┘                          │
                          └─────────┬──────────────┬───────────────────┘
                                    │              │
                          ┌─────────▼──────┐ ┌─────▼──────────┐
                          │  Memorystore   │ │   Cloud SQL    │
                          │  (Redis 7)     │ │ (PostgreSQL 16)│
                          └────────────────┘ └────────────────┘
```

### Infrastructure (Terraform)

The `terraform/` directory provisions:

| Resource | Description |
|----------|-------------|
| **GKE Cluster** | Standard cluster with Workload Identity, private nodes, autoscaling 2-5 `e2-medium` nodes |
| **Cloud SQL** | PostgreSQL 16, private IP, daily backups |
| **Memorystore** | Redis 7 BASIC tier, private service access |
| **VPC Network** | Custom VPC with pod/service secondary ranges, private service connection |
| **IAM** | `paybot-sa` service account with Gemini, Secret Manager, Cloud Trace, Cloud SQL roles |
| **Secret Manager** | Secrets for DB password, RabbitMQ password, Redis password, Gemini credentials |

```bash
cd terraform

# Configure variables
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with your project_id and passwords

# Provision infrastructure
terraform init
terraform plan
terraform apply
```

### Kubernetes Manifests

The `k8s/` directory contains deployment manifests:

```
k8s/
├── namespace.yaml              # paybot namespace
├── service-account.yaml        # Workload Identity binding
├── configmap.yaml              # Shared config (profile, Cloud SQL IP, Memorystore host)
├── otel-collector-config.yaml  # OpenTelemetry Collector sidecar config (OTLP → Cloud Trace)
├── db-secret.yaml              # Cloud SQL password
├── rabbitmq/                   # StatefulSet + Service + Secret
├── financial-service/          # Deployment (1 replica + OTel sidecar) + ClusterIP Service
├── ai-service/                 # Deployment (1 replica + OTel sidecar) + ClusterIP Service
└── frontend/                   # Deployment + Service + Ingress
```

**Deploy to GKE:**

```bash
# Get cluster credentials
gcloud container clusters get-credentials paybot-cluster --region us-central1

# Update configmap with Terraform outputs
# Replace REPLACE_WITH_TERRAFORM_OUTPUT in k8s/configmap.yaml with:
#   CLOUDSQL_PRIVATE_IP: $(terraform -chdir=terraform output -raw cloudsql_private_ip)
#   MEMORYSTORE_HOST:    $(terraform -chdir=terraform output -raw memorystore_host)

# Replace PROJECT_ID in service-account.yaml and deployment image references

# Build and push images
docker build -t gcr.io/PROJECT_ID/paybot-ai-service -f paybot-ai-service/Dockerfile .
docker build -t gcr.io/PROJECT_ID/paybot-financial-service -f paybot-financial-service/Dockerfile .
docker build -t gcr.io/PROJECT_ID/paybot-frontend -f paybot-ui/Dockerfile .
docker push gcr.io/PROJECT_ID/paybot-ai-service
docker push gcr.io/PROJECT_ID/paybot-financial-service
docker push gcr.io/PROJECT_ID/paybot-frontend

# Apply manifests in order
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/service-account.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/otel-collector-config.yaml
kubectl apply -f k8s/db-secret.yaml
kubectl apply -f k8s/rabbitmq/
kubectl apply -f k8s/financial-service/
kubectl apply -f k8s/ai-service/
kubectl apply -f k8s/frontend/

# Verify
kubectl get pods -n paybot
kubectl get services -n paybot
```

### Spring Profiles

| Profile | Activated By | Purpose |
|---------|-------------|---------|
| *(default)* | No profile set | Local development (localhost connections) |
| `docker` | Docker Compose | Docker networking (container hostnames) |
| `gcp` | K8s ConfigMap | GKE deployment (Cloud SQL, Memorystore, Cloud Trace) |

**GCP profile differences:**
- Database connects to Cloud SQL via private IP
- Redis connects to Memorystore via private IP
- RabbitMQ connects to in-cluster StatefulSet
- Tracing exports via OTLP to OTel Collector sidecar → Google Cloud Trace (W3C propagation) instead of Zipkin (B3)
- Secrets injected as env vars from K8s Secrets (synced from GCP Secret Manager)
- Actuator health probes enabled for K8s liveness/readiness
- Sampling reduced to 10% for production

## Distributed Tracing

Both backend services export traces, allowing you to visualize request flows across the system.

### Local (Zipkin)

Access the Zipkin UI at **http://localhost:9411**

- **Trace timeline** -- full request flow across both services
- **Span details** -- latency per service hop, including Feign REST calls and RabbitMQ messaging
- **Dependency graph** -- auto-generated service topology

**How to use:**
1. Send a chat message via the UI at http://localhost:4200
2. Open Zipkin at http://localhost:9411
3. Select a service from the dropdown, click **Run Query**
4. Click a trace to see the full span waterfall

### GCP (Cloud Trace via OpenTelemetry Collector)

When running with the `gcp` profile, traces export to Google Cloud Trace using the **OpenTelemetry Collector sidecar pattern** — the industry standard for tracing in GKE:

```
┌─────────────────────────────────────────────┐
│  Pod                                        │
│  ┌──────────────┐    ┌───────────────────┐  │
│  │ Spring Boot  │───▶│ OTel Collector    │  │
│  │ (OTLP gRPC)  │    │ (sidecar)         │  │
│  └──────────────┘    └───────┬───────────┘  │
└──────────────────────────────┼──────────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Google Cloud Trace  │
                    └─────────────────────┘
```

Each backend pod includes an OTel Collector sidecar container (`otel/opentelemetry-collector-contrib`) that:
1. Receives OTLP traces from the app on `localhost:4317` (gRPC)
2. Batches and exports them to Google Cloud Trace via the `googlecloud` exporter
3. Uses Workload Identity for authentication (no credentials needed)

View traces in the GCP Console under **Trace > Trace list**.

**Why sidecar instead of direct export?** The Google Cloud Trace client library (`exporter-trace:0.31.0`) is incompatible with Spring Boot 4's OpenTelemetry version (missing `ResourceAttributes` class). The Collector pattern decouples the app from vendor-specific exporters — the app only uses the standard OTLP exporter.

### Tracing Configuration

The tracing pipeline is profile-conditional:

| Component | Local / Docker (`!gcp`) | GCP (`gcp` profile) |
|-----------|------------------------|---------------------|
| Exporter | `ZipkinSpanExporter` → Zipkin | `OtlpGrpcSpanExporter` → OTel Collector → Cloud Trace |
| Propagation | B3 (multi-header) | W3C TraceContext |
| Sampling | 100% | 10% |
| Config class | `ZipkinTracingConfig.java` | `GcpTracingConfig.java` |
| Collector config | — | `k8s/otel-collector-config.yaml` |

The base `TracingConfig.java` accepts a generic `SpanExporter` and `TextMapPropagator`, wired by the active profile config.

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
├── docker-compose.yml              # Container orchestration (local dev)
├── terraform/                      # GCP infrastructure (Terraform)
│   ├── main.tf                     # Provider, required APIs
│   ├── variables.tf                # Input variables
│   ├── network.tf                  # VPC, subnet, private service connection
│   ├── gke.tf                      # GKE cluster + node pool
│   ├── cloudsql.tf                 # Cloud SQL PostgreSQL 16
│   ├── memorystore.tf              # Memorystore Redis 7
│   ├── iam.tf                      # Service account + Workload Identity
│   ├── secretmanager.tf            # GCP Secret Manager secrets
│   └── outputs.tf                  # Terraform outputs
│
├── k8s/                            # Kubernetes manifests (GKE deployment)
│   ├── namespace.yaml
│   ├── service-account.yaml        # Workload Identity
│   ├── configmap.yaml
│   ├── otel-collector-config.yaml  # OTel Collector sidecar config
│   ├── db-secret.yaml              # Cloud SQL password
│   ├── rabbitmq/                   # StatefulSet + Service + Secret
│   ├── financial-service/          # Deployment + OTel sidecar + Service
│   ├── ai-service/                 # Deployment + OTel sidecar + Service
│   └── frontend/                   # Deployment + Service + Ingress
│
├── paybot-shared/                  # Shared library (DTOs, enums, saga events)
│   └── src/main/java/com/agile/paybot/shared/
│       ├── dto/                    # BillDTO, ChatRequest, ChatResponse, etc.
│       ├── enums/                  # BillStatus, ScheduledPaymentStatus
│       └── event/                  # Saga events (command + result)
│
├── paybot-ai-service/              # AI orchestration service (:8080)
│   ├── Dockerfile
│   └── src/main/java/com/agile/paybot/
│       ├── PayBotApplication.java  # Main entry (@EnableFeignClients)
│       ├── client/                 # OpenFeign client + Resilience4j fallback
│       ├── config/                 # Queue, CORS, Redis, WebSocket, Security, Tracing
│       ├── controller/             # ChatController, GlobalExceptionHandler
│       ├── function/               # PayBotTools (@Tool methods for Gemini)
│       ├── listener/               # Chat, Payment result, Schedule result listeners
│       └── service/                # ChatService (Gemini + Redis history)
│
├── paybot-financial-service/       # Financial data service (:8081)
│   ├── Dockerfile
│   └── src/main/java/com/agile/paybot/financial/
│       ├── FinancialServiceApplication.java  # Main entry (@EnableScheduling)
│       ├── config/                 # Queue, Flyway, Security, Tracing
│       ├── controller/             # InternalBillController, InternalPaymentController
│       ├── domain/entity/          # Bill, Payment, ScheduledPayment
│       ├── listener/               # Payment + Schedule saga participants
│       ├── repository/             # JPA repositories
│       ├── scheduler/              # ScheduledPaymentExecutor (5 min job)
│       └── service/                # BillService, PaymentService, ScheduledPaymentService
│
└── paybot-ui/                      # Angular frontend (:4200)
    ├── Dockerfile
    ├── nginx.conf                  # Nginx (SPA + API/WebSocket proxy)
    └── src/app/
        ├── core/services/          # chat.service.ts, message-store.service.ts
        └── features/chat/          # Chat page, message list, chat input components
```

## Tech Stack

| Component | Technology |
|-----------|------------|
| AI Service | Spring Boot 4.0.3, Java 17, Spring AI 2.0.0-M2 |
| LLM | Google Gemini 2.5 Flash via Spring AI |
| Inter-service REST | Spring Cloud OpenFeign + Resilience4j circuit breaker |
| Async Messaging | RabbitMQ 3 (Choreography Saga pattern) |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache/Sessions | Redis 7 (idempotency + chat history) |
| Real-time | WebSocket with STOMP over SockJS |
| Distributed Tracing | OpenTelemetry + Zipkin (local) / OTel Collector sidecar → Cloud Trace (GCP) |
| Security | Spring Security (endpoint authorization) |
| Frontend | Angular 18 (Standalone Components, Signals) |
| Containerization | Docker, Docker Compose |
| Infrastructure | Terraform (GKE, Cloud SQL, Memorystore, IAM) |
| Orchestration | Kubernetes (GKE) |
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
    "model": "gemini-2.5-flash",
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

### Integration Tests (Testcontainers)

The financial service includes integration tests that run against real PostgreSQL and RabbitMQ containers via Testcontainers. These verify the saga flows and idempotency logic end-to-end.

```bash
cd paybot-financial-service && ./mvnw verify
```

**Requires Docker running locally.** Test coverage includes:
- **PaymentSagaIntegrationTest** -- happy path, bill not found, bill already paid
- **PaymentIdempotencyIntegrationTest** -- duplicate requestId replay, separate processing
- **ScheduleSagaIntegrationTest** -- schedule happy path, duplicate requestId idempotency

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
| `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` | both | Zipkin span collector URL (local/docker only) |

### GCP-Specific Variables (set via K8s ConfigMap/Secrets)

| Variable | Service | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | both | Set to `gcp` for GKE deployment |
| `CLOUDSQL_PRIVATE_IP` | financial-service | Cloud SQL private IP (from Terraform) |
| `MEMORYSTORE_HOST` | ai-service | Memorystore Redis IP (from Terraform) |
| `DB_PASSWORD` | financial-service | Database password (from K8s Secret) |
| `RABBITMQ_PASSWORD` | both | RabbitMQ password (from K8s Secret) |

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
Both services use a profile-conditional `TracingConfig.java`. Verify:
1. Zipkin container is healthy: `docker ps --filter name=zipkin`
2. Send a request, wait 5-10 seconds, then check: `curl http://localhost:9411/api/v2/services`
3. Both `paybot-ai-service` and `paybot-financial-service` should appear in the response

### CORS errors
The AI service is configured to allow requests from `http://localhost:4200`. If using a different port, update `CorsConfig.java`.

### Spring Cloud GCP compatibility
Spring Cloud GCP 5.x is not compatible with Spring Boot 4.x. The `spring-cloud-gcp-starter-secretmanager` cannot be used. Instead, secrets are injected via K8s Secrets (synced from GCP Secret Manager using External Secrets Operator or similar).

## License

For learning purposes only.
