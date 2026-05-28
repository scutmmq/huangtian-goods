#!/usr/bin/env bash
set -euo pipefail

APP_NAME="${APP_NAME:-online-mall-backend}"
IMAGE_NAME="${IMAGE_NAME:-online-mall-backend:latest}"
PROFILE="${1:-${SPRING_PROFILES_ACTIVE:-prd}}"
HOST_PORT="${HOST_PORT:-8080}"
CONTAINER_PORT="${CONTAINER_PORT:-8080}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ ! -f app.jar ]]; then
  echo "app.jar not found. Please run this script inside the extracted release package."
  exit 1
fi

if [[ ! -f "config/application-${PROFILE}.yaml" ]]; then
  echo "config/application-${PROFILE}.yaml not found. Available profiles are dev and prd."
  exit 1
fi

mkdir -p logs

docker_env_args=()
if [[ -f .env ]]; then
  docker_env_args+=(--env-file "$SCRIPT_DIR/.env")
elif [[ "$PROFILE" == "prd" ]]; then
  echo "Warning: .env not found. Production will use defaults from config/application-prd.yaml."
fi

docker_network_args=()
if [[ "${ADD_HOST_GATEWAY:-true}" == "true" ]]; then
  docker_network_args+=(--add-host=host.docker.internal:host-gateway)
fi

echo "Building Docker image: ${IMAGE_NAME}"
docker build -t "$IMAGE_NAME" .

if docker ps -a --format '{{.Names}}' | grep -Fxq "$APP_NAME"; then
  echo "Removing existing container: ${APP_NAME}"
  docker rm -f "$APP_NAME"
fi

echo "Starting container: ${APP_NAME}, profile=${PROFILE}, port=${HOST_PORT}:${CONTAINER_PORT}"
docker run -d \
  --name "$APP_NAME" \
  --restart always \
  --network online-mall-net \
  -p "${HOST_PORT}:${CONTAINER_PORT}" \
  -e SPRING_PROFILES_ACTIVE="$PROFILE" \
  -e TZ=Asia/Shanghai \
  -e LOG_PATH=/app/log \
  -v "$SCRIPT_DIR/logs:/app/log" \
  "${docker_network_args[@]}" \
  "${docker_env_args[@]}" \
  "$IMAGE_NAME"

echo "Done. Logs: docker logs -f ${APP_NAME}"
