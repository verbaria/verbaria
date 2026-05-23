package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD})
public @interface Field {
    String name() default "";
    Store store() default Store.NO;
    Index index() default Index.YES;
    Analyze analyze() default Analyze.YES;
    Norms norms() default Norms.YES;
    TermVector termVector() default TermVector.NO;
    Analyzer analyzer() default @Analyzer(definition = "");
    FieldBridge bridge() default @FieldBridge(name = "");
    Boost boost() default @Boost(value = 1.0f);
}
