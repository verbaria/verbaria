package org.zanata.spring.vaadin.component;

import java.util.List;

import com.vaadin.experimental.Feature;
import com.vaadin.experimental.FeatureFlagProvider;

/**
 * Registers the {@code breadcrumbsComponent} experimental flag with
 * Vaadin's feature-flag SPI so the generated {@code vaadin-featureflags.js}
 * file includes it and the runtime activator can switch it on when
 * {@code vaadin-featureflags.properties} sets
 * {@code com.vaadin.experimental.breadcrumbsComponent=true}.
 *
 * <p>Same pattern that Vaadin's official {@code BadgeFeatureFlagProvider}
 * uses for {@code badgeComponent}. Wired via
 * {@code META-INF/services/com.vaadin.experimental.FeatureFlagProvider}.
 * Drop this class when Vaadin Flow ships an official wrapper that
 * already registers the flag.</p>
 */
public class BreadcrumbsFeatureFlagProvider implements FeatureFlagProvider {

    public static final Feature BREADCRUMBS_COMPONENT = new Feature(
            "Breadcrumbs component",
            "breadcrumbsComponent",
            "https://github.com/vaadin/web-components/tree/main/packages/breadcrumbs",
            false,
            Breadcrumbs.class.getName());

    @Override
    public List<Feature> getFeatures() {
        return List.of(BREADCRUMBS_COMPONENT);
    }
}
