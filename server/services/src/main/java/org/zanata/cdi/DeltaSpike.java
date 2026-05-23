package org.zanata.cdi;

import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Local replacement for {@code org.apache.deltaspike.core.api.common.DeltaSpike}
 * which was removed/relocated in DeltaSpike 2.0. Behaves as a plain CDI
 * qualifier so existing @Inject sites compile.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
public @interface DeltaSpike {
}
