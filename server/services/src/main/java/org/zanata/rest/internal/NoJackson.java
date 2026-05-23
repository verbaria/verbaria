/* Local replacement for org.jboss.resteasy.annotations.providers.NoJackson (removed in RESTEasy 6). */
package org.zanata.rest.internal;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER})
public @interface NoJackson {}
