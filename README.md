<div align="center">

# CloudClaw

**Enterprise-grade open source AI Agent platform**

Built with Spring Boot · Spring AI · Vue 3

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)

[🌐 Website](https://cloudclaw.run) · [📖 Documentation](https://cloudclaw.run) · [🎮 Demo](https://demo.cloudclaw.run) · [💬 Discussions](https://github.com/cloudclaw-dev/cloudclaw/discussions) · [🐛 Issues](https://github.com/cloudclaw-dev/cloudclaw/issues)

**English** · [中文](README_CN.md)

</div>

---

Most AI Agent platforms target individual developers — local filesystems, script execution, single-user. Enterprises need more: **multi-tenant isolation, stateless scalability, and a secure Agent runtime**.

CloudClaw is an enterprise-grade AI Agent platform that makes deploying multi-agent systems as easy as running a regular web application.

## ✨ Highlights

- 🚀 **One command to start** — `java -jar` in standalone mode, Docker Compose for production
- 👥 **Multi-tenant by design** — Sessions, memories, and configs are isolated per user
- 📈 **Stateless & scalable** — All state in databases and caches, ready for horizontal scaling
- 🔒 **Security first** — Agent I/O through MCP, database, or isolated sandbox — never raw host access
- 🔌 **Pluggable everything** — Memory engines, message queues, LLM providers, MCP servers are all replaceable
- 🤖 **5 orchestration modes** — Pipeline, Parallel, Router, Supervisor, Handoff out of the box


## 🏗️ Architecture Overview

<div align="center">
  <img src="docs/architecture.svg" alt="CloudClaw Architecture" width="800" />
</div>

> Enterprise developers build Digital Employees (Agents) on CloudClaw, then all company employees use them as services.


## 🎯 CloudClaw vs OpenClaw

CloudClaw and [OpenClaw](https://github.com/openclaw/openclaw) are complementary projects:

| | OpenClaw | CloudClaw |
|------|----------|-----------|
| **Target** | Personal AI assistant | Enterprise AI Agent platform |
| **Language** | Node.js / TypeScript | Java (Spring Boot) |
| **Users** | Single user | Multi-tenant with data isolation |
| **Storage** | Local filesystem (MEMORY.md) | Database (PostgreSQL / SQLite) |
| **Script Execution** | Local Shell / PTY | Isolated Sandbox (Local/Docker/E2B) |
| **Agent State** | Stateful (local process) | Stateless, horizontally scalable |
| **Memory** | Local Markdown files | Database-backed (JDBC / Mem0 / Zep) |
| **AI Framework** | Custom | Spring AI |
| **Frontend** | None (third-party integrations) | Vue 3 + Element Plus (Admin + Chat) |
| **Deployment** | Personal devices | Server / Container / K8s |
| **License** | MIT | Apache 2.0 |

> **OpenClaw is your personal butler. CloudClaw is your enterprise Agent middleware.**

## 🚀 Quick Start

### Standalone Mode (Zero Dependencies)

```bash
# Clone
git clone https://github.com/cloudclaw-dev/cloudclaw.git
cd cloudclaw

# Build
mvn clean package -DskipTests

# Set required secrets
export JWT_SECRET="your-secret-key-at-least-32-bytes-long!!"
export CRYPTO_SECRET="your-crypto-secret-key-at-least-32b"

# Run
java -jar cloudclaw-app/target/cloudclaw-app-1.0.5.jar
```

Open http://localhost:8080/ and login with `admin / admin123`.

That's it — SQLite + in-memory MQ, no external dependencies.

> ⚠️ `JWT_SECRET` and `CRYPTO_SECRET` are **required**. Generate strong random strings for production.

### Cluster Mode (PostgreSQL + Redis)

```bash
# Prerequisites: PostgreSQL 16+ and Redis 7+

createdb cloudclaw

mvn clean package -DskipTests

export JWT_SECRET="your-secret-key-at-least-32-bytes-long!!"
export CRYPTO_SECRET="your-crypto-secret-key-at-least-32b"

java -jar cloudclaw-app/target/cloudclaw-app-1.0.5.jar \
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

## 🤖 Multi-Agent Workflow

5 built-in orchestration modes for multi-agent collaboration:

| Mode | Description |
|------|-------------|
| **Pipeline** | Sequential steps — output feeds into the next |
| **Parallel** | Multiple agents run simultaneously, results merged |
| **Router** | LLM-based intent classification routes to the best sub-agent |
| **Supervisor** | Planner/reviewer agent delegates tasks iteratively |
| **Handoff** | Conversation transfers between agents with individual context |

All modes support per-node model, system prompt, MCP servers, and skills.

## 📂 Module Structure

```
cloudclaw
├── cloudclaw-app          # Spring Boot entry point & configuration
├── cloudclaw-common       # Shared models, DTOs, utilities
├── cloudclaw-auth         # JWT authentication & authorization
├── cloudclaw-agent        # Agent engine, prompt assembly, chat orchestration, workflow
├── cloudclaw-llm          # LLM multi-provider routing, credential encryption, usage stats
├── cloudclaw-mcp          # MCP gateway, connection pool, tool routing
├── cloudclaw-memory       # Memory service (JDBC / Mem0 engines)
├── cloudclaw-session      # Session & message persistence
├── cloudclaw-skill        # Skill definition & management
├── cloudclaw-mq           # Message queue abstraction (Redis Streams / in-memory)
├── cloudclaw-admin        # Admin API controllers
├── cloudclaw-user         # User-facing API controllers
├── cloudclaw-sandbox      # Code sandbox (Local/Docker/E2B)
├── cloudclaw-standalone   # Standalone mode (SQLite, in-memory MQ, Caffeine cache)
├── cloudclaw-debug        # Debug utilities (optional, zero-overhead when disabled)
├── cloudclaw-release      # Distribution packaging (scripts, assembly)
└── cloudclaw-ui           # Vue.js frontend (Chat + Admin, unified SPA)
```

## 📦 Tech Stack

| Layer | Technology |
|-------|-----------|
| **Backend** | Java 17, Spring Boot 3.4.5, Spring AI 1.1.5 |
| **Database** | PostgreSQL 16 / SQLite |
| **Cache** | Redis 7 |
| **Message Queue** | Redis Streams |
| **Auth** | JWT (HS384) |
| **Migration** | Flyway |
| **Sandbox** | Local / Docker (Testcontainers) / E2B |
| **Frontend** | Vue 3, Element Plus, Vite, TypeScript, ECharts |
| **AI Models** | Spring AI — OpenAI compatible (DeepSeek / Qwen / GLM / Ollama / …) |

## ⚙️ Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `spring.profiles.active` | `standalone` | `standalone` or `cluster` |
| `cloudclaw.jwt.secret` | *(required)* | JWT signing key (env: `JWT_SECRET`) |
| `cloudclaw.jwt.access-token-ttl` | `2h` | Access token TTL |
| `cloudclaw.jwt.refresh-token-ttl` | `7d` | Refresh token TTL |
| `cloudclaw.crypto.secret` | *(required)* | Encryption secret (env: `CRYPTO_SECRET`) |
| `cloudclaw.memory.engine` | `jdbc` | Memory engine: `jdbc` or `mem0` |
| `cloudclaw.mq.provider` | `inmemory` | MQ provider: `inmemory` or `redis` |
| `cloudclaw.mcp.pool.max-connections-per-server` | `5` | Max MCP connections per server |
| `cloudclaw.sandbox.default-backend` | `LOCAL` | Sandbox: `LOCAL`, `DOCKER`, `E2B` |
| `cloudclaw.sandbox.default-mode` | `STATELESS` | `STATELESS` or `SESSION` |
| `cloudclaw.sandbox.default-timeout` | `30s` | Default execution timeout |
| `cloudclaw.sandbox.max-timeout` | `5m` | Maximum execution timeout |

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

[Apache License 2.0](LICENSE)

---

<div align="center">

**[⬆ Back to Top](#cloudclaw)**

</div>
