# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.6] - 2026-06-18

### Added

- **Feishu (飞书) Channel Integration** — Full bidirectional messaging support via Feishu Long Connection (WebSocket):
  - `FeishuLongConnectionManager` — WebSocket client connecting to `wss://msg-frontier.feishu.cn/ws/v2`
  - `FeishuMessageHandler` — Message processing with streaming card updates, emoji reactions, and agent/model metadata in card footer
  - `FeishuAuthController` — Feishu OAuth login with auto-registration and channel binding
  - Pseudo-streaming output: initial card send → throttled patch updates (1.5s interval) → final markdown render
  - Configurable reaction emoji (Typing/THUMBSUP) on incoming messages for acknowledgment
- **Mobile UI Optimization** — Comprehensive mobile-responsive improvements across the entire frontend:
  - `100dvh` dynamic viewport height to fix mobile browser address bar issues
  - Safe Area (env(safe-area-inset)) support for iPhone notch/home indicator
  - Touch-friendly targets (minimum 44px), always-visible action buttons (no hover dependency)
  - 16px input font size to prevent iOS auto-zoom
  - Optimized admin layout: column flex direction, compact card spacing, horizontal table scroll
  - Redesigned mobile admin header with logo, title, and badge
- **Dashboard Recent Sessions API** — New `/api/admin/stats/recent-sessions` endpoint returning paginated session list with agent names
- **Channel Config Page Redesign** — Unified admin-page styling, full i18n coverage (table headers, status labels, channel types, connection modes)
- **Dashboard Chart Fixes** — ECharts grid spacing (top/bottom padding), rotated x-axis labels with interval skipping to prevent legend/label overlap

### Changed

- System version updated to 1.0.6
- Channel config icons differentiated (MCP=Connection, Channel=ChatSquare)
- Dashboard i18n: "Recent Sessions" → "Session Trends" (会话趋势) to distinguish from "Recent Chats" (最近对话)
- Admin mobile header restyled with logo, hamburger button, and Admin badge

### Fixed

- Stale channel binding causing "Bound user not found" error after database rebuild — orphaned bindings now cleaned
- Missing `Session` import in `FeishuMessageHandler` causing compilation failure
- UUID-to-String type mismatch in `AdminStatsController.getRecentSessions()`
- Channel config page style inconsistency with other admin pages
- Duplicate "最近会话" labels in dashboard (session chart vs. recent chats list)
- Duplicate `</style>` tag in `MessageBubble.vue` causing Vue build failure

## [1.0.5] - 2026-06-06

### Added

- **Package Rename** — Migrated entire codebase from `com.cloudclaw` to `run.cloudclaw`, establishing a proper root namespace for the project
- **Navigation Refactoring** — Replaced the three-column layout (nav + session sidebar + chat) with a clean two-column design:
  - Session list merged into the left navigation bar, collapsible (64px/260px)
  - "New Chat" dialog with agent card selection
  - Bottom section: Memory, System Admin (admin only), Language toggle, Dark mode, Logout, Collapse
  - Mobile-responsive tab bar with slide-in session panel
- **Contributing Guide** — Added `CONTRIBUTING.md` with development setup, coding standards, and PR workflow
- **Release Module** — New `cloudclaw-release` Maven module for distribution packaging:
  - Assembly descriptor for standalone distribution archive
  - Start/stop scripts for Linux (`start.sh`, `stop.sh`) and Windows (`start.bat`)
  - Self-contained release README with deployment instructions
- **New Chat Dialog** — Replaced direct session creation with an agent selection dialog (`NewChatDialog.vue`)
- **Tool Display** — New `toolDisplay.ts` utility for human-readable tool call rendering in streaming messages
- **Skeleton Screens** — `SkeletonScreen.vue` component for loading placeholders (sessions, messages)
- **Workflow Panel** — `WorkflowPanel.vue` for real-time multi-agent orchestration status visualization
- **Thinking Support** — `ThinkingDisabledConnector` to handle models that don't support thinking/reasoning tokens
- **Memory Page Redesign** — Complete UI overhaul of the Memory panel:
  - Removed admin-page style; immersive full-screen layout with capsule stats bar
  - Dual-column layout (Profile + Session Memory), responsive stacking on mobile
  - Profile items as rounded cards with hover-reveal edit/delete buttons
  - Session memories grouped by session with timeline layout (collapsible groups)
  - Auto-categorization tags (Preference/Fact/Task/Context) with colored dots
  - Inline SVG empty-state illustrations with dark mode support
