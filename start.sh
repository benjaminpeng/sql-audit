#!/bin/bash

# 1. Stop existing processes
echo "Stopping existing services..."
# Use lsof if available (Mac/Linux), fallback to fuser (common on Linux/WSL)
if command -v lsof >/dev/null 2>&1; then
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    lsof -ti:5174 | xargs kill -9 2>/dev/null || true
elif command -v fuser >/dev/null 2>&1; then
    fuser -k 8080/tcp 2>/dev/null || true
    fuser -k 5174/tcp 2>/dev/null || true
fi

# OS Detection for proper environment variables
OS="$(uname -s)"
if [ "$OS" = "Darwin" ]; then
    # Mac specific JAVA_HOME
    if [ -d "/opt/homebrew/opt/openjdk@21" ]; then
        export JAVA_HOME=/opt/homebrew/opt/openjdk@21
    fi
elif [ "$OS" = "Linux" ]; then
    # Linux/WSL specific JAVA_HOME (optional fallback)
    if [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
        export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    fi
fi

# 2. Start Backend
echo "Starting SQL Audit Backend on $OS..."
cd backend
nohup mvn spring-boot:run > backend.log 2>&1 &
BACKEND_PID=$!
echo "Backend started (PID: $BACKEND_PID). Logs: backend/backend.log"

# 3. Start Frontend
echo "Starting SQL Audit Frontend..."
cd ../frontend
nohup npm run dev -- --port 5174 > frontend.log 2>&1 &
FRONTEND_PID=$!
echo "Frontend started (PID: $FRONTEND_PID). Logs: frontend/frontend.log"

# 4. Wait for simple health check (optional sleep)
sleep 3

echo "=================================================="
echo "✅ Project is running!"
echo "Frontend: http://localhost:5174"
echo "Backend:  http://localhost:8080"
echo "=================================================="
