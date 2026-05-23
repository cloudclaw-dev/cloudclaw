# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.1] - 2026-05-23

### Added

- **Multi-Agent Workflow** — 5 orchestration modes for multi-agent collaboration:
  - Pipeline: sequential step execution with output chaining
  - Parallel: simultaneous execution with merge strategies (concat / LLM summarize)
  - Router: LLM-based intent classification routes to best-matching sub-agent
  - Supervisor: iterative planner/reviewer delegates tasks to specialist sub-agents
  - Handoff: conversation transfer between agents with independent context
- **Workflow execution UI** — real-time status panel showing pipeline steps, parallel node progress, router decisions, supervisor delegation, and handoff targets
- **System info API** — `/api/admin/stats/info` returns version and active Spring profile
- **Version & mode display** — Logo bar shows current version and run mode (standalone / cluster)
- **Frontend performance optimization**:
  - highlight.js reduced from 100+ languages to 12 commonly used (93% smaller)
  - manualChunks splits vendor libs (element-plus, vue, markdown-it) into independent cacheable chunks
  - Tomcat gzip compression enabled for JS/CSS/JSON responses
  - Main index chunk: 1,094 KB → 32 KB (-97%)

### Fixed

- RouterConfig JSON deserialization failure — added missing `@JsonProperty("allow_fallback")` annotation, which caused router-mode agents to fall back to simple chat
- Workflow panel not rendering during streaming — parent `v-if` condition now includes `|| workflowState` so panel appears before text content arrives
- Session content leaking when switching agents/sessions — all streaming state cleared immediately on switch
- Supervisor mode `SocketTimeoutException` — LLM read timeout increased from 60s to 300s
- Prompt logging for multi-agent sub-nodes — all 5 executors now log system/user prompts with `{agentId}/{nodeName}` context
- Agent assistant-role messages excluded from prompt logs
- Sidebar menu icon duplicates resolved — each menu item now has a unique icon
- System monitor missing `nav.session` i18n key — added to both zh/en locales
- Unique credential constraint handling on repeated seed script runs

### Changed

- Version upgraded from `1.0.1-SNAPSHOT` to `1.0.1` release

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
