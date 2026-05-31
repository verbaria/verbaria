package org.verbaria.server.headless.settings;

import java.util.List;

/**
 * The server settings the Spring server consumes — the single source of truth
 * for each setting's storage key, value type, default, optional enumerated
 * values, and admin-UI section.
 *
 * <p>Each constant <em>is</em> the key, so consumers reference
 * {@code ServerSetting.HOST.key()} instead of a separate string constant, and
 * both admin surfaces (the Vaadin settings view and the React config API) build
 * generically from {@link #values()}.</p>
 */
public enum ServerSetting {

    /**
     * Whether self-service registration is allowed. Stored as
     * {@code "true"}/{@code "false"}; unset means allowed.
     */
    ALLOW_REGISTRATION("serverSettings.section.access", "register.enabled",
            Boolean.class, Boolean.TRUE, null),
    EMAIL_FROM("serverSettings.section.email", "email.from.addr",
            String.class, "", null),
    HOST("serverSettings.section.urls", "host.url",
            String.class, "", null),
    GRAVATAR_RATING("serverSettings.section.urls", "gravatar.rating",
            String.class, "g", List.of("g", "pg", "r", "x"));

    private final String section;
    private final String key;
    private final Class<?> type;
    private final Object defaultValue;
    private final List<String> options;

    ServerSetting(String section, String key, Class<?> type,
                  Object defaultValue, List<String> options) {
        this.section = section;
        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.options = options;
    }

    /** i18n key of the admin-UI block heading this setting belongs to. */
    public String section() {
        return section;
    }

    /** The {@code HApplicationConfiguration} storage key. */
    public String key() {
        return key;
    }

    /** {@code Boolean}/{@code Integer}/{@code String}. */
    public Class<?> type() {
        return type;
    }

    /** Value used when the setting is unset. */
    public Object defaultValue() {
        return defaultValue;
    }

    /** Allowed values to render as a dropdown, or {@code null} for free input. */
    public List<String> options() {
        return options;
    }
}
