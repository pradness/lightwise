# Lightwise

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-green.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.0-blue.svg)](https://spring.io/projects/spring-cloud)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?logo=docker&logoColor=white)](https://docs.docker.com/compose/)
[![License](https://img.shields.io/badge/license-Educational-lightgrey.svg)]()

A **microservices reference implementation** for monitoring, reasoning about, and acting on household or facility electricity usage. The system accepts energy readings from devices, processes them asynchronously, stores time-series metrics, raises alerts when usage spikes, uses an AI agent to judge whether a spike is genuine overuse, and (with human approval) can shut off the offending device. All public HTTP traffic passes through a single **API Gateway** with **resilience**, **security**, and **observability** built in.

---

## Project overview

**Lightwise** models how a real product might collect **power (watts)** and **timestamps** from smart plugs or meters, aggregate that data for dashboards and billing-style views, notify residents when consumption crosses thresholds, and take supervised action when a device looks like it was left on by accident.

**Problem it solves:** Raw device events are high-volume and need reliable ingestion, decoupled processing, and specialized storage (relational metadata vs. time-series measurements). On top of that, a raw threshold breach on its own can't distinguish genuine overuse (a heater left on for hours) from normal cycling (a fridge compressor), and no automated system should physically act on a home without a human in the loop. Lightwise demonstrates all of this: HTTP APIs for users and devices, Kafka for event streaming, InfluxDB for usage series, MySQL for durable domain data, an LLM-backed agent for judgment calls, and a hard, code-level safety gate that holds regardless of what the agent or a human approver decides.

**Typical use cases:**

- Track **per-device** energy usage over time
- **Alert** when instantaneous or aggregated power exceeds a limit
- **Judge** whether an alert is genuine overuse or normal cycling, using an LLM with tool access to real usage and device data
- **Propose** shutting off a device, gated behind explicit human approval, never automatic
- **Block**, unconditionally, any shutoff attempt against a device flagged `never_shut_off` (medical equipment, servers, freezers, etc.), enforced in code, not just in a prompt
- **Gate** all public HTTP traffic through one entry point (API Gateway) with JWT validation
- **Observe** latency, errors, and circuit-breaker state with Prometheus and Grafana

---

## Architecture overview

The system is a **microservices architecture** built primarily with **Spring Boot 4** and **Java 21**. Services are independently deployable modules; integration uses **synchronous HTTP** (client → gateway → service) and **asynchronous messaging** (Kafka) where loose coupling and scale matter.

**Patterns and capabilities:**

| Area | Approach |
| ------ | ---------- |
| **API Gateway** | Spring Cloud Gateway (Server MVC); single public HTTP façade, route aggregation, OpenAPI aggregation |
| **Service communication** | REST between gateway and backends; Kafka for ingestion → usage → alerts → agent |
| **Agentic control loop** | `agent-service` consumes usage alerts, calls tools against usage-service and device-service, reasons via Spring AI, and only *proposes* an action, never executes it directly |
| **Safety gate** | `device-service` enforces the `never_shut_off` flag at the command endpoint itself, independent of the agent, the approval flow, or any human clicking the wrong button |
| **Resilience** | **Circuit breakers** (Resilience4j) on gateway routes with fallbacks |
| **Security** | **OAuth2 Resource Server** on the gateway; **Keycloak** for identity (dev profile in Docker Compose) |
| **Observability** | Spring Boot **Actuator**, **Micrometer**, **Prometheus** scrape targets, **Grafana** dashboards |
| **Configuration** | Per-service `application.properties` (no separate Spring Cloud Config Server in this repo) |

**High-level interaction:** Clients call the **API Gateway**. Domain services (**user**, **device**, **ingestion**, **insight**, **agent**) sit behind it. **Ingestion** publishes to Kafka; **usage** consumes, writes to **InfluxDB**, and publishes **alerts**; **alert-service** consumes alerts and notifies users by email (e.g. via **Mailpit** in local dev); **agent-service** also consumes alerts, gathers context via tool calls, and logs a reasoned proposal for human approval; **insight-service** provides plain-language, AI-backed explanations (Spring AI) of what happened, separate from the agent's decision to act.

---

## Services breakdown

| Service | Port | Responsibility | Key technologies | Interactions |
| --------- | ------ | ------------------ | ------------------ | -------------- |
| **api-gateway** | `9000` | Public entry: routing, circuit breaking, JWT validation, aggregated API docs | Spring Boot 4, Spring Cloud Gateway (WebMVC), Resilience4j, OAuth2 Resource Server, springdoc | Proxies to user, device, ingestion, insight, agent services; calls Keycloak JWKS |
| **user-service** | `8080` | User accounts and related persistence | Spring Boot 4, JPA, MySQL, Flyway, Actuator/Prometheus | MySQL; invoked via gateway |
| **device-service** | `8081` | Device registry / metadata, actuation endpoint, hard safety gate | Spring Boot 4, JPA, MySQL, Actuator/Prometheus | MySQL; invoked via gateway and by agent-service; logs every command attempt |
| **ingestion-service** | `8082` | Accept energy readings over HTTP and publish to streaming pipeline | Spring Boot 4, Kafka producer, Actuator/Prometheus | Produces to Kafka (`energy-usage`); invoked via gateway or directly for tests |
| **usage-service** | `8083` | Consume usage events, time-series storage, aggregation / threshold logic | Spring Boot 4, Kafka consumer/producer, InfluxDB Java client, Actuator/Prometheus | Kafka ↔ InfluxDB; produces alert events for downstream consumers |
| **alert-service** | `8084` | Consume alert events, notify users (e.g. email) | Spring Boot 4, Kafka, JPA, Mail, MySQL, Actuator/Prometheus | Kafka consumer; SMTP (Mailpit locally); MySQL where applicable |
| **insight-service** | `8085` | Usage insights (e.g. LLM-backed explanations via Ollama) | Spring Boot 3.5, Spring AI, Ollama starter, Actuator/Prometheus | Invoked via gateway; optional external Ollama runtime |
| **agent-service** | `8086` | Judges usage alerts via LLM tool-calling, proposes device actions, requires human approval before anything is executed | Spring Boot 3.5, Spring AI, Ollama starter, Kafka consumer, JPA, MySQL, Actuator/Prometheus | Consumes `usage-alerts`; calls usage-service and device-service as tools; writes/reads `agent_decision_log`; invoked via gateway for approve/reject |

> **Note:** Most services target **Spring Boot 4**; `insight-service` and `agent-service` use **Spring Boot 3.5** with **Spring AI**, since Spring AI is not yet compatible with Boot 4. There is **no** Spring Cloud Config Server or Kubernetes manifests in this repository, Compose is the primary local orchestration path.

---

## The agentic control loop

Lightwise's agent does not act autonomously on the physical system. The flow is:

1. `usage-service` detects a threshold breach and publishes to Kafka topic `usage-alerts`.
2. `agent-service` consumes the alert and asks an LLM to judge it, giving the model two tools: `getUsageHistory` (reads usage-service) and `getDeviceInfo` (reads device-service).
3. The LLM reasons about whether the pattern looks like genuine sustained overuse or normal cycling, and may call a third tool, `proposeAction`, which only writes a `PENDING_APPROVAL` row and notifies the user. It cannot execute anything itself.
4. A human reviews the proposal and hits approve or reject via `agent-service`'s endpoints.
5. On approval, `agent-service` calls `device-service`'s `POST /devices/{id}/command`. That endpoint independently checks the device's `never_shut_off` flag before doing anything, if the device is protected, the command is blocked and logged regardless of who or what approved it.
6. Every step, the alert, the reasoning, the proposal, the human decision, and the final executed/blocked outcome, is logged and auditable.

The safety gate lives in `device-service`, in code, not in the LLM's system prompt and not in the approval UI, so it holds even if the agent misjudges a situation or a human approves the wrong thing.

---

## Tech stack

- **Language:** Java **21**
- **Framework:** **Spring Boot 4** (domain services and gateway); **Spring Boot 3.5** + **Spring AI** (`insight-service`, `agent-service`)
- **Spring Cloud:** **2025.1.0** — Gateway (Server WebMVC), **Circuit Breaker** (Resilience4j)
- **Messaging:** **Apache Kafka** (KRaft)
- **Databases:** **MySQL 8** (relational data), **InfluxDB 2** (time-series usage)
- **Identity (local dev):** **Keycloak**
- **Email (local dev):** **Mailpit**
- **Observability:** **Micrometer**, **Prometheus**, **Grafana**
- **API documentation:** **springdoc-openapi** (gateway aggregates service OpenAPI URLs)
- **Containerization:** **Docker** & **Docker Compose**
- **Build:** **Maven** (each service includes `mvnw`)

Kubernetes is **not** part of this repo; deploying to K8s would be a natural extension (Helm charts, ConfigMaps, service mesh, etc.).

---

## Getting started

### Prerequisites

- **JDK 21**
- **Docker** and **Docker Compose**
- **Maven** (optional if you use `./mvnw` in each service)
- **Ollama**, running locally, for `insight-service` and `agent-service` if you want the LLM-backed paths active

### Clone the repository

```bash
git clone git@github.com:leetjourney/lightwise.git
cd lightwise
```

### Start infrastructure

From the **repository root**:

```bash
docker compose -v up -d
```

This brings up **MySQL**, **Kafka**, **Kafka UI**, **InfluxDB**, **Mailpit**, **Keycloak** (+ DB), **Prometheus**, and **Grafana**.

Stop everything:

```bash
docker compose down
```

If databases fail to initialize, remove volumes or re-run `docker/mysql/init.sql` as described in `AGENTS.md`.

### Build services

Each microservice is its own Maven project:

```bash
cd user-service && ./mvnw -q package && cd ..
# Repeat for: device-service, ingestion-service, usage-service, alert-service, insight-service, agent-service, api-gateway
```

Or run with:

```bash
./mvnw spring-boot:run
```

### Run applications

1. Ensure Docker Compose is running (Kafka, MySQL, InfluxDB, etc.).
2. Start services on the **host** on their default ports (see table above), or containerize them yourself.
3. For **Kafka from the host**, bootstrap is typically **`localhost:9094`** (external listener in Compose).
4. If exercising the agent path, make sure Ollama is running and reachable at the URL configured in `agent-service`'s `application.properties`.

**Prometheus** in this repo is configured to scrape **`host.docker.internal`** for Actuator endpoints, so metrics work when Spring Boot apps run on the **host** while Prometheus runs in Docker.

### Quick pipeline test

Post a sample reading to ingestion (direct to service or via gateway if routed):

```bash
curl -X POST http://localhost:8082/api/v1/ingestion \
  -H 'Content-Type: application/json' \
  -d '{"deviceId":"dev-1","timestamp":"2025-01-01T12:00:00Z","watts":1200}'
```

Then check **usage-service** logs, **InfluxDB**, **Kafka UI** (`http://localhost:8070`), and **Mailpit** (`http://localhost:8025`) after threshold/alert logic runs. If the reading is a sustained breach, check `agent-service`'s decision log for a `PENDING_APPROVAL` row, and approve or reject it via the agent's endpoints.

### Access points (local defaults)

| What | URL |
| ------ | ----- |
| **API Gateway** | <http://localhost:9000> |
| **Grafana** | <http://localhost:3000> (admin / admin) |
| **Prometheus** | <http://localhost:9090> |
| **Kafka UI** | <http://localhost:8070> |
| **Mailpit** | <http://localhost:8025> |
| **Keycloak** | <http://localhost:8091> |
| **InfluxDB UI** | <http://localhost:8072> |

Service-specific OpenAPI is linked from the gateway's Swagger UI configuration (`/swagger-ui.html`).

---

## Observability

- Each Spring Boot app exposes **`/actuator/prometheus`** (enabled via dependencies and management config).
- **Prometheus** (`docker/prometheus/prometheus.yml`) defines scrape jobs for the gateway and all services, including `agent-service`, on the host.
- **Grafana** loads provisioning from `docker/grafana/provisioning` and uses Prometheus as a data source.
- **Circuit breaker** state can be surfaced through Actuator health where enabled (see gateway `application.properties`).

Use Grafana for dashboards and Prometheus for ad-hoc queries and alerting rules as you extend the deployment.

---

## Future improvements

- **End-to-end tests** — Contract or black-box tests across gateway → services → Kafka → DB
- **CI/CD** — Build matrix per service, image publish, Compose or K8s smoke tests
- **Frontend dashboard** — SPA for devices, live usage charts, alert history, and the agent approval queue
- **Multi-device agent reasoning** — prioritize across several flagged devices under a shared power budget instead of one device at a time
- **Real actuation adapters** — replace the mock device command adapter with real smart-plug protocol integrations (Kasa, Shelly, Tuya, Home Assistant)
- **AuthZ hardening** — Fine-grained scopes, service-to-service tokens, policy engine
- **Kubernetes** — Helm charts, external secrets, HPA, and Kafka/Influx operators
- **Centralized config** — Spring Cloud Config or external secret stores for non-dev environments

---

## Additional documentation

- **[AGENTS.md](AGENTS.md)** — Quick runbook for AI agents and operators (ports, topics, curl examples).
- Per-service **`HELP.md`** — Module-specific notes where present.

---

*Lightwise — a portfolio-grade Spring microservices example combining event-driven monitoring with a supervised, tool-using AI agent, built for learning production-style patterns without oversimplifying the moving parts.*
