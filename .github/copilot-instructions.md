# GitHub Copilot Instructions

This file provides guidance to GitHub Copilot when working with code in this repository.

## Main Development Guide

For comprehensive development information including architecture, build commands, and coding conventions, see **DEVELOPMENT.md**.
Please always try to plan first before generating code. The same behaviour as Claude Code is expected. Try to behave like a senior developer and think about the implications of the code you generate.

## Copilot-Specific Guidelines

### Code Generation Patterns
- Follow Spring Boot reactive programming patterns using `Mono<T>` and `Flux<T>`
- Use Lombok annotations (`@Data`, `@RequiredArgsConstructor`, `@Slf4j`) for boilerplate reduction
- Implement proper error handling with custom exceptions from the `exception` package
- Use `@Transactional` for service methods that modify data

### Entity and Repository Patterns
- Use UUID v7 with `@GeneratedUuidV7` annotation for primary keys
- Follow the composite key pattern used in Participant entity when needed
- Use JPA repository methods with proper naming conventions

### Authentication and Security
- Use `ReactiveSecurityContextHolder` for security context in reactive code
- Handle both authenticated users and guest participants via `CookieAuthenticationToken`
- Always validate permissions before performing operations

### Testing Code Generation
- Use Testcontainers for integration tests requiring PostgreSQL/Redis
- Use `@SpringBootTest` with appropriate test slices
- Mock external dependencies appropriately in unit tests