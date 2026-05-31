package direct.reflect.facilitator.organization;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orgs")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
@Tag(name = "Organization API", description = "Organization, team, and membership management")
public class OrganizationApiController {

    @SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Spring manages service lifecycle — reference is stable")
    private final OrganizationService organizationService;

    @PostMapping
    @ApiResponse(responseCode = "201", description = "Organization created successfully")
    public ResponseEntity<OrganizationDto> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createOrganization(request));
    }

    @GetMapping
    public ResponseEntity<List<OrganizationDto>> getOrganizations() {
        return ResponseEntity.ok(organizationService.getOrganizations());
    }

    @PostMapping("/{orgId}/teams")
    @ApiResponse(responseCode = "201", description = "Team created successfully")
    public ResponseEntity<TeamDto> createTeam(
            @PathVariable UUID orgId,
            @Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.createTeam(orgId, request));
    }

    @GetMapping("/{orgId}/teams")
    public ResponseEntity<List<TeamDto>> getTeams(@PathVariable UUID orgId) {
        return ResponseEntity.ok(organizationService.getTeams(orgId));
    }

    @PostMapping("/{orgId}/teams/{teamId}/members")
    @ApiResponse(responseCode = "201", description = "Team member added successfully")
    public ResponseEntity<TeamMemberDto> addMember(
            @PathVariable UUID orgId,
            @PathVariable UUID teamId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.addMember(orgId, teamId, request));
    }

    @GetMapping("/{orgId}/teams/{teamId}/members")
    public ResponseEntity<List<TeamMemberDto>> getMembers(
            @PathVariable UUID orgId,
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(organizationService.getMembers(orgId, teamId));
    }
}
