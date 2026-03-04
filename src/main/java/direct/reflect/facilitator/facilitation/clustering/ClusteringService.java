package direct.reflect.facilitator.facilitation.clustering;

import com.fasterxml.uuid.Generators;
import direct.reflect.facilitator.common.exception.ResourceNotFoundException;
import direct.reflect.facilitator.eventing.EventService;
import direct.reflect.facilitator.eventing.RetroEvent;
import direct.reflect.facilitator.facilitation.dto.ColumnResponseDto;
import direct.reflect.facilitator.facilitation.response.ParticipantResponse;
import direct.reflect.facilitator.facilitation.response.ParticipantResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
/**
 * Manages clustering of participant responses within retrospective steps.
 * Supports merging responses into clusters, unmerging, renaming clusters,
 * and retrieving clustered/unclustered response groups.
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ClusteringService {

    private final ParticipantResponseRepository responseRepository;
    private final EventService eventService;

    public UUID mergeResponses(UUID retroId, Long stepId, List<UUID> responseIds) {
        UUID clusterId = Generators.timeBasedEpochGenerator().generate();
        List<ParticipantResponse> responses = responseRepository.findAllById(responseIds);
        if (responses.size() != responseIds.size()) {
            throw new IllegalArgumentException("Some response IDs were not found");
        }
        responses.forEach(r -> {
            if (!r.getParticipant().getSession().getId().equals(retroId)) {
                throw new IllegalArgumentException("Response " + r.getId() + " does not belong to retro " + retroId);
            }
        });
        responses.forEach(r -> r.setClusterId(clusterId));
        responseRepository.saveAll(responses);
        try {
            eventService.publish(RetroEvent.responsesRevealed(retroId, "facilitator", stepId));
        } catch (Exception e) {
            log.debug("Failed to publish cluster merge event: {}", e.getMessage());
        }
        return clusterId;
    }

    public void unmergeResponse(UUID retroId, Long stepId, UUID responseId) {
        ParticipantResponse response = responseRepository.findById(responseId)
                .orElseThrow(() -> new ResourceNotFoundException("Response not found: " + responseId));
        response.setClusterId(null);
        response.setClusterName(null);
        responseRepository.save(response);
        try {
            eventService.publish(RetroEvent.responsesRevealed(retroId, "facilitator", stepId));
        } catch (Exception e) {
            log.debug("Failed to publish cluster unmerge event: {}", e.getMessage());
        }
    }

    public void renameCluster(UUID retroId, Long stepId, UUID clusterId, String newName) {
        List<ParticipantResponse> responses = responseRepository.findByRetroIdAndClusterId(retroId, clusterId);
        if (responses.isEmpty()) {
            throw new ResourceNotFoundException("Cluster not found: " + clusterId);
        }
        responses.forEach(r -> r.setClusterName(newName));
        responseRepository.saveAll(responses);
        try {
            eventService.publish(RetroEvent.responsesRevealed(retroId, "facilitator", stepId));
        } catch (Exception e) {
            log.debug("Failed to publish cluster rename event: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public ClusterGroupsDto getClusters(UUID retroId) {
        List<ColumnResponseDto> unclustered = responseRepository.findByRetroIdAndClusterIdIsNull(retroId)
                .stream()
                .map(ColumnResponseDto::from)
                .collect(Collectors.toList());

        Map<UUID, List<ColumnResponseDto>> clustered = new LinkedHashMap<>();
        responseRepository.findByRetroIdAndClusterIdIsNotNull(retroId)
                .forEach(r -> clustered
                        .computeIfAbsent(r.getClusterId(), k -> new ArrayList<>())
                        .add(ColumnResponseDto.from(r)));

        return new ClusterGroupsDto(clustered, unclustered);
    }
}
