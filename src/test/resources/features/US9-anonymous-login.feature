@anonymous-login @facilitation @story-anonymous-login
Feature: Anonymous Login

  Scenario: Scenario 1: Team member joins via unique session link
    Given I am a team member
    And I have received a unique session link for a retrospective
    When I click on the session link
    Then I should be automatically logged into the session
    And I should not be prompted for username, password, or email
    And I should see the retrospective interface immediately

  Scenario: Scenario 3: Consistent identity within session
    Given I am participating in a retrospective session with pseudonym "Wise Owl"
    When I add a card, vote, or comment
    Then all my contributions should display as coming from "Wise Owl"
    And other participants should see my contributions attributed to "Wise Owl"
    And I should be able to identify my own contributions

  Scenario: Scenario 4: Re-entry preserves session state
    Given I was disconnected from a retrospective session
    And the session is still active
    When I rejoin via the unique session link
    Then I should see the current phase and timer state
    And I should see all contributions made during my absence
    And I should be able to continue participating seamlessly

  Scenario: Scenario 7: No login barriers or friction
    Given I am a team member with a session link
    When I access the retrospective
    Then I should not be required to create an account
    And I should not be required to remember a password
    And I should not be required to verify my email
    And I should not be required to complete a CAPTCHA
    And I should not be required to provide any personal information
    And I should reach the retrospective interface in one click

  Scenario: Scenario 5: Invalid or expired session link
    Given I have a session link for a retrospective
    And the session has ended or the link is invalid
    When I click on the session link
    Then I should see an error message indicating the session is unavailable
    And I should not be able to access the retrospective
