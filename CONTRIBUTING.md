# Contributing to CloudClaw

Thank you for your interest in contributing to CloudClaw! This guide will help you get started.

## Development Setup

### Prerequisites

- Java 17 (JDK)
- Maven 3.9+
- Node.js 20+
- npm

### Build & Run

```bash
# Clone the repository
git clone https://github.com/your-org/cloudclaw.git
cd cloudclaw

# Build (includes frontend)
mvn clean package -DskipTests

# Run standalone mode
java -jar cloudclaw-app/target/cloudclaw-app-1.0.0.jar

# Access
# Chat UI: http://localhost:8080/
# Login:   admin / admin123
```

### Frontend Development

```bash
cd cloudclaw-ui/chat

# Install dependencies
npm install

# Dev server (port 3001, proxies API to localhost:8080)
npx vite

# Build
npx vite build
```

## Project Structure

CloudClaw is a multi-module Maven project:

- `cloudclaw-app` — Spring Boot entry point
- `cloudclaw-common` — Shared models, DTOs, utilities
- `cloudclaw-auth` — JWT authentication & authorization
- `cloudclaw-agent` — Agent engine, prompt assembly, chat orchestration
- `cloudclaw-llm` — LLM provider routing, credential management
- `cloudclaw-mcp` — MCP gateway, connection pool
- `cloudclaw-memory` — Memory service (JDBC / Mem0)
- `cloudclaw-session` — Session & message persistence
- `cloudclaw-skill` — Skill definition & management
- `cloudclaw-mq` — Message queue abstraction
- `cloudclaw-sandbox` — Code sandbox execution
- `cloudclaw-admin` — Admin API controllers
- `cloudclaw-user` — User-facing API controllers
- `cloudclaw-standalone` — Standalone mode (SQLite)
- `cloudclaw-ui` — Vue.js frontend

## How to Contribute

### Bug Reports

Open a [GitHub Issue](../../issues) with:

- Steps to reproduce
- Expected vs actual behavior
- Logs or screenshots if applicable

### Pull Requests

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes
4. Ensure the build passes: `mvn clean package -DskipTests`
5. Commit with a clear message
6. Push and open a Pull Request

### Code Style

- Java: Follow standard Spring Boot conventions
- Frontend: Vue 3 Composition API with `<script setup lang="ts">`
- Keep PRs focused — one feature or fix per PR

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
