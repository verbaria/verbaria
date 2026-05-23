package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClassBridge {
    String name() default "";
    Class<?> impl() default void.class;
    Store store() default Store.NO;
    Index index() default Index.YES;
    Analyze analyze() default Analyze.YES;
    Norms norms() default Norms.YES;
    Parameter[] params() default {};
    Analyzer analyzer() default @Analyzer(definition = "");
}
