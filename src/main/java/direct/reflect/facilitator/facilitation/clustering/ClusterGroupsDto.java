package direct.reflect.facilitator.facilitation.clustering;

import direct.reflect.facilitator.facilitation.dto.ColumnResponseDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ClusterGroupsDto(
        Map<UUID, List<ColumnResponseDto>> clustered,
        List<ColumnResponseDto> unclustered
) {

    public ClusterGroupsDto {
        clustered = clustered != null ? Map.copyOf(clustered) : null;
        unclustered = unclustered != null ? List.copyOf(unclustered) : null;
    }

    @Override
    public Map<UUID, List<ColumnResponseDto>> clustered() {
        return clustered != null ? Map.copyOf(clustered) : null;
    }

    @Override
    public List<ColumnResponseDto> unclustered() {
        return unclustered != null ? List.copyOf(unclustered) : null;
    }
}