- **Session List Enhancement** — Added chat bubble icon before each session title; Agent name tags now display full names instead of truncated 4-character labels
- **Global Theme Composable** — New `utils/theme.ts` module-level singleton for dark mode state, shared across ChatLayout/AdminLayout/Login
- **CSS Variables System** — `assets/variables.css` with `:root` and `html.dark` variables for unified theming

### Changed

- **System version** updated to 1.0.5
- **Admin UI merged into Chat UI** — Removed separate `cloudclaw-ui/admin` project; all admin pages now live under `cloudclaw-ui/chat` with `AdminLayout.vue` and route-based navigation
- **Frontend refactored into focused components** — `SessionList`, `NewChatDialog`, `MessageBubble`, `WorkflowPanel`, `ChatInput`, `WelcomeSection`, `SkeletonScreen`, `CodeBlock` extracted from monolithic `ChatLayout.vue`
- **"New Chat" button** — Icon changed from generic `Plus` to `EditPen`; text shortened from "开始新会话" to "新会话"
- **Welcome page** — Flexbox centering for proper horizontal and vertical alignment in the content area
- **Session sidebar removed** — `SessionSidebar.vue` replaced by inline `SessionList.vue` in the navigation bar
- **Release files removed from repo root** — `release-1.0.0/` directory moved to `cloudclaw-release/` module

### Fixed

- **Duplicate `EditPen` import** — Vue compiler error caused by adding icon name twice in the import block
- **Missing `Plus` import** — Restored after icon change to prevent mobile session button breakage
- **Chat send button not working** — `sendWithRetry` function was undefined (never defined or imported), causing `ReferenceError` silently caught by catch block; added `sendWithRetryFn` wrapping `sendChatMessage` for SSE streaming with retry logic
- **Default agent model misconfiguration** — Changed default agent's `model_id` from `gpt-4o` (OpenAI, no credential) to `glm-4` (ZhipuAI, has valid API key) in V1 seed SQL
- **AdminLayout navigation inconsistency** — Unified nav bar width (200→260px), class names, and CSS values to match ChatLayout

## [1.0.4] - 2026-05-31

### Added

- **Frontend Component Refactoring** -- Split ChatLayout.vue (2100+ lines) into 7 focused components:
  - SessionSidebar, MessageBubble, CodeBlock, WorkflowPanel, ChatInput, WelcomeSection, SkeletonScreen
- **Message Actions** -- Copy button on assistant messages and code blocks, regenerate button, edit-and-resend for user messages
- **Session Management** -- Session search, time-based grouping (Today/Yesterday/Last 7 Days/Earlier), pin/unpin, rename
- **Code Block Enhancements** -- Language label, line numbers, one-click copy, auto-collapse for 30+ line blocks
- **Welcome Page** -- Suggested prompt cards for new conversations
- **Agent Form Tabs** -- Agent editor split into Basic/Model/Workflow/Tools tabs for better UX
- **Dashboard Enhancements** -- Recent conversations list, Agent usage frequency chart (ECharts)
- **SSE Reconnection** -- Auto-retry (up to 3 attempts) on network interruption
- **Streaming Animation** -- Smooth fade-in animation replacing blinking cursor
- **Dark Mode Improvements** -- Comprehensive dark mode fixes across all components and admin pages
- **ECharts** -- Added as dependency for dashboard charts

### Changed

- **System version** updated to 1.0.4
- **Start scripts** now use wildcard jar matching for forward compatibility

## [1.0.3] - 2026-05-26

### Added

