package org.verbaria.server.headless.web.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.model.HApplicationConfiguration;
import org.verbaria.server.headless.repository.ApplicationConfigurationRepository;
import org.verbaria.server.headless.settings.ServerSetting;

/**
 * Reads/writes the HApplicationConfiguration key/value table consumed by
 * the React admin screen at /rest/admin/server-settings. The set of settings
 * comes from {@link ServerSetting}.
 */
@RestController
@RequestMapping("/rest/admin/server-settings")
public class AdminController {

    private final ApplicationConfigurationRepository repo;

    public AdminController(ApplicationConfigurationRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Property> get() {
        List<Property> out = new ArrayList<>(ServerSetting.values().length);
        for (ServerSetting setting : ServerSetting.values()) {
            Object value = repo.findByKey(setting.key())
                    .map(HApplicationConfiguration::getValue)
                    .map(s -> coerce(s, setting.defaultValue()))
                    .orElse(setting.defaultValue());
            out.add(new Property(setting.key(), value));
        }
        return out;
    }

    @PostMapping
    public List<Property> savePost(@RequestBody Map<String, Object> updates) {
        return doSave(updates);
    }

    @PutMapping
    public List<Property> savePut(@RequestBody Map<String, Object> updates) {
        return doSave(updates);
    }

    @Transactional
    public List<Property> doSave(Map<String, Object> updates) {
        if (updates != null) {
            updates.forEach((key, rawValue) -> {
                HApplicationConfiguration row = repo.findByKey(key).orElseGet(() -> {
                    HApplicationConfiguration n = new HApplicationConfiguration();
                    n.setKey(key);
                    return n;
                });
                row.setValue(rawValue == null ? "" : String.valueOf(rawValue));
                repo.save(row);
            });
        }
        return get();
    }

    /** Coerce stored string value to the type of the default
     *  (Boolean/Integer/String) so the React form sees the right
     *  JavaScript type. */
    private static Object coerce(String raw, Object defaultValue) {
        if (raw == null) return defaultValue;
        if (defaultValue instanceof Boolean) return Boolean.parseBoolean(raw);
        if (defaultValue instanceof Integer) {
            try { return Integer.parseInt(raw); }
            catch (NumberFormatException e) { return defaultValue; }
        }
        return raw;
    }

    public record Property(String key, Object value) {
        /** Alias for the `defaultValue` field consumed by the React
         *  explore page's PropertyWithDBKey shape. */
        public Object defaultValue() { return value; }
    }
}
