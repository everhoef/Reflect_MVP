package direct.reflect.facilitator.facilitation.escalation;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/manager/escalations")
@PreAuthorize("hasRole('MANAGER')")
@RequiredArgsConstructor
@Tag(name = "Manager Escalation API", description = "Manager escalation inbox")
public class ManagerEscalationApiController {

    private final EscalationService escalationService;

    @GetMapping
    public ResponseEntity<List<EscalatedItemDto>> getEscalations(Authentication authentication) {
        return ResponseEntity.ok(escalationService.getManagerEscalations(authentication));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EscalatedItemDto> getEscalation(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(escalationService.getManagerEscalation(id, authentication));
    }
}
