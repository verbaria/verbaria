package org.verbaria.server.headless.service.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HApplicationConfiguration;
import org.verbaria.server.headless.repository.ApplicationConfigurationRepository;

@Service
public class AiSettingsService {

    private final ApplicationConfigurationRepository repo;

    public AiSettingsService(ApplicationConfigurationRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public String get(String key, String fallback) {
        return repo.findByKey(key)
                .map(HApplicationConfiguration::getValue)
                .filter(s -> s != null && !s.isBlank())
                .orElse(fallback);
    }

    @Transactional(readOnly = true)
    public String getOrNull(String key) {
        return repo.findByKey(key)
                .map(HApplicationConfiguration::getValue)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
    }

    @Transactional
    public void set(String key, String value) {
        HApplicationConfiguration row = repo.findByKey(key)
                .orElseGet(() -> {
                    HApplicationConfiguration r = new HApplicationConfiguration();
                    r.setKey(key);
                    return r;
                });
        row.setValue(value == null ? "" : value);
        repo.save(row);
    }
}
