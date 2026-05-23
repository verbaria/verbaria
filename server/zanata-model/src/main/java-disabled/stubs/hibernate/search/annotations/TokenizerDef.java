package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
public @interface TokenizerDef {
    String name() default "";
    Class<?> factory();
    Parameter[] params() default {};
}
