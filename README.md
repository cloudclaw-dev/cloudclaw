# CloudClaw

Enterprise-grade open source AI Agent platform built with Spring Boot and Spring AI.

## Why CloudClaw

Most AI Agent platforms target individual developers, relying on local filesystems and script execution. Enterprises need something different: multi-tenant isolation, stateless scalability, and a secure Agent runtime.

CloudClaw exists to:

- **Make AI Agent platforms as easy to deploy as any regular app** — `java -jar` to start, Docker Compose for production
- **Multi-tenant by design** — Sessions, memories, and configs are isolated per user, not a single-user toy
- **Stateless Agents** — All state lives in databases and caches, enabling horizontal scaling
- **Security first** — Agent I/O through MCP, database, or isolated sandbox — never raw host access
- **Pluggable architecture** — Memory engines, message queues, LLM providers, and MCP servers are all replaceable

## CloudClaw vs OpenClaw

CloudClaw and [OpenClaw](https://github.com/openclaw/openclaw) serve different purposes:

| | OpenClaw | CloudClaw |
|------|----------|-----------|
| **Target** | Personal AI assistant | Enterprise AI Agent platform |
| **Language** | Node.js / TypeScript | Java (Spring Boot) |
| **Users** | Single user | Multi-tenant with data isolation |
| **Storage** | Local filesystem (MEMORY.md) | Database (PostgreSQL / SQLite) |
| **Script Execution** | Local Shell / PTY | Isolated Sandbox (Local/Docker/E2B) |
| **Agent State** | Stateful (local process) | Stateless, horizontally scalable |
| **Memory** | Local Markdown files | Database-backed, extensible (Mem0 / Zep) |
| **AI Framework** | Custom | Spring AI |
| **Frontend** | None (third-party integrations) | Vue 3 + Element Plus (Admin + Chat) |
| **Deployment** | Personal devices | Server / Container / K8s |
| **License** | MIT | Apache 2.0 |

In short: **OpenClaw is your personal butler. CloudClaw is your enterprise Agent middleware.**

## Features

- 🤖 **Multi-Agent** — Create and manage multiple AI agents with independent configs
- 🔧 **MCP Integration** — Built-in MCP gateway with connection pooling, tool routing, and permission control
- 🧠 **Memory Service** — Pluggable memory engine (JDBC / Mem0) with vector search support
- 💬 **Session Management** — Session persistence, context caching, message history
- 🔐 **Auth & RBAC** — JWT authentication with admin/user role separation
- 🎯 **Skill System** — Claude Agent Skill standard with progressive disclosure (metadata → instructions → files), auto-discovery via `@Tool`
- 📡 **Message Queue** — Redis Streams or in-memory MQ for async processing
- 🖥️ **Dual UI** — Admin dashboard + Chat interface in one Vue.js app
- 🐘 **PostgreSQL** — Cluster mode with Redis cache
- 🪶 **Standalone Mode** — SQLite, zero external dependencies
- 📊 **Monitoring** — System logs, prompt logs, usage dashboards
- 🔒 **Sandbox Execution** — Isolated code execution (Python/JS/Shell/Java) with Local, Docker, and E2B backends, Stateless and Session modes
- 🧩 **LLM Management** — Multi-provider credential management, model registry, usage tracking

## Multi-Agent Workflow

CloudClaw supports 5 built-in orchestration modes for multi-agent collaboration:

| Mode | Description |
|------|-------------|
| **Pipeline** | Sequential steps — output of one node feeds into the next |
| **Parallel** | Multiple agents run simultaneously, results merged (concat or LLM summarize) |
| **Router** | LLM-based intent classification routes to the best-matching sub-agent |
| **Supervisor** | A planner/reviewer agent delegates tasks to specialist sub-agents iteratively |
| **Handoff** | Conversation is transferred between agents, each maintaining its own context |

All modes are configured inline (no external agent references needed) and support per-node model, system prompt, MCP servers, and skills.

## Architecture

```
cloudclaw
├── cloudclaw-app          # Spring Boot entry point & configuration
├── cloudclaw-common       # Shared models, DTOs, utilities
├── cloudclaw-auth         # JWT authentication & authorization
├── cloudclaw-agent        # Agent engine, prompt assembly, chat orchestration
├── cloudclaw-llm          # LLM multi-provider routing, credential encryption, usage stats
├── cloudclaw-mcp          # MCP gateway, connection pool, tool routing
├── cloudclaw-memory       # Memory service (JDBC / Mem0 engines)
├── cloudclaw-session      # Session & message persistence
├── cloudclaw-skill        # Skill definition & management
├── cloudclaw-mq           # Message queue abstraction (Redis Streams / in-memory)
├── cloudclaw-admin        # Admin API controllers
├── cloudclaw-user         # User-facing API controllers
├── cloudclaw-sandbox      # Code sandbox (Local/Docker/E2B, Stateless/Session modes)
├── cloudclaw-standalone   # Standalone mode (SQLite, in-memory MQ, Caffeine cache)
└── cloudclaw-ui           # Vue.js frontend (Chat + Admin merged)
```

## Quick Start

### Standalone Mode (Zero Dependencies)

```bash
# Build
mvn clean package -DskipTests

# Set required secrets (no defaults for security)
export JWT_SECRET="your-secret-key-at-least-32-bytes-long!!"
export CRYPTO_SECRET="your-crypto-secret-key-at-least-32b"

# Run
java -jar cloudclaw-app/target/cloudclaw-app-1.0.2-SNAPSHOT.jar

# Access
# Chat:    http://localhost:8080/
# Login:   admin / admin123
```

Standalone mode works out of the box: SQLite + in-memory MQ. No PostgreSQL or Redis needed.

> ⚠️ `JWT_SECRET` and `CRYPTO_SECRET` are **required** — the application will refuse to start without them. Generate strong random strings for production.

After login, go to **LLM 管理** to configure your API key and model provider.

### Cluster Mode (PostgreSQL + Redis)

```bash
# Prerequisites: PostgreSQL 16 + Redis 7

# Create database
createdb cloudclaw

# Build
mvn clean package -DskipTests

# Set required secrets
export JWT_SECRET="your-secret-key-at-least-32-bytes-long!!"
export CRYPTO_SECRET="your-crypto-secret-key-at-least-32b"

# Run
java -jar cloudclaw-app/target/cloudclaw-app-1.0.2-SNAPSHOT.jar \
  --spring.profiles.active=cluster
```

Configure `application-cluster.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cloudclaw
    username: postgres
    password: your-password
  data:
    redis:
      host: localhost
      port: 6379
```

## Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `spring.profiles.active` | `standalone` | `standalone` or `cluster` |
| `cloudclaw.jwt.secret` | *(required)* | JWT signing key (env: `JWT_SECRET`, no default) |
| `cloudclaw.jwt.access-token-ttl` | `2h` | Access token TTL |
| `cloudclaw.jwt.refresh-token-ttl` | `7d` | Refresh token TTL |
| `cloudclaw.memory.engine` | `jdbc` | Memory engine: `jdbc` or `mem0` |
| `cloudclaw.mq.provider` | `inmemory` | MQ provider: `inmemory` or `redis` |
| `cloudclaw.mcp.pool.max-connections-per-server` | `5` | Max connections per MCP server |
| `cloudclaw.cache.session-ttl` | `30m` | Session cache TTL |
| `cloudclaw.crypto.secret` | *(required)* | API key encryption secret (env: `CRYPTO_SECRET`, no default) |
| `cloudclaw.sandbox.default-backend` | `LOCAL` | Sandbox backend: `LOCAL`, `DOCKER`, `E2B` |
| `cloudclaw.sandbox.default-mode` | `STATELESS` | Sandbox mode: `STATELESS` or `SESSION` |
| `cloudclaw.sandbox.default-timeout` | `30s` | Default code execution timeout |
| `cloudclaw.sandbox.max-timeout` | `5m` | Maximum allowed execution timeout |

## Tech Stack

- **Backend**: Java 17, Spring Boot 3.4.5, Spring AI 1.1.5
- **Database**: PostgreSQL 16 / SQLite
- **Cache**: Redis 7
- **MQ**: Redis Streams
- **Auth**: JWT
- **Migration**: Flyway
- **Sandbox**: agent-sandbox-core 0.9.1 (Local), Docker (Testcontainers), E2B
- **Frontend**: Vue 3, Element Plus, Vite, TypeScript, ECharts
- **AI**: Spring AI (OpenAI compatible, supports DeepSeek/Qwen/GLM/Ollama)

## License

Apache License 2.0