- **Async Programming Optimization** -- Comprehensive thread pool and reactive programming improvements:
  - New `parallelTaskExecutor` (core=2, max=8) for ParallelExecutor, eliminating thread pool nesting deadlock risk
  - New `asyncTaskExecutor` (core=2, max=4) for `@Async` tasks and background work
  - `PromptLogService` uses explicit `@Async("asyncTaskExecutor")` instead of default unbounded executor
  - `CloudClawApplication` implements `AsyncConfigurer` to set default async executor
  - `ReactiveContextHelper` utility class centralizes ThreadLocal binding (SandboxContext + MemoryTools)
  - `generateTitleAsync()` uses dedicated executor instead of sharing chatExecutor
  - WorkflowExecutor core threads now time out when idle (`allowCoreThreadTimeOut`)
  - MCP client cleanup moved to `doFinally()` signal, ensuring proper cleanup on all paths

### Fixed

- **MemoryTools contextMap memory leak** -- Exception paths now properly clear context via `doFinally`
- **LimitedToolCallingManager thread safety** -- `int callCount` replaced with `AtomicInteger`
- **PromptLogService cleanupOldLogs concurrency** -- Added `synchronized` to prevent duplicate DB operations
- **targetMcpClients resource leak** -- Close moved from `doOnComplete` to `doFinally`, covers error paths
- **ObjectMapper repeated creation** -- `AgentTransferService` and `SupervisorExecutor` now reuse static instance
- **LoginRateLimiter unbounded map** -- Added `maxEntries` limit (default 10000) to prevent OOM under DDoS
- **catch(Exception ignored) swallowing exceptions** -- Replaced with `log.warn()` for debugging
- **SessionCompressor multi-byte character corruption** -- Surrogate pair boundary check before truncation
- **resolveErrorCode O(n) scan** -- `ErrorCode` now has static Map for O(1) lookup by code
- **chatExecutor core threads never reclaimed** -- Added `allowCoreThreadTimeOut(true)`
- **Sinks buffer overflow silent loss** -- Emits user-visible warning when backpressure drops messages
- **X-Forwarded-For IP spoofing** -- New config `cloudclaw.auth.trust-forwarded-headers` (default false)
- **JWT token race condition** -- `refreshToken` catches `ExpiredJwtException` and returns 401 instead of 500
- **ScheduledExecutorService not managed** -- `AuthService` implements `DisposableBean` for graceful shutdown

### Changed

- Version upgraded from `1.0.3-SNAPSHOT` to `1.0.3` release

## [1.0.2] - 2026-05-25

### Added

- **Unit Tests** — 65 test cases across 5 modules (common, auth, session, llm, agent), all passing
  - Workflow DTO deserialization tests (21 cases)
  - JWT token provider tests (12 cases)
  - Session service tests (9 cases)
  - AES encryption service tests (8 cases)
  - LLM route service tests (6 cases)
  - Agent config & prompt log service tests (9 cases)
- **Unified Error Handling (D2)** — Enterprise-grade error handling framework:
  - `ErrorCode` enum with 34 error codes across 9 categories (1xxx–9xxx)
  - Enhanced `BusinessException` with ErrorCode support (backward compatible)
  - `GlobalExceptionHandler` improved: i18n key support, no internal detail leakage
  - 16 source files migrated from hardcoded exceptions to ErrorCode
  - ChatEngine SSE error unification (sendErrorEvent + resolveErrorCode)
  - Frontend interceptor supports i18n key translation for error messages
- **i18n Polish (D3)** — All hardcoded UI text eliminated:
  - Login.vue (7 items) and ChatLayout.vue (3 items) fully i18n-ized
  - zh.ts / en.ts updated with error code translations
- **Version Display** — `@project.version@` via Maven resource filtering, shown in UI as `v1.0.2 · standalone`
- **Chinese README** — Full `README_CN.md` with language toggle between English and Chinese

### Changed

- Version upgraded from `1.0.2-SNAPSHOT` to `1.0.2` release
- Maven resource filtering enabled for YAML files (version injection)

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
