package org.zanata.adapter.consulo;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * Matches only when Vaadin Flow is on the classpath, so Vaadin-based UI
 * contributions are skipped in the headless context (which has no Vaadin) and
 * registered only in the server-ui context.
 */
class VaadinPresent implements Condition {

    @Override
    public boolean matches(ConditionContext context,
            AnnotatedTypeMetadata metadata) {
        return ClassUtils.isPresent("com.vaadin.flow.component.Component",
                context.getClassLoader());
    }
}
