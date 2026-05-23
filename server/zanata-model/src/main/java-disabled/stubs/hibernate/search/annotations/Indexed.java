package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Indexed {
    String index() default "";
    Class<?> interceptor() default DefaultIndexingInterceptor.class;
    class DefaultIndexingInterceptor {}
}
