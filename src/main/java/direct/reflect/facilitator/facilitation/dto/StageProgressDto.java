package direct.reflect.facilitator.facilitation.dto;

import direct.reflect.facilitator.facilitation.session.RetroPhase;
import direct.reflect.facilitator.facilitation.session.RetroSession;

import java.util.List;

/**
 * Progress status of a retrospective stage for rendering the stage progress bar.
 * Maps the 5 active stages to RetroPhase: SET_THE_STAGE, GATHER_DATA, GENERATE_INSIGHTS, DECIDE_ACTIONS, CLOSE_RETRO.
 */
public record StageProgressDto(
    String name,
    int number,
    Status status,
    RetroPhase phase
) {
    public enum Status {
        COMPLETE,
        IN_PROGRESS,
        TO_DO
    }

    private static final List<StageDefinition> STAGE_DEFINITIONS = List.of(
        new StageDefinition("Set the Stage", 1, RetroPhase.SET_THE_STAGE),
        new StageDefinition("Gather Data", 2, RetroPhase.GATHER_DATA),
        new StageDefinition("Generate Insights", 3, RetroPhase.GENERATE_INSIGHTS),
        new StageDefinition("Decide Actions", 4, RetroPhase.DECIDE_ACTIONS),
        new StageDefinition("Close Retro", 5, RetroPhase.CLOSE_RETRO)
    );

    private record StageDefinition(String name, int number, RetroPhase phase) { }

    public static List<StageProgressDto> forSession(RetroSession session) {
        RetroPhase currentPhase = session.getPhase();

        return STAGE_DEFINITIONS.stream()
            .map(def -> {
                Status status = computeStatus(currentPhase, def.phase());
                return new StageProgressDto(def.name(), def.number(), status, def.phase());
            })
            .toList();
    }

    private static Status computeStatus(RetroPhase currentPhase, RetroPhase stagePhase) {
        if (!currentPhase.isActivePhase()) {
            if (currentPhase == RetroPhase.COMPLETED) {
                return Status.COMPLETE;
            }
            return Status.TO_DO;
        }

        int currentOrdinal = currentPhase.ordinal();
        int stageOrdinal = stagePhase.ordinal();

        if (currentOrdinal > stageOrdinal) {
            return Status.COMPLETE;
        } else if (currentOrdinal == stageOrdinal) {
            return Status.IN_PROGRESS;
        } else {
            return Status.TO_DO;
        }
    }
}
