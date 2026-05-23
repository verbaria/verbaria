package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface IndexedEmbedded {
    String prefix() default "";
    String[] includePaths() default {};
    int depth() default Integer.MAX_VALUE;
    Class<?> targetElement() default void.class;
}
