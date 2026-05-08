@visual-clue-pilot @facilitation @story-visual-clue-stage
Feature: Visual Clue Stage

  Scenario: Scenario 1: Underground map displayed horizontally at top of screen
    Given I am a team member in an active retrospective
    When I view the retrospective interface
    Then I should see an underground/metro map style progress indicator at the top of the screen
    And the map should be oriented horizontally from left to right
    And the map should display all 5 phases of the retrospective
    And the map should be always visible throughout the session

  Scenario: Scenario 2: Map shows all 5 phases as connected stations
    Given the underground map is displayed
    When I view the progress indicator
    Then I should see all 5 phases represented as "stations": Set the Stage, Gather Data, Generate Insights, Decide What to Do, Close the Retrospective
    And each station should show the allocated time for that phase
    And stations should be connected by lines showing the journey progression

  Scenario: Scenario 3: Current phase is visually highlighted
    Given I am in phase 3 of the retrospective
    When I view the underground map
    Then the "Generate Insights" station should be visually highlighted
    And the highlighting should clearly distinguish it from other phases
    And it should be obvious this is the current active phase

  Scenario: Scenario 4: Completed phases are greyed out
    Given I am in phase 3 of the retrospective
    When I view the underground map
    Then phases 1 and 2 stations should be displayed in grey/dimmed style
    And the connecting lines to completed phases should also be greyed out
    And they should visually indicate they are completed
    And the visual state should clearly differ from the current phase

  Scenario: Scenario 5: Upcoming phases are displayed normally
    Given I am in phase 3 of the retrospective
    When I view the underground map
    Then phases 4 and 5 stations should be displayed in normal/default style
    And the connecting lines to upcoming phases should be in normal style
    And they should not be greyed out
    And they should not be highlighted like the current phase
    And they should indicate they are yet to come

  Scenario: Scenario 6: Map updates when phase changes
    Given I am in phase 2 with phase 2 highlighted on the map
    When the retrospective advances to phase 3
    Then phase 2 station should change to greyed out (completed)
    And the line between phase 2 and 3 should update to show progression
    And phase 3 station should become highlighted (current)
    And phases 4 and 5 should remain in normal style (upcoming)
    And the update should happen automatically

  Scenario: Scenario 8: Map shows complete retrospective journey
    Given I am viewing the underground map
    When I look at the progress indicator
    Then I should see a visual representation of the complete retrospective journey from left to right
    And the map should show progression from phase 1 through phase 5
    And the layout should resemble an underground/metro transit map
    And stations should be connected by lines indicating the path forward
    And it should provide clear spatial orientation of where we are in the process

  Scenario: Scenario 9: Map is always visible and cannot be hidden
    Given I am participating in a retrospective
    When I interact with the interface
    Then the underground map should remain visible at the top of screen at all times
    And I should NOT be able to minimize, collapse, or hide it
    And it should persist throughout all phases
    And it should always be accessible for orientation

  Scenario: Scenario 12: Map provides contextual awareness through phase states
    Given I am a team member in any phase of the retrospective
    When I view the underground map
    Then I should immediately understand which phases have been completed (greyed out)
    And which phase we are currently in (highlighted)
    And which phases are still to come (normal style)
    And the overall structure of the retrospective
    And this should be clear from the phase state changes alone

  Scenario: Scenario 14: First phase shows correct initial state
    Given a retrospective has just started
    When I am in phase 1: "Set the Stage"
    Then phase 1 station should be highlighted (current)
    And phases 2-5 stations should be displayed in normal style (upcoming)
    And all connecting lines should be in normal style
    And no phases should be greyed out (none completed yet)

  Scenario: Scenario 15: Final phase shows correct completion state
    Given the retrospective has progressed to phase 5: "Close the Retrospective"
    When I view the underground map
    Then phase 5 station should be highlighted (current)
    And phases 1-4 stations should be greyed out (completed)
    And the connecting lines through completed phases should be greyed out
    And the map should visually indicate we are at the final station

