# CloudClaw 1.0.0 Release

## Quick Start

### Linux / macOS
```bash
chmod +x start.sh stop.sh
./start.sh              # standalone mode (default)
./start.sh cluster      # cluster mode (PostgreSQL + Redis)
./stop.sh               # stop
```

### Windows
```cmd
start.bat               # standalone mode (default)
start.bat cluster       # cluster mode
```

## Directory Structure
```
cloudclaw/
├── cloudclaw-app-1.0.0.jar   # Application JAR
├── config/                    # External config (edit here)
│   ├── application.yml            # Main config
│   ├── application-standalone.yml # SQLite mode (default)
│   ├── application-cluster.yml    # PostgreSQL + Redis mode
│   └── application-prod.yml       # Production logging
├── start.sh / start.bat       # Start script
├── stop.sh                    # Stop script (Linux/macOS)
├── logs/                      # Log output (auto-created)
└── README.txt                 # This file
```

## Requirements
- Java 17+
- (Cluster mode) PostgreSQL 16+ and Redis 7+

## Configuration
Edit `config/application.yml` to customize:
- Server port (default: 8080)
- JWT secret
- Database connection
- LLM API keys

External config in `config/` overrides defaults inside the JAR.

## Access
- Chat UI: http://localhost:8080
- Default admin account: admin / admin123 (please change after first login)
