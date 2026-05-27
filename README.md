# Facilitator

An app-driven retrospective platform that guides teams through structured, high-quality retrospectives without requiring facilitation skills. The app acts as the facilitator; the human just clicks "Next".

## What it does

- Guides teams through the five-phase Derby & Larsen retrospective structure
- Provides step-by-step facilitation coaching embedded directly in the UI
- Supports real-time multi-user collaboration via Server-Sent Events
- Produces SMART action points with guided templates
- Gives managers anonymized insights into team health trends over time

## Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 4.x, Java 25, Spring MVC, Spring Security, Spring Data JPA |
| Database | PostgreSQL 17 |
| Session store | Redis |
| Frontend | React + Vite + TypeScript |
| Server-rendered routes | Thymeleaf for limited auth/error flows; primary UI is React SPA |
| Auth | OIDC + guest mode |

## Quickstart

### Prerequisites

- Docker Desktop (must be running)
- Java 25 (`brew install --cask temurin@25`)
- Maven wrapper is bundled (`./mvnw`)

### 1. Start the backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```

Spring Boot automatically starts PostgreSQL, Redis, and the React/Vite frontend dev server via `compose.yaml` when Docker Desktop is running. You don't need to run `docker compose up -d` first.

The `import` profile loads retrospective templates, stages, and steps from CSV files on startup. Without it the database starts empty.

### 2. Open the app

- App: http://localhost:8080
- Frontend dev server: http://localhost:5173

### Troubleshooting: manual service startup

If auto-start fails (e.g. Docker Desktop took too long to initialise), you can start the services manually before running the backend:

```bash
docker compose up -d
```

## Clean macOS bootstrap (for partners using Claude Code)

If you're setting up this project on a fresh Mac and plan to work with an AI agent, follow these steps once. After that, the agent should take over the repetitive setup and run workflows.

```bash
# Install Homebrew
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Java 25
brew install --cask temurin@25

# Install GitHub CLI
brew install gh

# Install Docker Desktop (or download it manually)
brew install --cask docker

# Clone the repo
gh repo clone Reflect-Direct/facilitator
cd facilitator

# Start the backend (Spring Boot auto-starts PostgreSQL, Redis, and the frontend dev server)
./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```

Once the backend is running, open http://localhost:8080.

## Running tests

Tests use Testcontainers, so Docker must be running.

```bash
./mvnw test
```

To run the full regression gate:

```bash
./mvnw clean test
```

To run a single test:

```bash
./mvnw test -Dtest=ClassName#methodName
```

To enable the local Conventional Commit hook once per clone:

```bash
git config core.hooksPath .githooks
```

## CI/CD and GitOps

CI runs on every push to `main` and on every pull request targeting `main`.

- **Test gate**: `./mvnw clean test` (Java + Testcontainers) and `cd frontend && npm test` (Vitest)
- **Image build**: Paketo buildpacks via `./mvnw spring-boot:build-image`
- **Registry**: GHCR — `ghcr.io/reflect-direct/facilitator`
- **GitOps source of truth**: `reflect-direct/facilitator-gitops` (acc overlay auto-updated on every `main` merge)
- **Preview environments**: add the `preview` label to a PR to publish a preview image (`pr-<PR_NUMBER>-<SHORT_SHA>`) for the ArgoCD ApplicationSet pull request generator; preview deployments are created and pruned dynamically by ArgoCD, not by commits to `facilitator-gitops`

### Versioning model

- **Maven project version** (`pom.xml`, currently `0.0.1-SNAPSHOT`) is the development/build version for the app source.
- **Deployment image version** is controlled by Git context in CI:
  - PR previews: `pr-<PR_NUMBER>-<SHORT_SHA>`
  - `main` promotions: `sha-<SHORT_SHA>`
  - formal releases only: Git tag `vX.Y.Z` publishes image `X.Y.Z`
- The built OCI image metadata is stamped from the CI-derived deployment version, so runtime/deployment identity does not silently depend on the Maven snapshot string.

## Project structure

```text
src/main/java/direct/reflect/facilitator/
  auth/          OIDC + guest authentication
  configurator/  Retro templates, stages, steps, CSV import
  facilitation/  Sessions, participants, flow control
  eventing/      SSE real-time event streaming
  web/           View controllers and remaining server-rendered routes
  common/        Shared utilities and configuration
```

## Deeper guidance

See [`AGENTS.md`](AGENTS.md) for:
- domain and business rules
- architecture and coding conventions
- testing rules and delivery gates
- AI-agent operating guidance

For partner-oriented local environment workflows, see:
- `.opencode/skills/partner-dev-assistant/SKILL.md`
