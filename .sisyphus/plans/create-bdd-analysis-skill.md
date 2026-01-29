# Create BDD Analysis Skill

## Context

### Original Request
User wants a reusable skill agent to analyze BDD scenarios from Notion user stories and verify implementation status. Reports should be suitable for non-technical business partners with agile coaching backgrounds.

### Interview Summary
**Key Requirements:**
- Fetch user stories from Notion
- Analyze ALL BDD scenarios (Critical and non-Critical)
- Special focus on Critical scenarios - detailed analysis required
- For non-Critical: report implemented ones, but don't prioritize gaps
- More technical evidence than "too high level" - include concrete details
- Generate stakeholder-friendly reports with appropriate technical depth

**Priority Model (from Notion):**
- **Critical** - must be implemented, gets detailed analysis
- **Non-Critical** (normal) - nice to have, report if implemented

**Target Audience:**
- Product owners
- Agile coaches  
- Business partners (with some technical appreciation)

---

## Work Objectives

### Core Objective
Create an OpenCode skill that standardizes BDD scenario analysis and produces consistent, high-quality reports with appropriate technical evidence.

### Concrete Deliverables
- `.opencode/skills/bdd-analysis/SKILL.md` file

### Definition of Done
- [x] Skill file created with proper frontmatter (name, description)
- [x] Skill discoverable via `skill` tool
- [x] Skill produces consistent report format when invoked

### Must Have
- YAML frontmatter with required fields
- Clear workflow steps
- Report template with proper technical depth
- Two-tier priority analysis (Critical vs non-Critical)
- Concrete evidence examples

### Must NOT Have (Guardrails)
- No invented priority levels (only Critical / non-Critical from Notion)
- No line-by-line code walkthrough (but file/entity names are OK)
- No focusing on unimplemented non-Critical scenarios
- No overly abstract "it works" statements without evidence

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: N/A (skill file, not code)
- **User wants tests**: Manual verification
- **Framework**: N/A

### Manual QA Procedure
1. After creating the skill, run `opencode` in the project
2. Check skill appears in available skills list
3. Invoke skill with a test user story
4. Verify report format matches specification

---

## TODOs

- [x] 1. Create skill directory structure

  **What to do**:
  - Create directory: `.opencode/skills/bdd-analysis/`
  
  **Parallelizable**: YES (independent)

  **Acceptance Criteria**:
  - [x] Directory exists at `.opencode/skills/bdd-analysis/`

  **Commit**: NO (groups with 2)

