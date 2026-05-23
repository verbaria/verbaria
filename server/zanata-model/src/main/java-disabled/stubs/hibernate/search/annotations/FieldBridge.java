package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
public @interface FieldBridge {
    Class<?> impl() default void.class;
    Parameter[] params() default {};
    String name() default "";
}
