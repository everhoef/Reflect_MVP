# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Main Development Guide

For comprehensive development information including architecture, build commands, and coding conventions, see **DEVELOPMENT.md**.

## Claude-Specific Instructions

### Task Management
- Use the TodoWrite tool to track multi-step tasks and show progress to users
- Mark tasks as completed immediately after finishing them
- Only have one task in_progress at a time

### Reactive Programming Patterns
- When working with WebFlux controllers, use proper reactive chains with `Mono` and `Flux`
- Handle errors in reactive streams with `.doOnError()` and proper error propagation
- Use `ReactiveSecurityContextHolder.getContext()` for authentication in reactive contexts

### Testing Commands
- Run tests: `mvn test`
- Run specific test: `mvn test -Dtest=ClassName#methodName`
- Tests use Testcontainers - no manual database setup required

### Common Development Tasks
- Start dependencies: `docker compose up -d`
- Run application: `mvn spring-boot:run`
- Build project: `mvn clean compile`