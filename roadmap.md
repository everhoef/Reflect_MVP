# Automated Retrospective App - Product Breakdown

## Epic 1: Retrospective Planning & Setup

### Feature 1.1: Retrospective Templates & Formats

### Feature 1.2: Retrospective Configurator

**User Story: Web-based RetroStep configuration interface**
- As a retrospective template administrator, I want a user-friendly web interface to create and modify RetroSteps for each RetroStage, so that non-technical team members can configure retrospective flows without editing complex JSON or CSV files

```gherkin
Feature: RetroStep Configuration Web Interface
  As a retrospective template administrator
  I want to manage RetroSteps through a web interface
  So that I can configure retrospectives without technical knowledge

  Scenario: Configure steps for a RetroStage
    Given I am logged in as an administrator
    When I navigate to "Configure RetroStage: Mad Sad Glad"
    Then I should see a list of existing steps in order
    And I should be able to add new steps
    And I should be able to reorder steps by dragging
    And I should be able to delete existing steps

  Scenario: Create CATEGORICAL step with drag-and-drop
    Given I am configuring a RetroStage
    When I click "Add Step"
    And I select "Activity" step type
    And I select "Categorical" data pattern
    Then I should see a form with:
      | Field           | Type              |
      | Step Title      | Text input        |
      | Duration        | Number input      |
      | Prompt          | Textarea          |
      | Categories      | Dynamic list      |
    When I add categories "Mad", "Sad", "Glad" with emojis and colors
    Then I should see a preview of the 3-column layout
    And the configuration should be automatically generated

  Scenario: Create RATING step with validation
    Given I am adding a rating step
    When I set scale minimum to 1 and maximum to 10
    And I enable "Allow Comments"
    Then I should see a preview of the rating scale
    And invalid configurations should show error messages

  Scenario: Reorder steps with visual feedback
    Given a RetroStage has 5 steps configured
    When I drag step 3 to position 1
    Then all steps should renumber automatically
    And the order should be saved immediately

  Scenario: Export and import configurations
    Given I have configured multiple RetroSteps
    When I click "Export Configuration"
    Then I should download a CSV file with the step configurations
    When I upload a valid CSV file
    Then the steps should be imported and validated
    And any errors should be clearly displayed
```

## Epic 2: Retrospective Execution & Facilitation

### Feature 2.1: Team management

### Feature 2.2: Interactive Meeting Interface

### Feature 2.3: Action Item Management

### Feature 2.4: Five-step approach

## Epic 3: Management Insights & Analytics

### Feature 3.1: Team Health Monitoring


# User Stories with Gherkin BDD Scenarios

This document contains detailed user stories and their corresponding Gherkin BDD scenarios for the retrospective facilitation roadmap.

## Epic 1: Retrospective Planning & Setup

### Feature 1.1: Retrospective Templates & Behavioral Design

**User Story: Visual phase indicators**
- As a facilitator, I want clear visual indicators of what phase we're currently in, so I understand the session progress

```gherkin
Feature: Phase Progress Indication
  As a facilitator
  I want clear visual indicators of what phase we're currently in
  So I understand the session progress

  Scenario: Display current phase in session header
    Given I am facilitating a retrospective session
    When the session is in "Gather Data" phase
    Then I should see "Phase 2 of 5: Gather Data" prominently displayed
    And the phase indicator should show progress visually (e.g., progress bar)
    And previous phases should be marked as completed
    And upcoming phases should be shown as pending

  Scenario: Phase transition animations
    Given I am in "Set the Stage" phase
    When I click "Next Phase"
    Then I should see a smooth transition to "Gather Data" phase
    And the phase indicator should update accordingly
    And participants should see the same transition simultaneously
    And the interface should change to show appropriate tools for the new phase
```

**User Story: Available actions visibility**
- As a facilitator, I want to see what actions are available in each phase, so I know how to guide the team

```gherkin
Feature: Phase-Specific Actions
  As a facilitator
  I want to see what actions are available in each phase
  So I know how to guide the team

  Scenario: Actions in Gather Data phase
    Given I am in "Gather Data" phase
    When I view the facilitator controls
    Then I should see "Allow Sticky Note Creation" toggle
    And I should see "Next Phase" button
    And I should see "End Session" button
    But I should not see "Start Voting" button

  Scenario: Actions in Generate Insights phase
    Given I am in "Generate Insights" phase
    When I view the facilitator controls
    Then I should see "Enable Voting" toggle
    And I should see "Group Similar Items" button
    And I should see "Next Phase" button
    But I should not see "Create Sticky Notes" option
```

