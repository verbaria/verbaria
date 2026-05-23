package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
public @interface CharFilterDef {
    Class<?> factory();
    Parameter[] params() default {};
}
