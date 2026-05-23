package org.apache.deltaspike.core.api.exception.control;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER)
public @interface Handles {
    int ordinal() default 0;
    boolean during() default false;
    Class<? extends Throwable>[] precedence() default {};
}
