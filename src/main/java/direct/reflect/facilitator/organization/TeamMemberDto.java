package direct.reflect.facilitator.organization;

import java.util.UUID;

public record TeamMemberDto(
        UUID teamId,
        UUID userId,
        TeamRole role
) {
    public static TeamMemberDto from(TeamMember member) {
        return new TeamMemberDto(
                member.getTeam().getId(),
                member.getUserId(),
                member.getRole()
        );
    }
}
