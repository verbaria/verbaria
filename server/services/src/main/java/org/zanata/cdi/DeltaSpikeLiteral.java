/**
 * Replacement for org.apache.deltaspike.core.api.literal.DeltaSpikeLiteral.
 * Annotation literal for the local @DeltaSpike CDI qualifier.
 */
package org.zanata.cdi;

import jakarta.enterprise.util.AnnotationLiteral;

public class DeltaSpikeLiteral extends AnnotationLiteral<DeltaSpike> implements DeltaSpike {
    public static final DeltaSpikeLiteral INSTANCE = new DeltaSpikeLiteral();
    private static final long serialVersionUID = 1L;
}
