package direct.reflect.facilitator.common.ids;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

import java.io.Serializable;

/**
 * Custom Hibernate ID generator for UUIDv7-like time-ordered UUIDs
 * using the com.fasterxml.uuid.java-uuid-generator library.
 */
public class UuidV7Generator implements IdentifierGenerator {

    private static final TimeBasedEpochGenerator UUID_V7_EPOCH_GENERATOR = Generators.timeBasedEpochGenerator();

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) {
        return UUID_V7_EPOCH_GENERATOR.generate();
    }
}
