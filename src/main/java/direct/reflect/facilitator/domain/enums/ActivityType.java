package direct.reflect.facilitator.domain.enums;

public enum ActivityType {
    // Participant activities
    PROVIDE_INPUT("Participant providing written input"),
    VOTE("Participant voting on items"),
    GROUP_ITEMS("Participant grouping notes"),
    DISCUSS("Participant engaging in discussion"),

    // Virtual Assistant activities
    EXPLAIN("VA providing explanations"),
    PROMPT("VA prompting for action"),
    SUMMARIZE("VA summarizing content"),
    SUGGEST("VA making suggestions");

    private final String description;

    ActivityType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
