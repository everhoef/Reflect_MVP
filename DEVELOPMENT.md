# Development Guide

This document provides comprehensive guidance for working with the Facilitator codebase - a Spring Boot application for team retrospectives.

## Project Overview

This is a Spring Boot 3.5.3 application called "Facilitator" - a web application that facilitates team retrospectives. It's built with Java 21 and uses reactive programming (WebFlux) for real-time functionality.

### Application Purpose
The Facilitator enables teams to conduct structured retrospective meetings with real-time synchronization between participants. The application guides teams through retrospective phases while maintaining participant engagement and facilitator control.

### User Journey
1. **Login Screen**: Users start at a login page with two options:
   - **Authenticated Mode**: Login with credentials for persistent identity
   - **Guest Mode**: Enter as guest (similar to Zoom/Teams guest participants)

2. **Lobby Creation/Joining**:
   - **Create Lobby**: User becomes the "Facilitator" and creates a new retrospective session
   - **Join Lobby**: User joins an existing session as a participant

3. **Lobby Synchronization**: 
   - Server-Sent Events (SSE) stream established for real-time updates
   - All participants synchronized on lobby state and participant list
   - Facilitator controls when to advance to retrospective phases

4. **Retrospective Flow**:
   - Facilitator guides participants through structured phases
   - All participants synchronized on current step/activity
   - Real-time updates ensure everyone stays in sync

## Build and Development Commands

### Maven Commands
- **Build the project**: `mvn clean compile`
- **Run tests**: `mvn test`
- **Run a specific test**: `mvn test -Dtest=ClassName#methodName`
- **Run the application**: `mvn spring-boot:run`
- **Package the application**: `mvn clean package`

### Running with Docker Compose
The application requires PostgreSQL and Redis, provided via Docker Compose:
- **Start dependencies**: `docker compose up -d`
- **Stop dependencies**: `docker compose down`

### Running Tests
Tests use Testcontainers for integration testing with PostgreSQL and Redis. No additional setup needed - containers are managed automatically during test execution.

## Architecture Overview

### Design Principles

The application follows **function-oriented modular architecture** instead of traditional layered architecture, organized around business capabilities:

#### GRASP Principles
- **Information Expert**: Assign responsibility to the class that has the information needed to fulfill it
- **Creator**: Assign responsibility for creating objects to classes that closely use those objects
- **Controller**: Keep controllers concise and focused - they should only handle HTTP concerns and delegate to business services
- **Low Coupling**: Minimize dependencies between modules through interfaces and dependency injection
- **High Cohesion**: Group related functionality within the same module

#### Module Organization
Organize code by **business function** rather than technical layers:
- **Retrospective Management**: Session creation, lifecycle, template handling
- **Participant Management**: User participation, authentication, roles
- **Real-time Communication**: Event streaming, notifications
- **Template Configuration**: Template definition, stage management

#### Service Layer Design
- Use `@Service` annotations for all business function implementations
- Keep business logic independent of Spring Boot framework code
- Make services database-agnostic through repository abstractions
- Use constructor injection (recommended over field injection)

#### Database Independence
- Business logic should not depend on JPA entities or Spring Data
- Use domain models in business layer, separate from persistence models
- Repository interfaces should return domain objects, not JPA entities
- Database concerns isolated to infrastructure layer

#### Global Exception Handling
- Implement comprehensive global exception handling for all layers
- Use `@ControllerAdvice` for REST API error responses
- Handle reactive stream errors appropriately
- Provide meaningful error messages to clients

#### Logging
- Use `@Slf4j` annotation consistently across all classes
- Log at appropriate levels (DEBUG for detailed flow, INFO for business events, WARN/ERROR for problems)
- Include contextual information (session IDs, user IDs) in log messages

### Domain Model
- **RetroSession**: Main entity representing a retrospective session with phases (CREATED → LOBBY → SET_THE_STAGE → GATHER_DATA → GENERATE_INSIGHTS → DECIDE_ACTIONS → CLOSE_RETRO → COMPLETED)
- **Participant**: Users participating in sessions with composite primary key (participantId + sessionId)
- **RetroTemplate/RetroStage/RetroStep**: Template system defining retrospective flow and activities
- **RetroPhase**: Enum defining the lifecycle states of a retrospective

### Key Components

#### Controller Separation
The application maintains **strict separation** between three types of controllers:

