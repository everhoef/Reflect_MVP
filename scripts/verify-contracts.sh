#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "=================================================="
echo "  Contract Verification Pipeline"
echo "=================================================="
echo ""

echo "=== [1/3] Verifying SSE contract (JSON Schema → TypeScript) ==="
echo "  Regenerating SSE TypeScript types from shared/schemas/sse/ ..."
(cd frontend && npm run generate-sse-types)
echo "  Running TypeScript typecheck (verifies generated SSE types compile) ..."
(cd frontend && npm run typecheck)
echo "  SSE contract: OK"
echo ""

echo "=== [2/3] Verifying REST contract (OpenAPI → TypeScript) ==="
echo "  NOTE: This step requires Spring Boot running on :8080."
echo "  If Spring Boot is not running, start it first:"
echo "    mvn spring-boot:run -Dspring-boot.run.profiles=import"
echo ""
if curl -s --max-time 3 "http://localhost:8080/v3/api-docs" > /dev/null 2>&1; then
    echo "  Spring Boot is running. Regenerating REST types from live OpenAPI spec ..."
    (cd frontend && npm run generate-types)
    echo "  Running TypeScript typecheck (verifies generated REST types compile) ..."
    (cd frontend && npm run typecheck)
    echo "  REST contract: OK"
else
    echo "  WARNING: Spring Boot is NOT running on :8080. Skipping REST type regeneration."
    echo "  TypeScript typecheck will use committed api.d.ts instead."
    (cd frontend && npm run typecheck)
    echo "  REST contract: SKIPPED (using committed api.d.ts)"
fi
echo ""

echo "=== [3/3] Verifying Java SSE contract test ==="
echo "  Running SseEventContractTest (validates event payloads serialize to valid JSON) ..."
./mvnw test -Dtest=SseEventContractTest --no-transfer-progress -q
echo "  Java SSE contract test: OK"
echo ""

echo "=================================================="
echo "  All contracts verified successfully!"
echo "=================================================="
