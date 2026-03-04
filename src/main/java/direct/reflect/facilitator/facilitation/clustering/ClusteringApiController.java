package direct.reflect.facilitator.facilitation.clustering;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/retro/{retroId}/step/{stepId}")
@RequiredArgsConstructor
@Slf4j
public class ClusteringApiController {

    private final ClusteringService clusteringService;

    public record MergeRequest(@NotEmpty List<UUID> responseIds) {}

    public record UnmergeRequest(@NotNull UUID responseId) {}

    public record RenameClusterRequest(@NotBlank @Size(max = 100) String name) {}

    @PostMapping("/cluster/merge")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<Map<String, UUID>> merge(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @Valid @RequestBody MergeRequest request) {
        UUID clusterId = clusteringService.mergeResponses(retroId, stepId, request.responseIds());
        return ResponseEntity.ok(Map.of("clusterId", clusterId));
    }

    @PostMapping("/cluster/unmerge")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<Void> unmerge(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @Valid @RequestBody UnmergeRequest request) {
        clusteringService.unmergeResponse(retroId, stepId, request.responseId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/cluster/{clusterId}/name")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<Void> renameCluster(
            @PathVariable UUID retroId,
            @PathVariable Long stepId,
            @PathVariable UUID clusterId,
            @Valid @RequestBody RenameClusterRequest request) {
        clusteringService.renameCluster(retroId, stepId, clusterId, request.name());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/clusters")
    @PreAuthorize("@participantService.canAccessRetro(#retroId)")
    public ResponseEntity<ClusterGroupsDto> getClusters(
            @PathVariable UUID retroId,
            @PathVariable Long stepId) {
        return ResponseEntity.ok(clusteringService.getClusters(retroId));
    }
}
