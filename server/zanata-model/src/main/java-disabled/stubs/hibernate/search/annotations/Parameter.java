package org.hibernate.search.annotations;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
public @interface Parameter {
    String name();
    String value();
}
