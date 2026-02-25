#!/bin/bash

# 1. Stop existing processes
echo "Stopping existing services..."
lsof -ti:8080 | xargs kill -9 2>/dev/null
lsof -ti:5174 | xargs kill -9 2>/dev/null

# 2. Start Backend
echo "Starting SQL Audit Backend..."
cd backend
# Use the Java 21 path as observed in user's environment
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
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