- **View Controllers**: Handle Thymeleaf template rendering and web page navigation
  - Serve HTML pages with server-side rendering
  - Handle form submissions and redirects
  - Focus on user interface concerns

- **API Controllers**: Handle REST API endpoints for data operations
  - Return JSON responses for AJAX calls
  - Handle CRUD operations
  - Focus on data exchange

- **Event Controllers**: Handle Server-Sent Events (SSE) streaming
  - Manage real-time communication streams
  - Broadcast updates to connected clients
  - Focus on real-time synchronization

#### Other Components
- **Services**: Business function implementations, framework-independent
- **Repositories**: Data access abstractions returning domain objects
- **Security**: Custom cookie-based authentication with guest user support
- **Messaging**: Server-sent events (SSE) for real-time updates via RetroEventGateway

### Technology Stack
- **Backend**: Spring Boot 3.5.3, WebFlux (reactive), Spring Security, Spring Data JPA
- **Database**: PostgreSQL with Hibernate
- **Session Management**: Redis for session storage
- **Frontend**: Thymeleaf templates with HTMX for dynamic interactions
- **Testing**: JUnit 5, Testcontainers, Spring Boot Test
- **Tools**: Lombok for boilerplate reduction, MapStruct for mapping

### Authentication System
Uses a custom cookie-based authentication system supporting two modes:

#### Authenticated Mode
- Users log in with credentials for persistent identity
- Session maintained across browser sessions
- Full user profile and history available

#### Guest Mode
- Similar to Zoom/Teams guest participants
- No account required - enter display name only
- Temporary session-based identity
- Guest authentication handled via `CookieAuthenticationToken` and `GuestAuthenticationFilter`

#### Facilitator Role
- The user who **creates** a lobby automatically becomes the **Facilitator**
- Facilitator has control over retrospective flow progression
- Only facilitator can advance participants to next phases/steps
- Other participants must wait for facilitator guidance

### Real-time Features
The application uses Server-Sent Events (SSE) for real-time synchronization:

#### Lobby Synchronization
- SSE stream established when participants join lobby
- Real-time updates for participant list changes
- Synchronized lobby state across all connected clients
- Facilitator actions broadcast to all participants

#### Retrospective Synchronization  
- All participants synchronized on current phase/step
- Facilitator controls phase advancement for entire group
- Real-time updates ensure no participant gets left behind
- Event handling managed through RetroEventGateway and EventService

### Data Import
CSV import functionality is available via CsvImporterService for loading retrospective templates and stages from CSV files located in `src/main/resources/`.

## Development Notes

### Database Schema
- Uses UUID v7 for primary keys via custom `@GeneratedUuidV7` annotation
- Composite primary keys for Participant entity (participantId + sessionId)
- Hibernate DDL auto-configuration set to "create-drop" for development

### Reactive Programming
The application uses Spring WebFlux reactive streams. Controllers return `Mono<T>` or `Flux<T>` for non-blocking operations.

### Template System
Retrospectives follow a template-driven approach where RetroTemplate defines stages, and each stage contains multiple steps that guide participants through activities.

### Session Lifecycle
Sessions progress through well-defined phases with business logic in RetroSessionService for phase transitions and step advancement.

## Code Conventions

### Java Coding Standards
- Use Lombok annotations (`@Data`, `@RequiredArgsConstructor`, etc.) to reduce boilerplate
- Use `@Slf4j` for logging in all classes
- Prefer constructor injection over field injection (`@RequiredArgsConstructor` with final fields)
- Follow reactive programming patterns with proper error handling
- Use `@Service` for all business function implementations
- Keep business logic out of Spring Boot framework code
- Use `@Transactional` for service methods that modify data
- Implement comprehensive global exception handling with `@ControllerAdvice`

### Controller Separation Standards
- **Strict separation** between View, API, and Event controllers
- **View Controllers**: Only handle Thymeleaf rendering and web navigation
- **API Controllers**: Only handle REST endpoints returning JSON
- **Event Controllers**: Only handle SSE streaming for real-time updates
- Never mix responsibilities - each controller type has single purpose

### Testing Patterns
- Use Testcontainers for integration tests requiring database/Redis
- Mock external dependencies in unit tests
- Test reactive streams with `StepVerifier` from reactor-test

### Security Considerations
- Always validate user permissions before operations
- Use SecurityContext for authentication checks
- Handle guest users appropriately in security filters