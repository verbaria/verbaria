package org.zanata.spring.web.rest;

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
import org.zanata.spring.repository.ApplicationConfigurationRepository;

/**
 * Reads/writes the HApplicationConfiguration key/value table consumed by
 * the React admin screen at /rest/admin/server-settings.
 */
@RestController
@RequestMapping("/rest/admin/server-settings")
public class AdminController {

    /** (key, default-value) pairs.  The default tells the JSON renderer
     *  which type to coerce ('false' for booleans, '0' for integers). */
    private static final List<Property> KNOWN_PROPERTIES = List.of(
            new Property(HApplicationConfiguration.KEY_ADMIN_EMAIL, ""),
            new Property(HApplicationConfiguration.KEY_ALLOW_ANONYMOUS_USER, false),
            new Property(HApplicationConfiguration.KEY_AUTO_ACCEPT_TRANSLATOR, false),
            new Property(HApplicationConfiguration.KEY_DISPLAY_USER_EMAIL, false),
            new Property(HApplicationConfiguration.KEY_DOMAIN, ""),
            new Property(HApplicationConfiguration.KEY_EMAIL_FROM_ADDRESS, ""),
            new Property(HApplicationConfiguration.KEY_EMAIL_LOG_EVENTS, false),
            new Property(HApplicationConfiguration.KEY_EMAIL_LOG_LEVEL, ""),
            new Property(HApplicationConfiguration.KEY_GRAVATAR_RATING, ""),
            new Property(HApplicationConfiguration.KEY_HELP_URL, ""),
            new Property(HApplicationConfiguration.KEY_HOST, ""),
            new Property(HApplicationConfiguration.KEY_LOG_DESTINATION_EMAIL, ""),
            new Property(HApplicationConfiguration.KEY_MAX_ACTIVE_REQ_PER_API_KEY, 2),
            new Property(HApplicationConfiguration.KEY_MAX_CONCURRENT_REQ_PER_API_KEY, 2),
            new Property(HApplicationConfiguration.KEY_MAX_FILES_PER_UPLOAD, 100),
            new Property(HApplicationConfiguration.KEY_PERMITTED_USER_EMAIL_DOMAIN, ""),
            new Property(HApplicationConfiguration.KEY_PIWIK_URL, ""),
            new Property(HApplicationConfiguration.KEY_PIWIK_IDSITE, ""),
            new Property(HApplicationConfiguration.KEY_REGISTER, ""),
            new Property(HApplicationConfiguration.KEY_TERMS_CONDITIONS_URL, ""),
            new Property(HApplicationConfiguration.KEY_TM_FUZZY_BANDS, ""));

    private final ApplicationConfigurationRepository repo;

    public AdminController(ApplicationConfigurationRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Property> get() {
        List<Property> out = new ArrayList<>(KNOWN_PROPERTIES.size());
        for (Property p : KNOWN_PROPERTIES) {
            Object value = repo.findByKey(p.key())
                    .map(HApplicationConfiguration::getValue)
                    .map(s -> coerce(s, p.defaultValue()))
                    .orElse(p.defaultValue());
            out.add(new Property(p.key(), value));
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
