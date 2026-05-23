package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface FullTextFilterDef {
    String name();
    Class<?> impl();
    FilterCacheModeType cache() default FilterCacheModeType.INSTANCE_AND_DOCIDSETRESULTS;
}
