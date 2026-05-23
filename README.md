<div align="center">

# ☁️🐾 CloudClaw

**Enterprise-grade Open Source AI Agent Platform**

*Build, deploy, and scale AI Agents with Spring Boot & Spring AI*

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.1.5-6db33f.svg)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/badge/Release-v1.0.1-blue.svg)](https://github.com/cloudclaw-dev/cloudclaw/releases/tag/v1.0.1)

[English](#features) · [中文](#-核心特性) · [Quick Start](#-quick-start) · [Documentation](#-architecture)

</div>

---

## 🌟 Why CloudClaw

Most AI Agent platforms target individual developers, relying on local filesystems and script execution. Enterprises need something different: multi-tenant isolation, stateless scalability, and a secure Agent runtime.

CloudClaw exists to:

- 🚀 **Make AI Agent platforms as easy to deploy as any regular app** — `java -jar` to start, Docker Compose for production
- 👥 **Multi-tenant by design** — Sessions, memories, and configs are isolated per user, not a single-user toy
- ⚡ **Stateless Agents** — All state lives in databases and caches, enabling horizontal scaling
- 🔒 **Security first** — Agent I/O through MCP, database, or isolated sandbox — never raw host access
- 🧩 **Pluggable architecture** — Memory engines, message queues, LLM providers, and MCP servers are all replaceable

## ✨ Features

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

## 🔄 Multi-Agent Workflow

CloudClaw supports 5 built-in orchestration modes for multi-agent collaboration:

| Mode | Description |
|------|-------------|
| **Pipeline** | Sequential steps — output of one node feeds into the next |
| **Parallel** | Multiple agents run simultaneously, results merged (concat or LLM summarize) |
| **Router** | LLM-based intent classification routes to the best-matching sub-agent |
| **Supervisor** | A planner/reviewer agent delegates tasks to specialist sub-agents iteratively |
| **Handoff** | Conversation is transferred between agents, each maintaining its own context |

All modes are configured inline (no external agent references needed) and support per-node model, system prompt, MCP servers, and skills.

## 🆚 CloudClaw vs OpenClaw

| | [OpenClaw](https://github.com/openclaw/openclaw) | CloudClaw |
|------|----------|-----------|
| **Target** | Personal AI assistant | Enterprise AI Agent platform |
| **Language** | Node.js / TypeScript | Java (Spring Boot) |
| **Users** | Single user | Multi-tenant with data isolation |
| **Storage** | Local filesystem (MEMORY.md) | Database (PostgreSQL / SQLite) |
| **Script Execution** | Local Shell / PTY | Isolated Sandbox (Docker / E2B) |
| **Agent State** | Stateful (local process) | Stateless, horizontally scalable |
| **Memory** | Local Markdown files | Database-backed (JDBC / Mem0) |
| **AI Framework** | Custom | Spring AI |
| **Frontend** | None (third-party integrations) | Vue 3 + Element Plus (Admin + Chat) |
| **Deployment** | Personal devices | Server / Container / K8s |
| **License** | MIT | Apache 2.0 |

> **OpenClaw is your personal butler. CloudClaw is your enterprise Agent middleware.**

## 🏗 Architecture

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

## 🚀 Quick Start

### Option 1: Download Release (Zero Dependencies)

```bash
# Download from GitHub Releases
wget https://github.com/cloudclaw-dev/cloudclaw/releases/download/v1.0.1/cloudclaw-1.0.1-release.zip
unzip cloudclaw-1.0.1-release.zip

# Start
chmod +x start.sh
./start.sh

# Access http://localhost:8080
# Default login: admin / admin123
```

### Option 2: Build from Source

```bash
git clone https://github.com/cloudclaw-dev/cloudclaw.git
cd cloudclaw
mvn clean package -DskipTests
java -jar cloudclaw-app/target/cloudclaw-app-1.0.1.jar
```

### Option 3: Docker Compose

```bash
# Standalone
docker compose -f docker-compose.standalone.yml up -d

# Cluster (PostgreSQL + Redis)
docker compose -f docker-compose.yml up -d
```

### Cluster Mode Configuration

```bash
# Prerequisites: PostgreSQL 16 + Redis 7
createdb cloudclaw

java -jar cloudclaw-app/target/cloudclaw-app-1.0.1.jar \
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

### After Login

Go to **LLM 管理** to configure your API key and model provider. Supports OpenAI, DeepSeek, Qwen, GLM, Ollama, and any OpenAI-compatible API.

## ⚙️ Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `spring.profiles.active` | `standalone` | `standalone` or `cluster` |
| `cloudclaw.jwt.secret` | (built-in) | JWT signing key (**change in production!**) |
| `cloudclaw.jwt.access-token-ttl` | `2h` | Access token TTL |
| `cloudclaw.jwt.refresh-token-ttl` | `7d` | Refresh token TTL |
| `cloudclaw.memory.engine` | `jdbc` | Memory engine: `jdbc` or `mem0` |
| `cloudclaw.mq.provider` | `inmemory` | MQ provider: `inmemory` or `redis` |
| `cloudclaw.mcp.pool.max-connections-per-server` | `5` | Max connections per MCP server |
| `cloudclaw.cache.session-ttl` | `30m` | Session cache TTL |
| `cloudclaw.sandbox.default-backend` | `LOCAL` | Sandbox backend: `LOCAL`, `DOCKER`, `E2B` |
| `cloudclaw.sandbox.default-mode` | `STATELESS` | Sandbox mode: `STATELESS` or `SESSION` |
| `cloudclaw.sandbox.default-timeout` | `30s` | Default code execution timeout |
| `cloudclaw.sandbox.max-timeout` | `5m` | Maximum allowed execution timeout |

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup.

## 📦 Deployment Modes

| Mode | Database | MQ | Cache | Best For |
|------|----------|----|-------|----------|
| **Standalone** | SQLite | In-Memory | Caffeine | Dev, testing, personal use |
| **Cluster** | PostgreSQL | Redis Streams | Redis | Production, horizontal scaling |

## 🛠 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Runtime** | Java 17, Spring Boot 3.4.5 |
| **AI Framework** | Spring AI 1.1.5 |
| **Database** | PostgreSQL 16 / SQLite |
| **Cache & MQ** | Redis 7 / Caffeine / In-Memory |
| **Auth** | JWT + Spring Security |
| **Migration** | Flyway |
| **Sandbox** | agent-sandbox-core, Docker (Testcontainers), E2B |
| **Frontend** | Vue 3, Element Plus, Vite, TypeScript, ECharts |

## 📄 License

[Apache License 2.0](LICENSE)

---

<div align="center">

**[⬆ Back to Top](#-cloudclaw)**

Made with ❤️ by the CloudClaw Team

</div>
