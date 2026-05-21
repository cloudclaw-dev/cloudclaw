# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-05-20

### Added

- Multi-Agent management with independent configs per agent
- MCP (Model Context Protocol) gateway with connection pooling and tool routing
- Skill system with progressive disclosure (metadata → instructions → files)
- Memory service with pluggable engines (JDBC / Mem0)
- Session management with persistence, context caching, and message history
- JWT authentication with admin/user role separation (RBAC)
- LLM multi-provider management with credential encryption
- Sandbox code execution (Python/JS/Shell/Java) — Local, Docker, and E2B backends
- Stateless and Session sandbox modes
- Vue 3 dual UI (Admin dashboard + Chat interface) with Element Plus
- Standalone mode — SQLite + in-memory MQ, zero external dependencies
- Cluster mode — PostgreSQL + Redis for horizontal scaling
- Docker Compose for both standalone and cluster deployments
- Streaming chat (SSE) with async polling fallback
- System monitoring — usage stats, prompt logs, session dashboard
- Mobile-responsive UI with card-based layouts

### Tech Stack

- Java 17, Spring Boot 3.4.5, Spring AI 1.1.5
- PostgreSQL 16 / SQLite
- Redis 7 (optional)
- Vue 3, Element Plus, Vite, TypeScript, ECharts
- Flyway database migrations
