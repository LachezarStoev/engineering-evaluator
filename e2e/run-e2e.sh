#!/bin/sh
set -eu

PROJECT_NAME="engineering-evaluator-e2e"

cleanup() {
  docker compose -p "$PROJECT_NAME" down -v --remove-orphans
}

trap cleanup EXIT INT TERM
cleanup
npx playwright test
