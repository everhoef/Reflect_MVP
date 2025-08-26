# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Main Development Guide

For comprehensive development information including architecture, build commands, and coding conventions, see **DEVELOPMENT.md**.

## Claude-Specific Instructions

### Task Management
- Use the TodoWrite tool to track multi-step tasks and show progress to users
- Mark tasks as completed immediately after finishing them
- Only have one task in_progress at a time

### Testing Commands
- Run tests: `mvn test`
- Run specific test: `mvn test -Dtest=ClassName#methodName`
- Tests use Testcontainers - no manual database setup required
- If you are having issues with Docker or TestContainers (Ryuk) when running the tests, please add this inline before the `mvn` command: `export TESTCONTAINERS_HOST_OVERRIDE=$(colima ls -j | jq -r '.address')`

### Common Development Tasks
- Run application: `mvn spring-boot:run`
- Build project: `mvn clean compile`