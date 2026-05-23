package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE})
public @interface AnalyzerDef {
    String name();
    TokenizerDef tokenizer();
    TokenFilterDef[] filters() default {};
    CharFilterDef[] charFilters() default {};
}
