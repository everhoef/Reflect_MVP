package direct.reflect.facilitator.mapper;

import org.springframework.stereotype.Component;
import direct.reflect.facilitator.dto.RetrospectiveSessionResponseDTO;
import direct.reflect.facilitator.dto.RetrospectiveSessionJoinDTO;
import direct.reflect.facilitator.entity.RetrospectiveSession;
import direct.reflect.facilitator.entity.Participant;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RetrospectiveSessionMapper {
  public RetrospectiveSessionResponseDTO toResponseDTO(RetrospectiveSession session) {
    RetrospectiveSessionResponseDTO dto = new RetrospectiveSessionResponseDTO();
    dto.setId(session.getRetroId());
    dto.setCreatedAt(session.getCreatedAt());
    dto.setFacilitator(session.getFacilitator());
    dto.setParticipants(mapParticipants(session.getParticipants()));
    return dto;
  }

  private List<String> mapParticipants(List<Participant> participants) {
    return participants.stream().map(Participant::getUsername).collect(Collectors.toList());
  }
}
