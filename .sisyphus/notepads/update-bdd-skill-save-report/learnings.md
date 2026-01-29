# Learnings: Update BDD Analysis Skill - Add Report Saving

## [2026-01-27T12:35] Task: Add Report File Saving to BDD Skill

### What Was Done

Updated `.opencode/skills/bdd-analysis/SKILL.md` to include instructions for saving BDD analysis reports as Markdown files.

### Changes Made

**Section Updated:** "### Step 5: Generate Report" → "### Step 5: Generate and Save Report"

**Added Content:**
1. File naming convention: `.sisyphus/bdd-reports/{ID}-{Story Name}.md`
2. Explanation of ID extraction from `userDefined:ID` Notion property
3. Explanation of Story Name extraction from Notion page title
4. Example: "6-Five step flow.md"
5. 5-step process for saving reports:
   - Extract userDefined:ID from Notion properties
   - Extract story name from Notion page title
   - Create .sisyphus/bdd-reports/ directory if needed
   - Write report to file
   - Confirm file path to user

### Technical Details

**File Modified:** `.opencode/skills/bdd-analysis/SKILL.md`
**Lines Changed:** +24, -1
**Commit:** 85e5a81 - `feat(skills): add report file saving to BDD analysis skill`

### Key Decisions

1. **Directory Location:** `.sisyphus/bdd-reports/` - keeps reports in project-specific sisyphus workspace
2. **Naming Convention:** `{ID}-{Story Name}.md` - makes reports easy to find and correlate with Notion stories
3. **ID Source:** `userDefined:ID` from Notion properties - stable identifier that won't change

### Usage

When the BDD analysis skill is invoked, it will now:
1. Generate the analysis report
2. Save it to `.sisyphus/bdd-reports/{ID}-{Story Name}.md`
3. Confirm the file path to the user

Example: User story "Five step flow" with ID "6" → saved as `6-Five step flow.md`

### Completion Status

✅ All tasks completed
✅ Skill updated with report saving instructions
✅ File naming convention documented
✅ Example provided
✅ Committed to git