**User Story: Smooth phase transitions**
- As a participant, I want to see smooth transitions between phases, so the experience feels seamless

**User Story: Phase preview**
- As a facilitator, I want to preview what each phase will look like before starting, so I can prepare the team

---

## Epic 2: Retrospective Execution & Facilitation

### Feature 2.2: Interactive Meeting Interface

**User Story: Digital sticky notes creation**
- As a participant, I want to create digital sticky notes, so I can contribute my thoughts

```gherkin
Feature: Sticky Notes Management
  As a participant
  I want to create digital sticky notes
  So I can contribute my thoughts

  Scenario: Create sticky note in appropriate phase
    Given I am a participant in "Gather Data" phase
    When I click on the "What went well" column
    And I type "Great team communication this sprint"
    And I press Enter
    Then a new sticky note should appear in the "What went well" column
    And other participants should see my sticky note in real-time
    And the sticky note should be attributed to me

  Scenario: Cannot create sticky notes outside Gather Data phase
    Given I am a participant in "Set the Stage" phase
    When I try to click on any column area
    Then I should not see any input fields
    And I should see a message "Sticky notes will be available in Gather Data phase"
```

**User Story: Lane-based visualization system with reusable templates**
- As a template administrator, I want to create one generic Lane structure that can support multiple visual implementations, so that different retrospective formats (Mad Sad Glad with text headers, Feedback Door with symbol headers, etc.) can share the same underlying data model and prevent duplicate development work

**User Story: Multi-step structured retrospective flow system**
- As a facilitator, I want to guide participants through complex multi-step activities within each RetroStage, so that I can run comprehensive retrospectives with privacy controls, timed activities, and collaborative features like the scripted Happiness Histogram, Mad Sad Glad, Original Four, Circle of Questions, and Feedback Door Smileys

**User Story: 3-pattern retrospective data system with incremental extensibility**
- As a retrospective platform administrator, I want to implement retrospective formats using 3 core data patterns (CATEGORICAL, RATING, FREEFORM) that cover 80% of retrospective types, so that I can build a working system incrementally while maintaining extensibility for complex formats later

