package core.framework.inject;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author Jordan
 */
@Target(FIELD)
@Retention(RUNTIME)
public @interface Value {
    String value();

    boolean json() default false;

    char delimiter() default '\0';
}
