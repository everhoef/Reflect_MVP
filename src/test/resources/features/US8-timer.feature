@facilitation @story-timer
Feature: Timer

  Scenario: Scenario 1: Timer displays at phase start
    Given I am a facilitator in an active retrospective session
    And I start the retrospective
    When I advance to a step with a configured duration
    Then I should see a timer displaying the remaining time in MM:SS format
    And the timer should be visible to all participants

  Scenario: Scenario 2: Timer counts down during active phase
    Given I am a facilitator in an active retrospective session
    And I am on a step with a running timer
    When several seconds elapse
    Then the remaining time shown on screen should have decreased
    And all participants should see the same remaining time

  Scenario: Scenario 4: Timer reaches zero
    Given I am a facilitator in an active retrospective session
    And I am on a step whose timer has expired
    Then the timer should display "00:00"
    And the timer should remain visible in red state
    And the session should not automatically advance to the next step

  Scenario: Scenario 5: Facilitator pauses timer
    Given I am the facilitator in an active retrospective session
    And a step timer is running
    When I pause the timer
    Then the timer should stop counting down
    And all participants should see the timer is paused
    And the remaining time should be preserved at the paused value

  Scenario: Scenario 6: Facilitator resumes timer
    Given I am the facilitator in an active retrospective session
    And a step timer is paused
    When I resume the timer
    Then the timer should continue counting down from the preserved remaining time
    And all participants should see the timer running again

  Scenario: Scenario 7: Facilitator manually advances — new step timer starts
    Given I am the facilitator in an active retrospective session
    And a step timer is running
    When I manually advance to the next step
    Then the current step timer should stop and disappear
    And if the next step has a configured duration the new timer should start
    And all participants should see the updated timer state

  Scenario: Scenario 9: Non-facilitator sees timer but not controls
    Given I am a team member (not the facilitator) in an active retrospective session
    And a step timer is running
    When I view the timer interface
    Then I should see the current remaining time
    And I should not see pause or resume controls
