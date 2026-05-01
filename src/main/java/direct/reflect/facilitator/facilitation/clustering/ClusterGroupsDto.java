package direct.reflect.facilitator.facilitation.clustering;

import direct.reflect.facilitator.facilitation.dto.ColumnResponseDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ClusterGroupsDto(
        Map<UUID, List<ColumnResponseDto>> clustered,
        List<ColumnResponseDto> unclustered
) { }
