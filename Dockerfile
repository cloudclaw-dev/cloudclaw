# ---- Stage 1: Build frontend ----
FROM node:20-alpine AS frontend-build
WORKDIR /app
COPY cloudclaw-ui/chat/package.json cloudclaw-ui/chat/package-lock.json* ./cloudclaw-ui/chat/
RUN cd cloudclaw-ui/chat && npm install
COPY cloudclaw-ui/chat/ ./cloudclaw-ui/chat/
RUN cd cloudclaw-ui/chat && npx vite build

# ---- Stage 2: Build backend ----
FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
COPY cloudclaw-common/pom.xml cloudclaw-common/
COPY cloudclaw-mq/pom.xml cloudclaw-mq/
COPY cloudclaw-auth/pom.xml cloudclaw-auth/
COPY cloudclaw-session/pom.xml cloudclaw-session/
COPY cloudclaw-memory/pom.xml cloudclaw-memory/
COPY cloudclaw-skill/pom.xml cloudclaw-skill/
COPY cloudclaw-mcp/pom.xml cloudclaw-mcp/
COPY cloudclaw-agent/pom.xml cloudclaw-agent/
COPY cloudclaw-user/pom.xml cloudclaw-user/
COPY cloudclaw-admin/pom.xml cloudclaw-admin/
COPY cloudclaw-llm/pom.xml cloudclaw-llm/
COPY cloudclaw-sandbox/pom.xml cloudclaw-sandbox/
COPY cloudclaw-standalone/pom.xml cloudclaw-standalone/
COPY cloudclaw-app/pom.xml cloudclaw-app/
RUN mvn dependency:go-offline -B

# Copy source (exclude cloudclaw-ui, we provide frontend dist separately)
COPY . .

# Copy pre-built frontend dist
COPY --from=frontend-build /app/cloudclaw-ui/chat/dist ./cloudclaw-ui/chat/dist

# Build (skip frontend exec plugin, we already have dist)
RUN mvn clean package -DskipTests -Dexec.skip=true -B

# ---- Stage 3: Runtime ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=backend-build /app/cloudclaw-app/target/*.jar app.jar

# SQLite data volume
VOLUME /app/data

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