```gherkin
Feature: 3-Pattern Retrospective Data System
  As a retrospective platform administrator
  I want to implement retrospectives using 3 core data patterns with UI mapping
  So that I can build working retrospectives incrementally while proving extensibility

  Background:
    Given the system supports 3 core data patterns:
      | Pattern     | Description              | UI Layout           | Data Storage           |
      | CATEGORICAL | Items grouped by category| Multi-column grid   | category + content     |
      | RATING      | Numeric scale responses  | Scale + histogram   | rating + comment       |
      | FREEFORM    | Open text responses      | Text areas/list     | content only           |
    And each pattern maps to specific UI components in the 3-section layout (LEFT: instructions, CENTER: activity, RIGHT: results)
    And all patterns use the same ParticipantResponse entity with flexible fields

  Scenario: CATEGORICAL pattern - Mad Sad Glad implementation
    Given I configure a "Mad Sad Glad" RetroStage as CATEGORICAL
    When I define the configuration:
      """
      {
        "pattern": "CATEGORICAL",
        "categories": [
          {"id": "mad", "title": "Mad", "color": "#EF4444", "emoji": "😡"},
          {"id": "sad", "title": "Sad", "color": "#3B82F6", "emoji": "😢"},  
          {"id": "glad", "title": "Glad", "color": "#10B981", "emoji": "😊"}
        ],
        "allowMultiple": true,
        "maxLength": 200
      }
      """
    Then the UI renders:
      | Section | Content                                          |
      | LEFT    | Facilitator instructions (video/text feed)       |
      | CENTER  | 3-column grid: Mad \| Sad \| Glad headers        |
      | CENTER  | Text areas under each column for sticky notes    |
      | RIGHT   | Empty (or participant list)                      |
    And participant data is stored as:
      | participant | category | content                | timestamp |
      | Alice       | mad      | "Too many meetings"    | 14:30:15  |
      | Alice       | glad     | "Great teamwork"       | 14:31:22  |
      | Bob         | sad      | "Missed deadline"      | 14:32:05  |
    And results phase shows grouped sticky notes by category

  Scenario: RATING pattern - Happiness Histogram implementation  
    Given I configure a "Happiness Histogram" RetroStage as RATING
    When I define the configuration:
      """
      {
        "pattern": "RATING",
        "scale": {"min": 1, "max": 10, "step": 1},
        "labels": ["Very Unhappy", "Very Happy"],
        "allowComment": true,
        "required": true
      }
      """
    Then the UI renders:
      | Section | Content                                          |
      | LEFT    | Facilitator instructions                         |
      | CENTER  | Rating scale 1-10 with radio buttons/dropdown   |
      | CENTER  | Optional comment text area below scale           |
      | RIGHT   | Empty during input, histogram during results     |
    And participant data is stored as:
      | participant | rating | comment                 | timestamp |
      | Alice       | 8      | "Good sprint overall"   | 14:30:15  |
      | Bob         | 6      | "Some frustrations"     | 14:31:10  |
    And results phase shows histogram visualization in RIGHT section
    And original scale remains visible in CENTER for context

  Scenario: FREEFORM pattern - One Word implementation
    Given I configure a "One Word" RetroStage as FREEFORM  
    When I define the configuration:
      """
      {
        "pattern": "FREEFORM",
        "prompt": "Share one word that describes this sprint",
        "maxLength": 50,
        "allowMultiple": false
      }
      """
    Then the UI renders:
      | Section | Content                                          |
      | LEFT    | Facilitator instructions with prompt             |
      | CENTER  | Single text input field                          |
      | CENTER  | Character counter (50 max)                       |
      | RIGHT   | Empty during input, word cloud during results    |
    And participant data is stored as:
      | participant | content   | timestamp |
      | Alice       | "Focused" | 14:30:15  |
      | Bob         | "Chaotic" | 14:31:05  |
    And results phase shows word cloud or list in RIGHT section

  Scenario: Pattern flexibility - mapping 15+ retrospective formats
    Given I want to prove the 3-pattern system covers most retrospective formats
    When I analyze common retrospective types:
      | Format                 | Pattern     | Categories/Scale           | UI Layout        |
      | Mad Sad Glad          | CATEGORICAL | Mad, Sad, Glad             | 3-column grid    |
      | Start Stop Continue   | CATEGORICAL | Start, Stop, Continue      | 3-column grid    |  
      | ESVP                  | CATEGORICAL | Explorer, Shopper, etc.    | 4-column grid    |
      | Happiness Histogram   | RATING      | Scale 1-10                 | Scale + histogram|
      | ROTI (5 stars)        | RATING      | Scale 1-5                  | Star rating      |
      | Satisfaction          | RATING      | Scale 1-5                  | Scale + chart    |
      | One Word             | FREEFORM    | Single text input          | Text + word cloud|
      | Closing Statements   | FREEFORM    | Open text                  | Text areas       |
      | Three Words          | FREEFORM    | 3 text inputs              | Multiple fields  |
      | Worked Well Do Diff  | CATEGORICAL | Worked Well, Do Different  | 2-column grid    |
      | Four Quadrants       | CATEGORICAL | 4 named quadrants          | 2x2 grid layout  |
      | Kudos Cards          | FREEFORM    | Appreciation messages      | Card layout      |
      | Team Radar           | RATING      | Multiple 1-10 scales       | Multi-scale radar|
      | Mood Voting          | RATING      | Emoji scale                | Emoji rating     |
      | Weather Report       | CATEGORICAL | Sunny, Cloudy, Rainy, etc. | Weather icons    |
    Then 80%+ of formats map cleanly to the 3 patterns
    And complex formats can be built as combinations
    And the data model supports all with ParticipantResponse flexibility

  Scenario: Extensibility proof - adding SPATIAL pattern later
    Given I have working CATEGORICAL, RATING, FREEFORM patterns
    When I need to add "Hot Air Balloon" format (spatial positioning)
    Then I can extend the system by:
      - Adding SPATIAL to the pattern enum
      - Extending ParticipantResponse with x/y coordinate fields  
      - Adding spatial UI components to CENTER section rendering
      - Reusing LEFT (instructions) and RIGHT (discussion) sections
    And existing CATEGORICAL/RATING/FREEFORM formats continue working unchanged
    And the architecture scales to support complex patterns incrementally
```

