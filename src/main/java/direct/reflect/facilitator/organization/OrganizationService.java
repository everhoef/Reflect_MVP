package direct.reflect.facilitator.organization;

import jakarta.transaction.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository) {
        this.organizationRepository = organizationRepository;
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @Transactional
    public OrganizationDto createOrganization(CreateOrganizationRequest request) {
        String normalizedSlug = normalizeSlug(request.slug());
        if (organizationRepository.findBySlug(normalizedSlug).isPresent()) {
            throw new DuplicateOrganizationSlugException(normalizedSlug);
        }

        Organization organization = new Organization();
        organization.setName(request.name().trim());
        organization.setSlug(normalizedSlug);

        return OrganizationDto.from(organizationRepository.save(organization));
    }

    @Transactional
    public TeamDto createTeam(UUID organizationId, CreateTeamRequest request) {
        Organization organization = getOrganizationOrThrow(organizationId);

        Team team = new Team();
        team.setName(request.name().trim());
        team.setOrganization(organization);

        return TeamDto.from(teamRepository.save(team));
    }

    @Transactional
    public TeamMemberDto addMember(UUID organizationId, UUID teamId, AddMemberRequest request) {
        Team team = getTeamForOrganizationOrThrow(organizationId, teamId);
        TeamMemberId teamMemberId = new TeamMemberId(teamId, request.userId());

        TeamMember member = teamMemberRepository.findById(teamMemberId)
                .orElseGet(() -> {
                    TeamMember newMember = new TeamMember();
                    newMember.setTeam(team);
                    newMember.setUserId(request.userId());
                    return newMember;
                });

        member.setRole(request.role());
        return TeamMemberDto.from(teamMemberRepository.save(member));
    }

    @Transactional
    public List<OrganizationDto> getOrganizations() {
        return organizationRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(OrganizationDto::from)
                .toList();
    }

    @Transactional
    public List<TeamDto> getTeams(UUID organizationId) {
        getOrganizationOrThrow(organizationId);
        return teamRepository.findByOrganization_Id(organizationId)
                .stream()
                .sorted(Comparator.comparing(team -> team.getName(), String.CASE_INSENSITIVE_ORDER))
                .map(TeamDto::from)
                .toList();
    }

    @Transactional
    public List<TeamMemberDto> getMembers(UUID organizationId, UUID teamId) {
        Team team = getTeamForOrganizationOrThrow(organizationId, teamId);
        return teamMemberRepository.findByTeamId(team.getId())
                .stream()
                .sorted(Comparator.comparing(member -> member.getUserId().toString()))
                .map(TeamMemberDto::from)
                .toList();
    }

    private Organization getOrganizationOrThrow(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    private Team getTeamForOrganizationOrThrow(UUID organizationId, UUID teamId) {
        getOrganizationOrThrow(organizationId);

        Team team = teamRepository.findById(teamId)
                .orElseThrow(TeamNotFoundException::new);

        if (!team.getOrganization().getId().equals(organizationId)) {
            throw new TeamNotFoundException();
        }

        return team;
    }

    private String normalizeSlug(String slug) {
        return slug.trim().toLowerCase(Locale.ROOT);
    }
}
