package jakarta.validation.constraints;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE_USE})
public @interface CopiedValues {

    String value();

    String[] strings() default {};

    Mode[] modes() default {};

    Class<?>[] types() default {};

    Class<?>[] emptyTypes();

    boolean enabled();

    byte byteValue();

    short shortValue();

    int intValue();

    long longValue();

    float floatValue();

    double doubleValue();

    char charValue();

    enum Mode {
        FIRST,
        SECOND
    }

}