```gherkin
Feature: Multi-Step Structured Retrospective Flow
  As a facilitator
  I want to guide participants through complex multi-step activities
  So that I can run comprehensive retrospectives with full privacy and collaboration controls

  Background:
    Given RetroStages can contain multiple sequential StageSteps
    And each StageStep has specific visibility rules and participant interactions
    And facilitator controls the progression between StageSteps
    And Lanes can be configured with privacy modes and collaboration features

  Scenario: Happiness Histogram multi-step flow
    Given I am facilitating a "Happiness Histogram" RetroStage with StageSteps:
      | Step | Type        | Content                    | Visibility | Lanes                          |
      | 1    | INSTRUCTION | "Rate happiness 1-10"      | PUBLIC     | None                          |
      | 2    | ACTIVITY    | "Individual rating"        | PRIVATE    | SINGLE_CHOICE rating scale    |
      | 3    | REVEAL      | "Show all results"         | PUBLIC     | Same lane, results revealed   |
      | 4    | DISCUSSION  | "Share observations"       | PUBLIC     | MULTI_TEXT comments           |
    When I start the stage
    Then participants see instructions first
    And cannot see each other's ratings during Step 2
    When I click "Next Step" to Step 3
    Then all ratings become visible to everyone
    And histogram visualization appears
    When I progress to Step 4
    Then participants can add comments about the results

  Scenario: Mad Sad Glad with clustering and privacy
    Given I am facilitating "Mad Sad Glad" with StageSteps:
      | Step | Type        | Content              | Visibility | Lanes                    |
      | 1    | INSTRUCTION | "Add sticky notes"   | PUBLIC     | None                     |
      | 2    | ACTIVITY    | "Private notes"      | PRIVATE    | 3x MULTI_TEXT lanes      |
      | 3    | REVEAL      | "Show all notes"     | PUBLIC     | Same lanes, notes revealed|
      | 4    | CLUSTERING  | "Group similar"      | PUBLIC     | Clustering enabled       |
      | 5    | DISCUSSION  | "Name clusters"      | PUBLIC     | Cluster naming           |
    When participants add sticky notes in Step 2
    Then notes are private and blurred for others
    When I reveal in Step 3
    Then all notes become visible
    When I enable clustering in Step 4
    Then participants can drag notes into groups
    And real-time clustering updates occur

  Scenario: Circle of Questions chained responses
    Given I am facilitating "Circle of Questions" with chained Q&A system
    When Participant A answers "Improve sprint planning" 
    And asks "What's one thing we should stop doing?"
    Then Participant B sees A's question
    And can respond with their answer
    And post their own question for Participant C
    And the chain continues until all participants have participated

  Scenario: Timed activities with countdown
    Given I am facilitating "Original Four" with 2-minute timed responses
    When I start the timer for "What did we do well?"
    Then participants see countdown: "1:59 remaining"
    And can post responses until timer expires
    When timer reaches 0:00
    Then input fields become disabled
    And I can progress to discussion phase

  Scenario: Facilitator stage progression controls
    Given I am facilitating any multi-step RetroStage
    When I am on Step 2 of 5
    Then I should see "Step 2 of 5" indicator
    And "Previous Step" button (enabled)
    And "Next Step" button (enabled when ready)
    And overview of all participants' progress
    When I click "Next Step"
    Then all participants immediately move to Step 3
    And their UI updates to match new step requirements

  Scenario: Privacy controls and content revelation
    Given participants have submitted private responses
    When content is in PRIVATE visibility mode
    Then participants see "Waiting for others..." or blurred content
    And cannot see other participants' submissions
    When I change visibility to PUBLIC
    Then all content becomes immediately visible to all participants
    And real-time updates propagate to all sessions

  Scenario: Participant response tracking and analytics
    Given I am facilitating any multi-step stage
    When participants interact with lanes
    Then I can see in real-time:
      | Metric                    | Display                    |
      | Participants completed    | "4 of 6 completed Step 2"  |
      | Individual progress       | Checkmarks per participant  |
      | Response timestamps       | "Alice - 2 min ago"        |
      | Engagement indicators     | Active/idle status         |
    And I can wait for stragglers before progressing
    Or override and continue with current responses

  Scenario: Template configuration for complex flows
    Given I am configuring a new RetroStage "Innovation Retrospective"
    When I define StageSteps:
      """
      Step 1: INSTRUCTION - "Think of breakthrough moments"
      Step 2: ACTIVITY - PRIVATE mode, 3x MULTI_TEXT lanes (New Ideas, Experiments, Learnings)
      Step 3: REVEAL - PUBLIC mode, all notes visible
      Step 4: VOTING - Participants vote on most impactful items
      Step 5: CLUSTERING - Group related items together
      Step 6: ACTION_PLANNING - Convert top items to action items
      """
    Then the system should generate the multi-step flow
    And each step should have appropriate UI components
    And facilitator should get progression controls
    And participant experience should be seamless
```

