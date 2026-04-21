package direct.reflect.facilitator.facilitation;
 
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import direct.reflect.facilitator.facilitation.session.RetroSession;
import direct.reflect.facilitator.facilitation.session.RetroSessionRepository;
import direct.reflect.facilitator.facilitation.session.RetroSyncVersionService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class RetroSyncVersionServiceTest {

    @Mock
    private RetroSessionRepository retroSessionRepository;

    @InjectMocks
    private RetroSyncVersionService retroSyncVersionService;

    @Test
    void bumpSyncVersion_incrementsExistingValueMonotonically() {
        UUID retroId = UUID.randomUUID();
        RetroSession retroSession = new RetroSession();
        retroSession.setSyncVersion(4L);

        when(retroSessionRepository.findById(retroId)).thenReturn(Optional.of(retroSession));

        long nextVersion = retroSyncVersionService.bumpSyncVersion(retroId);

        assertThat(nextVersion).isEqualTo(5L);
        assertThat(retroSession.getSyncVersion()).isEqualTo(5L);
        verify(retroSessionRepository).save(retroSession);
    }

    @Test
    void bumpSyncVersion_treatsNullAsZero() {
        UUID retroId = UUID.randomUUID();
        RetroSession retroSession = new RetroSession();
        retroSession.setSyncVersion(null);

        when(retroSessionRepository.findById(retroId)).thenReturn(Optional.of(retroSession));

        long nextVersion = retroSyncVersionService.bumpSyncVersion(retroId);

        assertThat(nextVersion).isEqualTo(1L);
        assertThat(retroSession.getSyncVersion()).isEqualTo(1L);
        verify(retroSessionRepository).save(retroSession);
    }
}
