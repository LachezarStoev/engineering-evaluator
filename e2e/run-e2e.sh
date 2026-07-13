#!/bin/sh
set -eu

PROJECT_NAME="engineering-evaluator-e2e"

cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    mkdir -p test-results
    docker compose -p "$PROJECT_NAME" logs --no-color >test-results/docker-compose.log 2>&1 || true
  fi
  docker compose -p "$PROJECT_NAME" down -v --remove-orphans
  return "$status"
}

trap cleanup EXIT INT TERM
cleanup
npx playwright test