**User Story: Edit sticky notes**
- As a participant, I want to edit or delete my sticky notes during the input phase, so I can refine my thoughts

**User Story: Real-time collaboration**
- As a facilitator, I want to see all participants' sticky notes in real-time, so I can monitor engagement

```gherkin
Feature: Real-time Sticky Note Updates
  As a facilitator
  I want to see all participants' sticky notes in real-time
  So I can monitor engagement

  Scenario: Real-time sticky note creation
    Given I am facilitating a session with 3 participants
    When participant "Alice" creates a sticky note "Good code reviews"
    Then I should see the sticky note appear immediately
    And it should show "Alice" as the author
    And other participants should also see it immediately

  Scenario: Engagement monitoring
    Given I am facilitating a session
    When I view the participants panel
    Then I should see a count of sticky notes per participant
    And I should see timestamp of last activity per participant
    And participants with no contributions should be highlighted
```

**User Story: Voting on sticky notes**
- As a participant, I want to vote on sticky notes created by others, so we can prioritize important topics

```gherkin
Feature: Sticky Note Voting
  As a participant
  I want to vote on sticky notes created by others
  So we can prioritize important topics

  Scenario: Vote on sticky notes during voting phase
    Given I am in "Generate Insights" phase
    And voting is enabled by the facilitator
    When I click the vote button on a sticky note
    Then the vote count should increase by 1
    And I should see my vote indicated
    And other participants should see the updated vote count

  Scenario: Limited votes per participant
    Given I am in voting phase
    And I have a maximum of 3 votes
    When I have already cast 3 votes
    And I try to vote on another sticky note
    Then I should see "No votes remaining" message
    And the vote should not be registered

  Scenario: Cannot vote on own sticky notes
    Given I created a sticky note "Improve documentation"
    When I try to vote on my own sticky note
    Then the vote button should be disabled
    And I should see "Cannot vote on own items" tooltip
```

**User Story: Group similar sticky notes**
- As a facilitator, I want to group similar sticky notes together, so we can discuss related themes

**User Story: Vote count visibility**
- As a participant, I want to see vote counts on sticky notes, so I understand what the team prioritizes

### Feature 2.3: Action Item Management

**User Story: Create action items**
- As a facilitator, I want to create action items during the "Decide Actions" phase, so we capture concrete next steps

```gherkin
Feature: Action Item Creation
  As a facilitator
  I want to create action items during the "Decide Actions" phase
  So we capture concrete next steps

  Scenario: Create action item from discussion
    Given I am in "Decide Actions" phase
    When I click "Create Action Item"
    And I enter title "Set up automated testing pipeline"
    And I enter description "Implement CI/CD with automated unit tests"
    And I set due date to "2024-02-15"
    And I assign it to "john.doe@company.com"
    And I click "Save Action Item"
    Then the action item should appear in the action items list
    And it should be marked as "Open" status
    And the assignee should be notified

  Scenario: Action items persist across sessions
    Given I created action items in a previous retrospective
    When I start a new retrospective session
    And I navigate to "Previous Action Items" section
    Then I should see all uncompleted action items from previous sessions
    And their current status should be displayed
    And I should be able to review progress
```

**User Story: Assign action items**
- As a facilitator, I want to assign action items to specific team members, so accountability is clear

**User Story: View assigned action items**
- As a participant, I want to see action items assigned to me, so I know my responsibilities

```gherkin
Feature: Action Item Assignment
  As a participant
  I want to see action items assigned to me
  So I know my responsibilities

  Scenario: View my assigned action items
    Given I have 2 action items assigned to me
    When I log into the retrospective system
    And I navigate to "My Action Items"
    Then I should see exactly 2 action items
    And each should show title, description, due date, and status
    And overdue items should be highlighted in red

  Scenario: Mark action item as complete
    Given I have an open action item "Update team documentation"
    When I click "Mark as Complete"
    And I confirm the action
    Then the action item status should change to "Completed"
    And it should show completion date
    And the facilitator should be notified
    And it should appear in next retrospective's "Completed Items" review
```

**User Story: Set due dates**
- As a facilitator, I want to set due dates for action items, so we have clear timelines

