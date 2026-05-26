#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PORT="${PORT:-8080}"
HOST="${HOST:-localhost}"
URL="http://${HOST}:${PORT}/"

cd "${ROOT_DIR}"

if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven is not installed or not available in PATH."
    exit 1
fi

if command -v lsof >/dev/null 2>&1 && lsof -iTCP:"${PORT}" -sTCP:LISTEN -n -P >/dev/null 2>&1; then
    echo "Port ${PORT} is already in use. Try another one, for example:"
    echo "  PORT=8081 ./start.sh"
    exit 1
fi

echo "Starting Doc Image Stripper backend..."
echo "Frontend page: ${URL}"
echo "Press Ctrl+C to stop the service."
echo

exec mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=${PORT}"
