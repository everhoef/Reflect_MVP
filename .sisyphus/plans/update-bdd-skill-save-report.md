# Update BDD Analysis Skill - Add Report Saving

## Context

### Original Request
Add requirement to save BDD analysis reports as Markdown files with naming convention: `{ID}-{Story Name}.md`

Example: For user story with ID "6" and name "Five step flow" → `6-Five step flow.md`

---

## Work Objectives

### Core Objective
Update the BDD analysis skill to save generated reports to `.sisyphus/bdd-reports/` directory.

### Concrete Deliverables
- Updated `.opencode/skills/bdd-analysis/SKILL.md` with report saving instructions

### Definition of Done
- [x] Skill includes file naming convention
- [x] Skill specifies output directory
- [x] Skill explains how to extract ID from Notion properties

---

## TODOs

- [x] 1. Update SKILL.md with report saving instructions

  **What to do**:
  Edit `.opencode/skills/bdd-analysis/SKILL.md` to add after "### Step 5: Generate Report":

  ```markdown
  ### Step 5: Generate and Save Report

  **IMPORTANT:** After generating the report, save it as a Markdown file.

  **File Naming Convention:**
  ```
  .sisyphus/bdd-reports/{ID}-{Story Name}.md
  ```

  Where:
  - `{ID}` = The `userDefined:ID` from Notion properties (e.g., "6")
  - `{Story Name}` = The user story name from Notion (e.g., "Five step flow")

  **Example:** For user story with ID "6" and name "Five step flow":
  ```
  .sisyphus/bdd-reports/6-Five step flow.md
  ```

  **Steps:**
  1. Extract `userDefined:ID` from Notion page properties
  2. Extract story name from Notion page title
  3. Create the `.sisyphus/bdd-reports/` directory if it doesn't exist
  4. Write the report to the file
  5. Confirm the file path to the user
  ```

  **Acceptance Criteria**:
  - [x] Step 5 includes file saving instructions
  - [x] Naming convention clearly documented
  - [x] Example provided

  **Commit**: YES
  - Message: `feat(skills): add report file saving to BDD analysis skill`
  - Files: `.opencode/skills/bdd-analysis/SKILL.md`

---

## Success Criteria

- [x] Skill updated with report saving instructions
- [x] Reports will be saved to `.sisyphus/bdd-reports/` directory
- [x] File naming follows `{ID}-{Story Name}.md` convention
