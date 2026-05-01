package direct.reflect.facilitator.configurator;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImporterService {

    private final RetroTemplateRepository retroTemplateRepository;
    private final RetroStageRepository retroStageRepository;
    private final RetroStepRepository retroStepRepository;
    private final ObjectMapper objectMapper;

    private static final tools.jackson.core.type.TypeReference<Map<String, Object>> MAP_TYPE_REF =
            new tools.jackson.core.type.TypeReference<>() { };

    @PostConstruct
    @Profile("import")
    public void importData() {
        importRetroStages();
        importRetroSteps();
        importRetroTemplates();
    }

    private void importRetroStages() {
        try {
            log.info("Starting import of retro stages...");
            ClassPathResource resource = new ClassPathResource("retrospective_stages.csv");
            Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setHeader("mastersheetID", "Stage Name", "Duration", "Why", "What")
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setAllowMissingColumnNames(true)
                    .build();
            try (CSVParser csvParser = new CSVParser(reader, csvFormat)) {
                int count = 0;
                for (CSVRecord record : csvParser) {
                    String mastersheetIDStr = record.get("mastersheetID");
                    if (mastersheetIDStr == null || mastersheetIDStr.trim().isEmpty()) {
                        log.warn("Skipping record with empty mastersheetID.");
                        continue;
                    }

                    Integer mastersheetID = Integer.parseInt(mastersheetIDStr.trim());
                    RetroStage stage = retroStageRepository.findByMastersheetID(mastersheetID)
                            .orElse(new RetroStage());

                    if (stage.getId() == null) {
                        log.debug("Creating new stage for mastersheetID: {}", mastersheetID);
                    } else {
                        log.debug("Updating existing stage for mastersheetID: {}", mastersheetID);
                    }

                    stage.setMastersheetID(mastersheetID);
                    stage.setName(record.get("Stage Name"));

                    String durationStr = record.get("Duration");
                    if (durationStr != null && !durationStr.trim().isEmpty()) {
                        stage.setDuration(Duration.ofMinutes(Long.parseLong(durationStr.trim())));
                    }

                    String why = record.get("Why");
                    stage.setWhy(why != null && !why.trim().isEmpty() ? why.trim() : null);

                    String what = record.get("What");
                    stage.setWhat(what != null && !what.trim().isEmpty() ? what.trim() : null);

                    retroStageRepository.save(stage);
                    count++;
                }
                log.info("Successfully imported/updated {} retro stages.", count);
            }
        } catch (IOException | NumberFormatException e) {
            log.error("Failed to import retro stages from CSV", e);
        }
    }

    public void importRetroTemplates() {
        if (retroTemplateRepository.count() > 0) {
            log.info("Skipping retro templates import as data already exists.");
            return;
        }
        try {
            log.info("Starting import of retro templates...");
            ClassPathResource resource = new ClassPathResource("retrospective_templates.csv");
            Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setHeader()
                    .setTrim(true)
                    .build();
            try (CSVParser csvParser = new CSVParser(reader, csvFormat)) {
                int count = 0;
                for (CSVRecord record : csvParser) {
                    RetroTemplate template = new RetroTemplate();
                    template.setName(record.get("Template Name"));
                    template.setDescription(record.get("Description"));
                    template.setMaturityLevel(Integer.parseInt(record.get("Maturity")));
                    template.setReleased(Boolean.parseBoolean(record.get("IsReleased")));

                    // Only save template if all required stages are found
                    RetroStage setStage = findStageByMastersheetId(record.get("SetTheStage_ID"));
                    RetroStage gatherStage = findStageByMastersheetId(record.get("GatherData_ID"));
                    RetroStage generateStage = findStageByMastersheetId(record.get("GenerateInsights_ID"));
                    RetroStage decideStage = findStageByMastersheetId(record.get("DecideActions_ID"));
                    RetroStage closeStage = findStageByMastersheetId(record.get("CloseRetro_ID"));

                    if (setStage != null && gatherStage != null && generateStage != null
                        && decideStage != null && closeStage != null) {
                        template.setSetTheStage(setStage);
                        template.setGatherData(gatherStage);
                        template.setGenerateInsights(generateStage);
                        template.setDecideActions(decideStage);
                        template.setCloseRetro(closeStage);

                        retroTemplateRepository.save(template);
                        count++;
                        log.info("Successfully imported template '{}'.", template.getName());
                    } else {
                        log.warn("Skipping template '{}' - one or more stages not found.", template.getName());
                    }
                }
                log.info("Successfully imported {} retro templates.", count);
            }
        } catch (IOException | NumberFormatException e) {
            log.error("Failed to import retro templates from CSV", e);
        }
    }

    private void importRetroSteps() {
        try {
            log.info("Starting import of retro steps...");
            ClassPathResource resource = new ClassPathResource("retrospective_steps.csv");
            if (!resource.exists()) {
                log.info("retrospective_steps.csv not found, skipping step import");
                return;
            }

            Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                    .setHeader("stageID", "orderIndex", "componentType",
                        "advancementTrigger", "durationSeconds", "componentConfig", "guidance")
                    .setSkipHeaderRecord(true)
                    .setTrim(true)
                    .setAllowMissingColumnNames(true)
                    .build();

            try (CSVParser csvParser = new CSVParser(reader, csvFormat)) {
                int count = 0;
                for (CSVRecord record : csvParser) {
                    String stageIdStr = record.get("stageID");
                    if (stageIdStr == null || stageIdStr.trim().isEmpty()) {
                        log.warn("Skipping record with empty stageID.");
                        continue;
                    }

                    Integer stageId = Integer.parseInt(stageIdStr.trim());
                    RetroStage stage = retroStageRepository.findByMastersheetID(stageId).orElse(null);

                    if (stage == null) {
                        log.warn("Skipping step - stage with mastersheetID {} not found", stageId);
                        continue;
                    }

                    RetroStep step = new RetroStep();
                    step.setRetroStage(stage);

                    String orderIndexStr = record.get("orderIndex");
                    step.setOrderIndex(orderIndexStr != null && !orderIndexStr.trim().isEmpty()
                        ? Integer.parseInt(orderIndexStr.trim()) : 1);

                    // ComponentType (required)
                    String componentTypeStr = record.get("componentType");
                    if (componentTypeStr != null && !componentTypeStr.trim().isEmpty()) {
                        step.setComponentType(ComponentType.valueOf(componentTypeStr.trim()));
                    } else {
                        step.setComponentType(ComponentType.MULTI_COLUMN_BOARD); // Default
                    }

                    // AdvancementTrigger (optional, defaults to FACILITATOR_CLICK)
                    String advancementTriggerStr = record.get("advancementTrigger");
                    if (advancementTriggerStr != null && !advancementTriggerStr.trim().isEmpty()) {
                        step.setAdvancementTrigger(AdvancementTrigger.valueOf(advancementTriggerStr.trim()));
                    } else {
                        step.setAdvancementTrigger(AdvancementTrigger.FACILITATOR_CLICK);
                    }

                    // Duration
                    String durationStr = record.get("durationSeconds");
                    step.setDurationSeconds(durationStr != null && !durationStr.trim().isEmpty()
                        ? Integer.parseInt(durationStr.trim()) : 0);

                    // ComponentConfig (JSON string to Map)
                    String componentConfigStr = record.get("componentConfig");
                    if (componentConfigStr != null && !componentConfigStr.trim().isEmpty()) {
                        try {
                            Map<String, Object> configMap = objectMapper.readValue(
                                componentConfigStr.trim(),
                                MAP_TYPE_REF
                            );
                            step.setComponentConfig(configMap);
                        } catch (JacksonException e) {
                            log.warn("Failed to parse componentConfig JSON for stage {}: {}", stageId, e.getMessage());
                            step.setComponentConfig(new HashMap<>());
                        }
                    } else {
                        step.setComponentConfig(new HashMap<>());
                    }

                    // Instructions (guidance text for chatbox)
                    String instructions = record.get("guidance");
                    step.setInstructions(instructions != null && !instructions.trim().isEmpty() ? instructions.trim() : null);

                    retroStepRepository.save(step);
                    count++;
                    log.debug("Imported step: {} for stage: {}", step.getComponentType(), stage.getName());
                }
                log.info("Successfully imported {} retro steps.", count);
            }
        } catch (IOException | NumberFormatException e) {
            log.error("Failed to import retro steps from CSV", e);
        }
    }

    private RetroStage findStageByMastersheetId(String id) {
        try {
            if (id == null || id.trim().isEmpty()) {
                return null;
            }
            return retroStageRepository.findByMastersheetID(Integer.parseInt(id.trim())).orElse(null);
        } catch (NumberFormatException e) {
            log.error("Invalid mastersheetID format: {}", id, e);
            return null;
        }
    }
}
