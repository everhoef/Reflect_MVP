package direct.reflect.facilitator.service;

import org.springframework.stereotype.Service;
import direct.reflect.facilitator.domain.entity.RetroSession;
import direct.reflect.facilitator.domain.entity.RetroStage;
import direct.reflect.facilitator.domain.entity.RetroStep;
import direct.reflect.facilitator.domain.entity.RetroTemplate;
import direct.reflect.facilitator.domain.enums.RetroPhase;
import direct.reflect.facilitator.exception.RetroSessionNotFoundException;
import direct.reflect.facilitator.exception.RetroTemplateNotFoundException;
import direct.reflect.facilitator.repository.RetroSessionRepository;
import direct.reflect.facilitator.repository.RetroStepRepository;
import direct.reflect.facilitator.repository.RetroTemplateRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.transaction.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RetroSessionService {
    private final RetroSessionRepository sessionRepository;
    private final RetroTemplateRepository templateRepository;
    private final RetroStepRepository stepRepository;

    @Transactional
    public RetroSession createNewSession(String sessionName) {
        // RetroTemplate template = templateRepository.findById(templateId)
        //     .orElseThrow(() -> new RetroTemplateNotFoundException(templateId));
            
        RetroSession session = new RetroSession();
        session.setName(sessionName);
        session.setCreatedAt(LocalDateTime.now());
        // session.setTemplate(template);
        session.setPhase(RetroPhase.LOBBY);
        
        return sessionRepository.save(session);
    }

    @Transactional
    public void startSession(UUID retroId) {
        RetroSession session = getSessionOrThrow(retroId);
        session.setPhase(RetroPhase.SET_THE_STAGE);
        sessionRepository.save(session);
        
        // Start first step
        advanceToNextStep(retroId);
        // TODO: Notify participants
    }

    @Transactional
    public void advanceToNextStep(UUID retroId) {
        RetroSession session = getSessionOrThrow(retroId);
        RetroStage currentStage = session.getCurrentStage();
        List<RetroStep> stageSteps = stepRepository.findByStageId(currentStage.getId());
        
        if (session.getCurrentStepIndex() + 1 < stageSteps.size()) {
            // More steps in current stage
            session.setCurrentStepIndex(session.getCurrentStepIndex() + 1);
        } else {
            // No more steps, advance to next phase
            RetroPhase nextPhase = session.getPhase().next();
            session.setPhase(nextPhase);
            session.setCurrentStepIndex(-1);
            
            if (nextPhase == RetroPhase.COMPLETED) {
                session.setFinishedAt(LocalDateTime.now());
            }
        }

        sessionRepository.save(session);
    }

    @Transactional
    public RetroStep getCurrentStep(UUID retroId) {
        RetroSession session = getSessionOrThrow(retroId);
        RetroStage currentStage = session.getCurrentStage();
        if (currentStage == null || session.getCurrentStepIndex() < 0) {
            return null;
        }
        
        List<RetroStep> steps = stepRepository.findByStageId(currentStage.getId());
        return steps.isEmpty() ? null : steps.get(session.getCurrentStepIndex());
    }

    public boolean sessionExists(UUID retroId) {
        return sessionRepository.findByRetroId(retroId).isPresent();
    }

    public String getSessionName(UUID retroId) {
        return getSessionOrThrow(retroId).getName();
    }

    public RetroSession getSessionByRetroId(UUID retroId) {
        return getSessionOrThrow(retroId);
    }

    private RetroSession getSessionOrThrow(UUID retroId) {
        return sessionRepository.findByRetroId(retroId)
            .orElseThrow(() -> new RetroSessionNotFoundException(retroId));
    }

    public List<RetroTemplate> getAvailableTemplates() {
        return templateRepository.findAll();
    }

    public RetroTemplate getTemplateById(Long templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new RetroTemplateNotFoundException(templateId));
    }
}
