#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

PROJECT_NAME="engineering-evaluator-e2e"
CREATED_ENV=false

if [ ! -f ../.env ]; then
  cp ../.env.example ../.env
  CREATED_ENV=true
fi

cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    mkdir -p test-results
    docker compose -p "$PROJECT_NAME" logs --no-color >test-results/docker-compose.log 2>&1 || true
  fi
  docker compose -p "$PROJECT_NAME" down -v --remove-orphans || true
  if [ "$CREATED_ENV" = true ]; then
    rm -f ../.env
  fi
  return "$status"
}

trap cleanup EXIT INT TERM
cleanup
npx playwright test
