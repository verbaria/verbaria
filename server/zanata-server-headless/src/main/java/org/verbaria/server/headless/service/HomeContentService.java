package org.verbaria.server.headless.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HApplicationConfiguration;
import org.verbaria.server.headless.repository.ApplicationConfigurationRepository;

/**
 * Reads and writes the admin-edited homepage Markdown stored in
 * {@link HApplicationConfiguration} under the legacy key
 * {@code pages.home.content} ({@link HApplicationConfiguration#KEY_HOME_CONTENT}).
 *
 * <p>The same row was used by the JSF {@code HomePage} bean in the WildFly
 * stack, so existing rows survive the migration intact.</p>
 */
@Service
public class HomeContentService {

    private final ApplicationConfigurationRepository repository;

    public HomeContentService(ApplicationConfigurationRepository repository) {
        this.repository = repository;
    }

    /** Raw Markdown source (never null; "" if nothing has ever been saved). */
    @Transactional(readOnly = true)
    public String getMarkdown() {
        return repository.findByKey(HApplicationConfiguration.KEY_HOME_CONTENT)
                .map(HApplicationConfiguration::getValue)
                .orElse("");
    }

    /** Upsert the Markdown source. Empty / null clears the row to "". */
    @Transactional
    public void save(String markdown) {
        String value = markdown == null ? "" : markdown;
        HApplicationConfiguration row = repository
                .findByKey(HApplicationConfiguration.KEY_HOME_CONTENT)
                .orElseGet(() -> {
                    HApplicationConfiguration fresh = new HApplicationConfiguration();
                    fresh.setKey(HApplicationConfiguration.KEY_HOME_CONTENT);
                    return fresh;
                });
        row.setValue(value);
        repository.save(row);
    }

    /**
     * Whether the public home page at {@code /} is enabled. When disabled the
     * root route redirects to the projects view. Defaults to enabled when no
     * row exists.
     */
    @Transactional(readOnly = true)
    public boolean isHomeEnabled() {
        return repository.findByKey(HApplicationConfiguration.KEY_HOME_ENABLED)
                .map(HApplicationConfiguration::getValue)
                .map(v -> !"false".equalsIgnoreCase(v.trim()))
                .orElse(true);
    }

    /** Enable or disable the public home page. */
    @Transactional
    public void setHomeEnabled(boolean enabled) {
        HApplicationConfiguration row = repository
                .findByKey(HApplicationConfiguration.KEY_HOME_ENABLED)
                .orElseGet(() -> {
                    HApplicationConfiguration fresh = new HApplicationConfiguration();
                    fresh.setKey(HApplicationConfiguration.KEY_HOME_ENABLED);
                    return fresh;
                });
        row.setValue(Boolean.toString(enabled));
        repository.save(row);
    }
}
