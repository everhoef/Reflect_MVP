package direct.reflect.facilitator.configurator;

import direct.reflect.facilitator.common.exception.RetroTemplateNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for managing RetroTemplate entities.
 *
 * Handles template retrieval and selection logic.
 * Future: Will contain configurator algorithm for intelligent template selection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetroTemplateService {

    private final RetroTemplateRepository templateRepository;

    private static final Long DEFAULT_TEMPLATE_ID = 1L;

    /**
     * Get a template by its ID.
     *
     * @param templateId the template ID
     * @return the RetroTemplate
     * @throws RetroTemplateNotFoundException if template not found
     */
    public RetroTemplate getTemplateById(Long templateId) {
        return templateRepository.findById(templateId)
            .orElseThrow(() -> new RetroTemplateNotFoundException(templateId));
    }

    /**
     * Get the default system template.
     *
     * @return the default RetroTemplate
     * @throws RetroTemplateNotFoundException if default template not found
     */
    public RetroTemplate getDefaultTemplate() {
        return templateRepository.findById(DEFAULT_TEMPLATE_ID)
            .orElseThrow(() -> new RetroTemplateNotFoundException(DEFAULT_TEMPLATE_ID));
    }

    /**
     * Select which template to use for a new retrospective session.
     *
     * Currently returns the default template.
     * Future: Will implement configurator algorithm based on team answers/context.
     *
     * @return the selected RetroTemplate
     */
    public RetroTemplate selectTemplateForSession() {
        log.debug("Selecting template for new session - using default template");
        return getDefaultTemplate();
    }

    /**
     * Get all available templates in the system.
     *
     * @return list of all RetroTemplates
     */
    public List<RetroTemplate> getAvailableTemplates() {
        return templateRepository.findAll();
    }
}
