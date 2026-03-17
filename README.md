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

- Docker Desktop
- Java 25 (`brew install --cask temurin@25`)
- Maven wrapper is bundled (`./mvnw`)

### 1. Start supporting services

```bash
docker compose up -d
```

This starts PostgreSQL, Redis, and the React/Vite frontend dev server. It does **not** start the Spring Boot backend.

### 2. Start the backend

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=import
```

The `import` profile loads retrospective templates, stages, and steps from CSV files on startup. Without it the database starts empty.

### 3. Open the app

- App: http://localhost:8080
- Frontend dev server: http://localhost:5173

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

# Start support services
docker compose up -d

# Start the backend
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