- [x] 2. Create SKILL.md file with complete content

  **What to do**:
  - Create `.opencode/skills/bdd-analysis/SKILL.md`
  - Include YAML frontmatter with:
    - name: bdd-analysis
    - description: (1024 chars max, specific for agent selection)
    - license: MIT
    - compatibility: opencode
    - metadata for audience/workflow
  - Include skill content with refined guidelines

  **Content to write** (full skill content):
  
  ```markdown
  ---
  name: bdd-analysis
  description: Analyze BDD scenarios from Notion user stories and verify implementation status against the codebase. Generates reports with technical evidence suitable for agile coaches and business partners. Focuses on Critical scenarios while reporting all implemented features.
  license: MIT
  compatibility: opencode
  metadata:
    audience: product-owners, agile-coaches, business-partners
    workflow: notion-to-codebase
  ---

  # BDD Scenario Analysis Skill

  You are a BDD Implementation Analyst. Your job is to fetch user stories from Notion, extract BDD scenarios, and verify their implementation status in the codebase. You produce reports with concrete technical evidence at an appropriate abstraction level.

  ## Core Principles

  ### Two-Tier Priority Model

  Notion user stories classify scenarios as either **Critical** or **non-Critical** (normal). Respect this classification:

  | Priority | Analysis Depth | Gap Focus |
  |----------|----------------|-----------|
  | **Critical** | Full detailed analysis | Yes - gaps must be addressed |
  | **Non-Critical** | Standard analysis | No - report implemented, don't stress gaps |

  ### Analysis Strategy

  1. **Analyze ALL scenarios** - Don't skip any
  2. **Critical scenarios get deep analysis** - Full evidence, detailed verification, gap analysis
  3. **Non-Critical implemented** - Report them (nice to know!)
  4. **Non-Critical NOT implemented** - Mention briefly in summary, don't dwell on it

  ### Evidence Depth Philosophy

  Your evidence should be **concrete and technical, but not line-by-line code review**.

  **Include:**
  - Entity/class names that implement concepts
  - Configuration files and what they define
  - Test class names and what they verify
  - Architectural patterns used
  - Data structures (enums, DTOs)
  - Key method/service names

  **Avoid:**
  - Line numbers
  - Full code snippets (>10 lines)
  - Implementation internals (algorithms, loops)
  - Framework boilerplate details

  ## Your Workflow

  ### Step 1: Fetch the User Story from Notion

  Use the Notion MCP tools:
  1. Search: `notion-search` with query for the story name
  2. Fetch: `notion-fetch` with the page ID to get full content

  ### Step 2: Extract and Classify Scenarios

  From the Notion content, create a complete inventory:

  - Count total scenarios
  - Identify which are marked **Critical**
  - Everything else is **non-Critical**

  For each scenario, extract:
  - Scenario number and name
  - Priority (Critical or non-Critical)
  - Given/When/Then criteria (if present)
  - Key acceptance criteria

  ### Step 3: Analyze Codebase Implementation

  **For Critical scenarios** - Deep dive:
  - Find ALL relevant entities, services, configurations
  - Identify test coverage
  - Verify each acceptance criterion
  - Document architectural approach

  **For non-Critical scenarios** - Standard check:
  - Find primary implementation evidence
  - Note test coverage if exists
  - Brief status assessment

  **What to look for:**
  - Domain entities and enums (e.g., `RetroPhase`, `RetroStep`)
  - Service classes (e.g., `RetroSessionService`)
  - Configuration files (CSV, JSON, YAML)
  - Test classes (e.g., `RetroFlowIntegrationTest`)
  - Repository interfaces
  - Controller endpoints
  - Template files (Thymeleaf, React, etc.)

  **Tools to use:**
  - `grep` for finding patterns across codebase
  - `glob` for finding related files
  - `read` for examining specific files
  - `lsp_symbols` for understanding code structure

  ### Step 4: Run Verification Tests (if available)

  If relevant tests exist:
  ```bash
  ./mvnw test -Dtest=RelevantTestClass
  ```

  Report:
  - Test execution result (pass/fail)
  - Number of test methods
  - What behaviors are covered

  ### Step 5: Generate Report

  ---

  ## BDD Implementation Status Report

  **User Story:** [Story Name]  
  **Analysis Date:** [Date]  
  **Critical Status:** [X of Y Critical scenarios implemented]  
  **Non-Critical Status:** [X of Y non-Critical scenarios implemented]

  ### Executive Summary

  [2-3 sentences: Critical scenario status, key wins, any Critical gaps that need attention]

  ### Scenario Inventory

  | Priority | Total | ✅ Implemented | ⚠️ Partial | ❌ Not Started |
  |----------|-------|----------------|------------|----------------|
  | Critical | X | X | X | X |
  | Non-Critical | X | X | X | X |

  ---

  ## Critical Scenarios (Detailed Analysis)

  ### Scenario [N]: [Name]

  **Status:** ✅ Implemented

  **What it does:** [Business-level description]

  **Implementation Evidence:**
  - **Domain Model:** `RetroPhase` enum defines 5 phases (SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO)
  - **Entity:** `RetroSession` tracks current phase and step index
  - **Configuration:** `retrospective_steps.csv` defines 24 steps with componentType, advancementTrigger, durationSeconds
  - **Service:** `RetroSessionService.advanceToNextStep()` handles phase transitions
  - **Real-time sync:** `EventService` publishes `STEP_ADVANCED` events via SSE

  **Test Coverage:**
  - `RetroFlowIntegrationTest.shouldValidateCompleteRetroFlowWithColumnIsolation()` - validates complete 24-step flow with 3 concurrent users
  - `CsvImporterServiceTest` - verifies 24 steps imported correctly

  **Acceptance Criteria Check:**
  - [x] 5 phases defined → `RetroPhase` enum
  - [x] Each phase has activities → Template-Stage mapping
  - [x] Participants synchronized → SSE events + HTMX triggers

  ---

  ### Scenario [N]: [Name]

  **Status:** ⚠️ Partial

  **What it does:** [Business-level description]

  **Implementation Evidence:**
  - **Found:** [What exists]
  - **Missing:** [What's not implemented]

  **Gap Details:**
  [Specific description of what needs to be built]

  ---

  ### Scenario [N]: [Name]

  **Status:** ❌ Not Started

  **What needs to be built:**
  - [Component 1]
  - [Component 2]

  **Suggested approach:** [Brief technical direction]

  ---

  ## Non-Critical Scenarios

  ### Implemented ✅

  | # | Scenario | Evidence |
  |---|----------|----------|
  | [N] | [Name] | [Entity/Service/Test that proves it] |
  | [N] | [Name] | [Brief evidence] |

  ### Not Yet Implemented

  [X scenarios not yet implemented - these are lower priority and can be addressed in future iterations]

  ---

  ## Recommendations

  ### Critical Gaps (Action Required)
  1. [Gap description] - [Suggested fix]

  ### Nice-to-Have (Non-Critical)
  1. [Non-critical improvement if any stand out]

  ---

  ## Evidence Quality Guidelines

  ### Good Evidence Examples

  **For "5-phase flow" scenario:**
  > - `RetroPhase` enum: SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO
  > - `RetroTemplate` entity links stages to each phase via `setTheStage`, `gatherData`, etc. fields
  > - `retrospective_steps.csv` contains 24 steps distributed across 5 stages
  > - `RetroSessionService.advanceToNextStep()` uses `RetroPhase.next()` for transitions
  > - Test: `CsvImporterServiceTest.testImportRetroStepsOrderedByStage()` asserts 24 steps exist

  **For "real-time synchronization" scenario:**
  > - `EventService` publishes `RetroEvent` with type `STEP_ADVANCED`
  > - `RetroEventController` streams SSE to `/api/retro/{id}/events`
  > - Thymeleaf template has `hx-trigger="sse:step_advanced from:body"` on content div
  > - Test: `RetroFlowIntegrationTest` runs 3 browser contexts and verifies all see updates

  ### Bad Evidence Examples

  ❌ Too vague:
  > "The system implements the 5-phase flow"

  ❌ Too detailed:
  > "Line 45 of RetroPhase.java has `GATHER_DATA,` and line 46 has..."

  ❌ No concrete references:
  > "There's a service that handles this"

  ## Invoking This Skill

  The user may say:
  - "Analyze the [Story Name] user story from Notion"
  - "Check BDD implementation status for [feature]"
  - "What Critical scenarios are implemented for [story]?"
  - "Generate a BDD report for [story]"

  Always confirm which user story before starting analysis.
  ```

  **Parallelizable**: NO (depends on 1)

  **Acceptance Criteria**:
  - [x] File exists at `.opencode/skills/bdd-analysis/SKILL.md`
  - [x] File starts with valid YAML frontmatter
  - [x] `name` field matches directory name (bdd-analysis)
  - [x] `description` field is present and under 1024 chars

  **Commit**: YES
  - Message: `feat(skills): add BDD analysis skill for stakeholder reporting`
  - Files: `.opencode/skills/bdd-analysis/SKILL.md`

- [x] 3. Verify skill is discoverable

  **What to do**:
  - Start opencode in the project directory
  - Check that `bdd-analysis` appears in available skills
  - Or run a quick test by asking to load the skill

  **Parallelizable**: NO (depends on 2)

  **Acceptance Criteria**:
  - [x] Skill appears in skill tool's available_skills list
  - [x] Skill can be loaded without errors

  **Commit**: NO (verification only)

---

## Commit Strategy

| After Task | Message | Files |
|------------|---------|-------|
| 2 | `feat(skills): add BDD analysis skill for stakeholder reporting` | `.opencode/skills/bdd-analysis/SKILL.md` |

---

## Success Criteria

### Final Checklist
- [x] Skill file created with valid frontmatter
- [x] Skill discoverable by OpenCode
- [x] Report uses two-tier priority (Critical / non-Critical only)
- [x] Critical scenarios get detailed analysis format
- [x] Evidence examples show appropriate technical depth
- [x] Non-Critical gaps are mentioned but not emphasized
