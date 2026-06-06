-- ============================================================
-- CloudClaw PostgreSQL Schema — Complete V1
-- ============================================================

CREATE TABLE users (
    id          VARCHAR(36) PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    role        VARCHAR(20) NOT NULL DEFAULT 'user',
    enabled     BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_keys (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(100),
    key_hash    VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sandbox_providers (
    id                      VARCHAR(36) PRIMARY KEY,
    name                    VARCHAR(100) NOT NULL,
    type                    VARCHAR(20) NOT NULL,
    enabled                 BOOLEAN DEFAULT TRUE,
    docker_images           TEXT,
    docker_memory           VARCHAR(20),
    docker_cpus             INTEGER,
    docker_network_enabled  BOOLEAN DEFAULT FALSE,
    e2b_api_key             VARCHAR(200),
    e2b_template_id         VARCHAR(100),
    e2b_api_url             VARCHAR(200),
    local_work_dir_base     VARCHAR(500),
    default_timeout         INTEGER DEFAULT 30,
    max_timeout             INTEGER DEFAULT 300,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE agents (
    id                        VARCHAR(36) PRIMARY KEY,
    name                      VARCHAR(100) NOT NULL,
    description               TEXT,
    system_prompt             TEXT NOT NULL,
    model_id                  VARCHAR(100) NOT NULL DEFAULT 'gpt-4o',
    temperature               DOUBLE PRECISION DEFAULT 0.7,
    max_tokens                INTEGER DEFAULT 4096,
    max_tool_calls            INTEGER,
    max_tool_result_chars     INTEGER,
    compression_threshold     INTEGER,
    compression_keep_rounds   INTEGER,
    context_usage_threshold   DOUBLE PRECISION,
    enable_memory_tools       BOOLEAN DEFAULT TRUE,
    memory_profile_max_tokens INTEGER DEFAULT 1000,
    memory_task_max_tokens    INTEGER DEFAULT 2000,
    sandbox_enabled           BOOLEAN DEFAULT FALSE,
    sandbox_backend           VARCHAR(20) DEFAULT 'LOCAL',
    sandbox_mode              VARCHAR(20) DEFAULT 'STATELESS',
    sandbox_timeout           INTEGER DEFAULT 30,
    sandbox_provider_id       VARCHAR(36) REFERENCES sandbox_providers(id),
    sub_agents                TEXT,
    workflow_mode             VARCHAR(20),
    workflow                  TEXT,
    created_by                VARCHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    emoji                     VARCHAR(10),
    featured                  BOOLEAN DEFAULT FALSE,
    greeting_message          TEXT,
    suggested_prompts         TEXT,
    enabled                   BOOLEAN DEFAULT TRUE,
    created_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE mcp_servers (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    transport   VARCHAR(20) NOT NULL,
    url         VARCHAR(500),
    command     VARCHAR(500),
    args        TEXT,
    env         TEXT,
    enabled     BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skills (
    id           VARCHAR(36) PRIMARY KEY,
    name         VARCHAR(100) NOT NULL UNIQUE,
    description  TEXT NOT NULL,
    instructions TEXT NOT NULL,
    tools        TEXT,
    scripts      TEXT,
    "references" TEXT,
    assets       TEXT,
    enabled      BOOLEAN DEFAULT TRUE,
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_files (
    id         VARCHAR(36) PRIMARY KEY,
    skill_id   VARCHAR(36) NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    file_path  TEXT NOT NULL,
    content    TEXT NOT NULL,
    file_type  VARCHAR(20) DEFAULT 'text',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(skill_id, file_path)
);

CREATE TABLE agent_mcp_servers (
    agent_id    VARCHAR(36) NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    server_id   VARCHAR(36) NOT NULL REFERENCES mcp_servers(id) ON DELETE CASCADE,
    PRIMARY KEY (agent_id, server_id)
);

CREATE TABLE agent_skills (
    agent_id    VARCHAR(36) NOT NULL REFERENCES agents(id) ON DELETE CASCADE,
    skill_id    VARCHAR(36) NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    PRIMARY KEY (agent_id, skill_id)
);

CREATE TABLE mcp_tool_permissions (
    id          VARCHAR(36) PRIMARY KEY,
    role_id     VARCHAR(36),
    user_id     VARCHAR(36),
    server_id   VARCHAR(36) REFERENCES mcp_servers(id),
    tool_name   VARCHAR(100),
    allowed     BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sessions (
    id              VARCHAR(36) PRIMARY KEY,
    user_id         VARCHAR(36) NOT NULL,
    agent_id        VARCHAR(36) NOT NULL REFERENCES agents(id),
    title           VARCHAR(500),
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    active_agent_path TEXT DEFAULT 'root',
    workflow_state   TEXT,
    last_active_at  TIMESTAMP,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sessions_user ON sessions(user_id);

CREATE TABLE messages (
    id                  VARCHAR(36) PRIMARY KEY,
    session_id          VARCHAR(36) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    role                VARCHAR(20) NOT NULL,
    content             TEXT,
    reasoning_content   TEXT,
    tool_calls          JSONB,
    tool_result         JSONB,
    tokens_in           INTEGER,
    tokens_out          INTEGER,
    compressed          BOOLEAN DEFAULT FALSE,
    is_summary          BOOLEAN DEFAULT FALSE,
    status              VARCHAR(20) NOT NULL DEFAULT 'completed',
    request_id          VARCHAR(64),
    agent_name          VARCHAR(100),
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_messages_session ON messages(session_id, created_at);

-- Memory: profile items (per-user, cross-session)
CREATE TABLE profile_items (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    content     TEXT NOT NULL,
    tokens      INTEGER,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);
CREATE INDEX idx_profile_items_user ON profile_items(user_id);

-- Memory: session context items (per-session, cleared on session end)
CREATE TABLE session_items (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    session_id  VARCHAR(36) NOT NULL,
    content     TEXT NOT NULL,
    tokens      INTEGER,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);
CREATE INDEX idx_session_items_session ON session_items(session_id);
CREATE INDEX idx_session_items_user ON session_items(user_id);

-- Sandbox session tracking (for SESSION mode)
CREATE TABLE sandbox_sessions (
    id              VARCHAR(36) PRIMARY KEY,
    session_id      VARCHAR(36) NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    agent_id        VARCHAR(36) NOT NULL,
    backend         VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    work_dir        VARCHAR(500),
    provider_id     VARCHAR(36) REFERENCES sandbox_providers(id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sandbox_sessions_session ON sandbox_sessions(session_id);
CREATE INDEX idx_sandbox_sessions_status ON sandbox_sessions(status);

-- Prompt logs
CREATE TABLE prompt_logs (
    id          VARCHAR(36) PRIMARY KEY,
    session_id  VARCHAR(36),
    agent_id    VARCHAR(36),
    user_id     VARCHAR(64),
    model_id    VARCHAR(64),
    role        VARCHAR(10) NOT NULL,
    content     TEXT,
    token_count INTEGER,
    tool_calls  TEXT,
    duration_ms INTEGER,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_prompt_logs_session ON prompt_logs(session_id);
CREATE INDEX idx_prompt_logs_user ON prompt_logs(user_id);

-- LLM tables
CREATE TABLE llm_providers (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(200),
    api_base        VARCHAR(500),
    provider_type   VARCHAR(20) NOT NULL,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE llm_credentials (
    id                  VARCHAR(36) PRIMARY KEY,
    provider_id         VARCHAR(36) NOT NULL REFERENCES llm_providers(id) ON DELETE CASCADE,
    name                VARCHAR(100) NOT NULL,
    api_key_encrypted   VARCHAR(500) NOT NULL,
    weight              INTEGER DEFAULT 100,
    priority            INTEGER DEFAULT 1,
    enabled             BOOLEAN DEFAULT TRUE,
    rate_limit_rpm      INTEGER,
    rate_limit_tpm      INTEGER,
    expires_at          TIMESTAMP,
    last_used_at        TIMESTAMP,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_credential_name UNIQUE (provider_id, name)
);

CREATE TABLE llm_models (
    id              VARCHAR(100) PRIMARY KEY,
    provider_id     VARCHAR(36) NOT NULL REFERENCES llm_providers(id),
    model_name      VARCHAR(200) NOT NULL,
    display_name    VARCHAR(200),
    model_type      VARCHAR(20) DEFAULT 'chat',
    context_window  INTEGER,
    max_output      INTEGER,
    input_price     DOUBLE PRECISION DEFAULT 0,
    output_price    DOUBLE PRECISION DEFAULT 0,
    capabilities    TEXT,
    default_params  TEXT,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE llm_usage_stats (
    id              VARCHAR(36) PRIMARY KEY,
    credential_id   VARCHAR(36) NOT NULL,
    model_id        VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    stat_date       TEXT NOT NULL,
    request_count   INTEGER DEFAULT 0,
    tokens_in       BIGINT DEFAULT 0,
    tokens_out      BIGINT DEFAULT 0,
    cost            DOUBLE PRECISION DEFAULT 0,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_usage_stat UNIQUE (credential_id, model_id, user_id, stat_date)
);
CREATE INDEX idx_usage_stats_date ON llm_usage_stats(stat_date);
CREATE INDEX idx_usage_stats_user ON llm_usage_stats(user_id);
CREATE INDEX idx_usage_stats_model ON llm_usage_stats(model_id);

-- ============================================================
-- Seed Data
-- ============================================================

-- Default admin user (password: admin123, BCrypt encoded)
INSERT INTO users (id, username, password, role, enabled) VALUES
    ('00000000-0000-0000-0000-000000000001', 'admin', '$2b$10$icRYpNK6GUGe1r5f56/IT.jetQ5oRegsI1wd2u6H6wjQsZNBQvss.', 'ADMIN', true);

-- Default agent
INSERT INTO agents (id, name, description, system_prompt, model_id, temperature, max_tokens, max_tool_calls, compression_threshold, compression_keep_rounds, context_usage_threshold, enable_memory_tools, memory_profile_max_tokens, memory_task_max_tokens, created_by, enabled) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Default Assistant', 'A general-purpose AI assistant',
     'You are CloudClaw AI Assistant, a helpful, harmless, and honest AI. You assist users with their questions and tasks. Be concise and accurate.',
     'gpt-4o', 0.7, 4096, 50, 20, 6, 0.75, true, 1000, 2000, '00000000-0000-0000-0000-000000000001', true);

-- Sandbox Agent
INSERT INTO agents (id, name, description, system_prompt, model_id, temperature, max_tokens, max_tool_calls, compression_threshold, compression_keep_rounds, context_usage_threshold, enable_memory_tools, memory_profile_max_tokens, memory_task_max_tokens, sandbox_enabled, sandbox_backend, sandbox_mode, created_by, enabled) VALUES
    ('d0663eb2-4c8e-4b9a-9c2a-3f8e1d5a7b9c', 'Sandbox Agent', 'A code execution assistant',
     'You are a code execution assistant. You MUST use the sandbox_execute tool to run any code. Never answer code questions without executing the code first.',
     'gpt-4o', 0.7, 4096, 50, 20, 6, 0.75, true, 1000, 2000, true, 'LOCAL', 'STATELESS', '00000000-0000-0000-0000-000000000001', true);

-- LLM preset providers
INSERT INTO llm_providers (id, name, display_name, api_base, provider_type) VALUES
('p1', 'openai', 'OpenAI', 'https://api.openai.com', 'openai_compatible'),
('p2', 'zhipu', '智谱AI', 'https://open.bigmodel.cn/api/coding/paas/v4', 'openai_compatible'),
('p3', 'deepseek', 'DeepSeek', 'https://api.deepseek.com', 'deepseek'),
('p4', 'qwen', '通义千问', 'https://dashscope.aliyuncs.com/compatible-mode', 'openai_compatible'),
('p5', 'ollama', 'Ollama', 'http://localhost:11434', 'ollama')
ON CONFLICT (name) DO NOTHING;

-- LLM preset models
INSERT INTO llm_models (id, provider_id, model_name, display_name, model_type, context_window, max_output, input_price, output_price, default_params) VALUES
('gpt-4o', 'p1', 'gpt-4o', 'GPT-4o', 'chat', 128000, 16384, 0.005, 0.015, '{"temperature":0.7}'),
('gpt-4o-mini', 'p1', 'gpt-4o-mini', 'GPT-4o Mini', 'chat', 128000, 16384, 0.00015, 0.0006, '{"temperature":0.7}'),
('glm-4', 'p2', 'glm-4', 'GLM-4', 'chat', 128000, 4096, 0.0001, 0.0001, '{"temperature":0.7}'),
('deepseek-chat', 'p3', 'deepseek-chat', 'DeepSeek Chat', 'chat', 64000, 8192, 0.00014, 0.00028, '{"temperature":0.7}'),
('deepseek-reasoner', 'p3', 'deepseek-reasoner', 'DeepSeek Reasoner', 'chat', 64000, 8192, 0.00055, 0.00219, '{"temperature":0.7}'),
('qwen-plus', 'p4', 'qwen-plus', '通义千问 Plus', 'chat', 131072, 8192, 0.0008, 0.002, '{"temperature":0.7}'),
('qwen-turbo', 'p4', 'qwen-turbo', '通义千问 Turbo', 'chat', 131072, 8192, 0.0003, 0.0006, '{"temperature":0.7}'),
('qwen2.5:7b', 'p5', 'qwen2.5:7b', 'Qwen2.5 7B (Ollama)', 'chat', 32768, 4096, 0, 0, '{"temperature":0.7}'),
('glm-5.1', 'p2', 'glm-5.1', 'GLM-5.1', 'chat', 128000, 16384, 0.0001, 0.0001, '{"temperature":0.7}')
ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- Multi-Agent workflow examples (5 modes)
-- ============================================================

-- 1. Handoff: 客服接力
INSERT INTO agents (id, name, description, system_prompt, model_id, temperature, max_tokens, max_tool_calls, compression_threshold, compression_keep_rounds, context_usage_threshold, enable_memory_tools, memory_profile_max_tokens, memory_task_max_tokens, sub_agents, workflow_mode, workflow, created_by, enabled) VALUES
('a0000001-0000-0000-0000-000000000001', '客服接力', '多轮客服转接系统', '你是客服前台，了解用户需求后转接给合适的客服。', 'glm-5.1', 0.7, 4096, 50, 20, 6, 0.75, 1, 1000, 2000,
 '[{"name":"reception","displayName":"前台接待","systemPrompt":"你是前台接待。技术问题转技术客服，退款转售后客服。","modelId":"glm-5.1"},{"name":"tech_support","displayName":"技术客服","systemPrompt":"你是技术客服，解决技术问题。","modelId":"glm-5.1"},{"name":"after_sales","displayName":"售后客服","systemPrompt":"你是售后客服，处理退款和售后问题。","modelId":"glm-5.1"}]',
 'handoff',
 '{"mode":"handoff","nodes":[{"id":"node_1","name":"reception","display_name":"前台接待","description":"","system_prompt":"你是前台接待。技术问题转技术客服，退款转售后客服。","model_id":"glm-5.1"},{"id":"node_2","name":"tech_support","display_name":"技术客服","description":"","system_prompt":"你是技术客服，解决技术问题。","model_id":"glm-5.1"},{"id":"node_3","name":"after_sales","display_name":"售后客服","description":"","system_prompt":"你是售后客服，处理退款和售后问题。","model_id":"glm-5.1"}],"handoff_config":{"auto_return":false}}',
 '00000000-0000-0000-0000-000000000001', 1);

-- 2. Pipeline: 翻译流水线
INSERT INTO agents (id, name, description, system_prompt, model_id, temperature, max_tokens, max_tool_calls, compression_threshold, compression_keep_rounds, context_usage_threshold, enable_memory_tools, memory_profile_max_tokens, memory_task_max_tokens, sub_agents, workflow_mode, workflow, created_by, enabled) VALUES
('a0000002-0000-0000-0000-000000000002', '翻译流水线', '先翻译再审校的流水线', '翻译系统', 'glm-5.1', 0.7, 4096, 50, 20, 6, 0.75, 1, 1000, 2000,
 '[{"name":"translator","displayName":"翻译器","systemPrompt":"将用户输入翻译成英文，只输出翻译结果。","modelId":"glm-5.1"},{"name":"reviewer","displayName":"审校","systemPrompt":"检查并优化翻译，只输出最终翻译。","modelId":"glm-5.1"}]',
 'pipeline',
 '{"mode":"pipeline","nodes":[{"id":"node_1","name":"translator","display_name":"翻译器","description":"","system_prompt":"将用户输入翻译成英文，只输出翻译结果。","model_id":"glm-5.1"},{"id":"node_2","name":"reviewer","display_name":"审校","description":"","system_prompt":"检查并优化翻译，只输出最终翻译。","model_id":"glm-5.1"}],"pipeline_config":{"passthrough_mode":"replace"}}',
 '00000000-0000-0000-0000-000000000001', 1);

-- 3. Router: 智能路由
INSERT INTO agents (id, name, description, system_prompt, model_id, temperature, max_tokens, max_tool_calls, compression_threshold, compression_keep_rounds, context_usage_threshold, enable_memory_tools, memory_profile_max_tokens, memory_task_max_tokens, sub_agents, workflow_mode, workflow, created_by, enabled) VALUES
('a0000003-0000-0000-0000-000000000003', '智能路由', '根据问题路由到最合适的专家', '根据问题选择最合适的专家来回答。', 'glm-5.1', 0.7, 4096, 50, 20, 6, 0.75, 1, 1000, 2000,
 '[{"name":"math_expert","displayName":"数学专家","description":"擅长数学问题","systemPrompt":"你是数学专家。","modelId":"glm-5.1"},{"name":"history_expert","displayName":"历史专家","description":"擅长历史问题","systemPrompt":"你是历史专家。","modelId":"glm-5.1"},{"name":"science_expert","displayName":"科学专家","description":"擅长科学问题","systemPrompt":"你是科学专家。","modelId":"glm-5.1"}]',
 'router',
 '{"mode":"router","nodes":[{"id":"node_1","name":"math_expert","display_name":"数学专家","description":"擅长数学问题","system_prompt":"你是数学专家。","model_id":"glm-5.1"},{"id":"node_2","name":"history_expert","display_name":"历史专家","description":"擅长历史问题","system_prompt":"你是历史专家。","model_id":"glm-5.1"},{"id":"node_3","name":"science_expert","display_name":"科学专家","description":"擅长科学问题","system_prompt":"你是科学专家。","model_id":"glm-5.1"}],"router_config":{"allow_fallback":true}}',
 '00000000-0000-0000-0000-000000000001', 1);

-- 4. Supervisor: 代码审查主管
INSERT INTO agents (id, name, description, system_prompt, model_id, temperature, max_tokens, max_tool_calls, compression_threshold, compression_keep_rounds, context_usage_threshold, enable_memory_tools, memory_profile_max_tokens, memory_task_max_tokens, sub_agents, workflow_mode, workflow, created_by, enabled) VALUES
('a0000004-0000-0000-0000-000000000004', '代码审查主管', '协调多审查员审查代码', '协调多个审查员审查代码。', 'glm-5.1', 0.7, 4096, 50, 20, 6, 0.75, 1, 1000, 2000,
 '[{"name":"security_reviewer","displayName":"安全审查员","systemPrompt":"你是安全审查专家，专注发现安全漏洞。","modelId":"glm-5.1"},{"name":"perf_reviewer","displayName":"性能审查员","systemPrompt":"你是性能优化专家，专注发现性能问题。","modelId":"glm-5.1"}]',
 'supervisor',
 '{"mode":"supervisor","nodes":[{"id":"node_1","name":"security_reviewer","display_name":"安全审查员","description":"","system_prompt":"你是安全审查专家，专注发现安全漏洞。","model_id":"glm-5.1"},{"id":"node_2","name":"perf_reviewer","display_name":"性能审查员","description":"","system_prompt":"你是性能优化专家，专注发现性能问题。","model_id":"glm-5.1"}],"supervisor_config":{"max_iterations":3}}',
 '00000000-0000-0000-0000-000000000001', 1);

-- 5. Parallel: 多角度分析
INSERT INTO agents (id, name, description, system_prompt, model_id, temperature, max_tokens, max_tool_calls, compression_threshold, compression_keep_rounds, context_usage_threshold, enable_memory_tools, memory_profile_max_tokens, memory_task_max_tokens, sub_agents, workflow_mode, workflow, created_by, enabled) VALUES
('a0000005-0000-0000-0000-000000000005', '多角度分析', '从多个角度并行分析问题', '从多个角度分析用户的问题。', 'glm-5.1', 0.7, 4096, 50, 20, 6, 0.75, 1, 1000, 2000,
 '[{"name":"optimist","displayName":"乐观派","systemPrompt":"你总是从积极乐观的角度分析问题，看到好的一面。","modelId":"glm-5.1"},{"name":"pessimist","displayName":"悲观派","systemPrompt":"你总是从风险和问题的角度分析事情，指出潜在隐患。","modelId":"glm-5.1"},{"name":"realist","displayName":"现实派","systemPrompt":"你从客观中立的角度分析问题，给出务实的建议。","modelId":"glm-5.1"}]',
 'parallel',
 '{"mode":"parallel","nodes":[{"id":"node_1","name":"optimist","display_name":"乐观派","description":"","system_prompt":"你总是从积极乐观的角度分析问题，看到好的一面。","model_id":"glm-5.1"},{"id":"node_2","name":"pessimist","display_name":"悲观派","description":"","system_prompt":"你总是从风险和问题的角度分析事情，指出潜在隐患。","model_id":"glm-5.1"},{"id":"node_3","name":"realist","display_name":"现实派","description":"","system_prompt":"你从客观中立的角度分析问题，给出务实的建议。","model_id":"glm-5.1"}],"parallel_config":{"merge_strategy":"summarize","max_concurrent":5}}',
 '00000000-0000-0000-0000-000000000001', 1);

-- Debug: chat trace table
CREATE TABLE IF NOT EXISTS chat_trace (
    trace_id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    agent_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    model_id TEXT,
    start_time TEXT NOT NULL,
    end_time TEXT,
    duration_ms INTEGER,
    input_tokens INTEGER DEFAULT 0,
    output_tokens INTEGER DEFAULT 0,
    total_tokens INTEGER DEFAULT 0,
    tool_call_count INTEGER DEFAULT 0,
    status TEXT DEFAULT 'RUNNING',
    error_message TEXT,
    spans_json TEXT,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_trace_session ON chat_trace(session_id);
CREATE INDEX IF NOT EXISTS idx_trace_agent ON chat_trace(agent_id);
CREATE INDEX IF NOT EXISTS idx_trace_start ON chat_trace(start_time);
