package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
public @interface TokenFilterDef {
    Class<?> factory();
    Parameter[] params() default {};
}
