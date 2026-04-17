package direct.reflect.facilitator.facilitation;

import direct.reflect.facilitator.organization.Organization;
import direct.reflect.facilitator.organization.OrganizationRepository;
import direct.reflect.facilitator.organization.Team;
import direct.reflect.facilitator.organization.TeamRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reusable fixture for creating team-backed retro sessions in integration tests.
 * Reduces boilerplate for organization, team, and session setup.
 */
@Component
public class TeamBackedRetroFixture {

    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final RetroSessionRepository sessionRepository;

    public TeamBackedRetroFixture(
            OrganizationRepository organizationRepository,
            TeamRepository teamRepository,
            RetroSessionRepository sessionRepository) {
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Creates a complete hierarchy: Organization -> Team -> RetroSession.
     */
    public RetroSession createTeamBackedSession(String teamName) {
        Organization organization = new Organization();
        organization.setName(teamName + " Org");
        organization.setSlug(teamName.toLowerCase() + "-org-" + UUID.randomUUID());
        Organization savedOrganization = organizationRepository.saveAndFlush(organization);

        Team team = new Team();
        team.setName(teamName);
        team.setOrganization(savedOrganization);
        Team savedTeam = teamRepository.saveAndFlush(team);

        RetroSession retroSession = new RetroSession();
        retroSession.setName(teamName + " Retro");
        retroSession.setTeam(savedTeam);
        retroSession.setPhase(RetroPhase.CREATED);
        retroSession.setCreatedAt(LocalDateTime.now());
        return sessionRepository.saveAndFlush(retroSession);
    }
}
