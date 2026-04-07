package direct.reflect.facilitator.configurator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ComponentTypeExtensionTest {

    @Test
    void componentTypeSurface_matchesSupportedWizardComponents() {
        assertThat(ComponentType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "MULTI_COLUMN_BOARD",
                        "RATING_SCALE",
                        "HISTOGRAM_CHART",
                        "GUIDANCE_MESSAGE",
                        "VISUAL_LAYOUT",
                        "SMART_ACTION_BUILDER",
                        "ACTION_REVIEW");
    }
}