**User Story: Mark items complete**
- As a team member, I want to mark action items as complete, so progress is tracked

**User Story: Review previous action items**
- As a facilitator, I want to review action items from previous retrospectives, so we can follow up on commitments

### Feature 2.4: Five-step Approach

**User Story: Structured phase progression**
- As a facilitator, I want to progress through each phase (Set the Stage → Gather Data → Generate Insights → Decide Actions → Close Retro), so the retrospective follows a structured format

```gherkin
Feature: Five-Phase Retrospective Flow
  As a facilitator
  I want to progress through each phase systematically
  So the retrospective follows a structured format

  Scenario: Complete five-phase flow
    Given I start a new retrospective session
    Then I should begin in "Set the Stage" phase
    When I complete "Set the Stage" and click "Next Phase"
    Then I should move to "Gather Data" phase
    When I complete "Gather Data" and click "Next Phase"
    Then I should move to "Generate Insights" phase
    When I complete "Generate Insights" and click "Next Phase"
    Then I should move to "Decide Actions" phase
    When I complete "Decide Actions" and click "Next Phase"
    Then I should move to "Close Retro" phase
    When I complete "Close Retro"
    Then the session should be marked as "Completed"

  Scenario: Phase-specific interfaces
    Given I am in "Gather Data" phase
    Then I should see the sticky note board
    And I should see column headers for categorization
    When I move to "Generate Insights" phase
    Then I should see voting controls
    And sticky notes should become votable
    When I move to "Decide Actions" phase
    Then I should see action item creation interface
    And high-voted items should be highlighted for discussion
```

**User Story: Control phase transitions**
- As a facilitator, I want to control when we move to the next phase, so I can ensure each phase is complete

**User Story: Current phase visibility**
- As a participant, I want to see which phase we're currently in, so I understand what's expected of me

**User Story: Phase-specific interfaces**
- As a facilitator, I want different interfaces for each phase (discussion for Set the Stage, sticky notes for Gather Data, etc.), so the tool supports the phase's purpose

**User Story: Phase restrictions**
- As a participant, I want to be prevented from creating sticky notes outside the "Gather Data" phase, so the process stays structured

---

## Epic 3: Management Insights & Analytics

### Feature 3.1: Team Health Monitoring

**User Story: Team sentiment trends**
- As a manager, I want to see team sentiment trends over multiple retrospectives, so I can identify patterns

```gherkin
Feature: Team Health Analytics
  As a manager
  I want to see team sentiment trends over multiple retrospectives
  So I can identify patterns

  Scenario: View sentiment trend over time
    Given I have conducted 5 retrospectives over 10 weeks
    When I access the team health dashboard
    Then I should see a trend line showing sentiment scores
    And I should see data points for each retrospective
    And I should be able to filter by date range
    And declining trends should be highlighted

  Scenario: Compare teams sentiment
    Given I manage 3 different teams
    When I view the comparative team health report
    Then I should see sentiment scores for each team
    And I should see which teams are improving/declining
    And I should be able to drill down into specific team details
```

**User Story: Participation metrics**
- As a facilitator, I want to see participation metrics (who contributed, how much), so I can ensure everyone is engaged

```gherkin
Feature: Participation Analytics
  As a facilitator
  I want to see participation metrics
  So I can ensure everyone is engaged

  Scenario: Individual participation tracking
    Given I completed a retrospective with 5 participants
    When I view the participation report
    Then I should see for each participant:
      | Metric                    | Display |
      | Number of sticky notes    | Count   |
      | Number of votes cast      | Count   |
      | Comments made             | Count   |
      | Time spent in session     | Minutes |
    And participants with low engagement should be flagged

  Scenario: Action item completion rates
    Given I have 6 months of retrospective data
    When I view the action item analytics
    Then I should see overall completion rate percentage
    And I should see completion rates by team member
    And I should see average time to completion
    And I should see frequently missed deadlines
```

**User Story: Action item completion tracking**
- As a manager, I want to track action item completion rates, so I can measure follow-through

**User Story: Recurring issues identification**
- As a facilitator, I want to see which topics are frequently raised, so I can identify systemic issues

