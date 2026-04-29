package direct.reflect.facilitator.common.ids;

import org.hibernate.annotations.IdGeneratorType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation to mark a UUID field to be generated with a UUIDv7-like strategy
 * using the UuidV7Generator.
 */
@IdGeneratorType(UuidV7Generator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD}) // Can be applied to fields or getter methods
public @interface GeneratedUuidV7 {
}
