package direct.reflect.facilitator.configurator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentTypeExtensionTest {

    @Test
    void shouldContainNewComponentTypes() {
        assertNotNull(ComponentType.valueOf("SMART_ACTION_BUILDER"));
        assertNotNull(ComponentType.valueOf("ACTION_REVIEW"));
    }

    @Test
    void shouldPreserveExistingComponentTypes() {
        assertNotNull(ComponentType.valueOf("MULTI_COLUMN_BOARD"));
        assertNotNull(ComponentType.valueOf("RATING_SCALE"));
        assertNotNull(ComponentType.valueOf("HISTOGRAM_CHART"));
        assertNotNull(ComponentType.valueOf("GUIDANCE_MESSAGE"));
        assertNotNull(ComponentType.valueOf("VISUAL_LAYOUT"));
    }
}
