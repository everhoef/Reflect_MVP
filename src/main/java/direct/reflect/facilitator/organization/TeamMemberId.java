package direct.reflect.facilitator.organization;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TeamMemberId implements Serializable {
    private static final long serialVersionUID = 1L;

    private UUID team;
    private UUID userId;

    public TeamMemberId() {
    }

    public TeamMemberId(UUID team, UUID userId) {
        this.team = team;
        this.userId = userId;
    }

    public UUID getTeam() {
        return team;
    }

    public void setTeam(UUID team) {
        this.team = team;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TeamMemberId that = (TeamMemberId) o;
        return Objects.equals(team, that.team) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(team, userId);
    }
}