```gherkin
Feature: Issues Pattern Recognition
  As a manager
  I want to identify recurring blocking issues across teams
  So I can address systemic problems

  Scenario: Identify recurring themes
    Given I have 10 retrospectives across different teams
    When I view the issues analysis dashboard
    Then I should see a word cloud of frequently mentioned problems
    And I should see categorized issue types (technical, process, communication)
    And I should see which issues appear across multiple teams
    And I should see trend of issue frequency over time

  Scenario: Export comprehensive team assessment
    Given I need to report on team health to leadership
    When I click "Export Team Assessment"
    Then I should get a report including:
      | Section                    | Content                           |
      | Team sentiment trends      | Charts and numerical scores       |
      | Participation metrics      | Individual and team averages      |
      | Action item completion     | Success rates and bottlenecks     |
      | Common blocking issues     | Categorized problem areas         |
      | Recommendations           | AI-generated improvement suggestions |
    And the report should be in PDF format
    And it should cover the last 3 months by default
```

**User Story: Cross-team comparison**
- As a team lead, I want to compare retrospective metrics across different teams, so I can identify best practices

**User Story: Export retrospective data**
- As a manager, I want to export retrospective data, so I can include insights in reports

---

## Implementation Notes

### Technical Requirements
- **Standardized Columns**: All retrospectives use the same 3-column structure:
  - What went well (Green)
  - What didn't go well (Red)  
  - Action items (Blue)

- **Reusable Structure**: Consistent interfaces and interactions across all sessions with configurable flexibility to be determined

- **Persistent Action Items**: Action items carry forward between retrospectives for tracking and follow-up

- **Comprehensive Analytics**: Multiple analysis dimensions including:
  - Team sentiment trends over time
  - Individual and team participation metrics
  - Action item completion rates and bottlenecks
  - Recurring issue pattern recognition
  - Cross-team comparative analysis
  - Exportable management reports

### Current Implementation Status
- ✅ Authentication (OIDC + Guest users)
- ✅ Session creation and joining
- ✅ Participant management with roles
- ✅ Real-time SSE communication
- ✅ Template/stage system (backend)
- ✅ RetroStep inheritance architecture (abstract base class)
- ✅ Lobby phase implementation
- 🔄 **3-Pattern retrospective data system** (in progress)
  - ✅ RetroStep entity with flexible configuration
  - ✅ ParticipantResponse entity with flexible fields
  - ✅ DataPattern enum (CATEGORICAL, RATING, FREEFORM)
  - ✅ StepType enum (INSTRUCTION, ACTIVITY, DISCUSSION)
  - ✅ ParticipantResponseService with pattern-specific methods
  - 🔲 CATEGORICAL pattern implementation (Mad Sad Glad, Start Stop Continue, ESVP)
  - 🔲 RATING pattern implementation (Happiness Histogram, ROTI, Satisfaction)
  - 🔲 FREEFORM pattern implementation (One Word, Closing Statements, Kudos)
  - 🔲 CSV import system for RetroSteps
  - 🔲 3-section UI layout (LEFT: instructions, CENTER: activity, RIGHT: results)
  - 🔲 Pattern-specific UI components and interactions
- 🔲 **Web-based RetroStep configuration interface** (planned)
  - 🔲 Thymeleaf templates for step management
  - 🔲 REST controllers for step CRUD operations
  - 🔲 Dynamic forms for pattern-specific configuration
  - 🔲 Drag-and-drop step reordering
  - 🔲 Configuration validation and preview
  - 🔲 CSV export/import functionality
- 🔄 **Multi-step structured flow system** (in progress)
  - 🔲 StageStep entity for sub-stage progression
  - 🔲 Privacy controls (PRIVATE/PUBLIC visibility modes)
  - 🔲 Facilitator progression controls
  - 🔲 Real-time step synchronization
  - 🔲 Timed activities with countdown
  - 🔲 Clustering and collaboration features
  - 🔲 Chained question-answer system
- 🔄 **Clean UI/UX based on system-ui mockups** (in progress)
  - 🔲 Golden header with stage progress indicators
  - 🔲 Three-column layout (guidance left, activity center, participants/results right)
  - 🔲 Embedded facilitation coaching with tooltips
  - 🔲 Clean activity templates matching mockup design
- 🔲 **App-driven facilitation system** (planned)
  - 🔲 Flexible guidance tooltip system (text/video/audio support)
  - 🔲 Step-by-step coaching overlays
  - 🔲 Facilitator "just click Next" workflow
  - 🔲 Quality-focused guidance without cognitive overload
- 🔲 Interactive sticky note board
- 🔲 Voting system
- 🔲 Action item management
- 🔲 Team health analytics