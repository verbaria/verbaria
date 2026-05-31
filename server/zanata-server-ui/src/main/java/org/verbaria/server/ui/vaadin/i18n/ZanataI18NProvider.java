package org.verbaria.server.ui.vaadin.i18n;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.vaadin.flow.i18n.I18NProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Vaadin {@link I18NProvider} backed by {@code translations.properties} files
 * under {@code src/main/resources/i18n/}.
 *
 * <p><b>Supported locales</b> are discovered at startup by scanning the
 * classpath for {@code i18n/translations*.properties} — drop a new file
 * (e.g. {@code translations_ru.properties}) and the locale appears
 * automatically, no code change needed. The bundle without a suffix is
 * always English.</p>
 *
 * <p><b>Caching</b>: each successfully loaded {@link ResourceBundle} is held
 * in a {@link ConcurrentHashMap} keyed by {@link Locale}, so a hot
 * translation lookup is just a map get + bundle lookup. {@code getBundle}
 * does have its own internal cache, but going through our own map saves the
 * fallback-chain walk on misses too.</p>
 *
 * <p>Lookups fall back to {@code Locale.ENGLISH} when a key is missing in
 * the requested locale, and to {@code "!key!"} when neither bundle has it,
 * so missing translations are visible in the UI without throwing.</p>
 */
@Component
@Primary
public class ZanataI18NProvider implements I18NProvider {

    private static final Logger log = LoggerFactory.getLogger(ZanataI18NProvider.class);
    private static final String BUNDLE_BASE = "i18n.translations";
    private static final Pattern LOCALE_SUFFIX =
            Pattern.compile("translations(?:_([A-Za-z]{2,3}(?:_[A-Za-z]{2,3})?))?\\.properties");

    private final ConcurrentMap<Locale, ResourceBundle> bundleCache = new ConcurrentHashMap<>();
    private volatile List<Locale> supported = List.of(Locale.ENGLISH);

    @PostConstruct
    void discoverLocales() {
        Set<Locale> found = new LinkedHashSet<>();
        found.add(Locale.ENGLISH); // baseline (translations.properties)
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(
                    "classpath*:i18n/translations*.properties");
            for (Resource r : resources) {
                String name = r.getFilename();
                if (name == null) continue;
                Matcher m = LOCALE_SUFFIX.matcher(name);
                if (!m.matches()) continue;
                String tag = m.group(1);
                if (tag == null) continue; // baseline already added
                String[] parts = tag.split("_");
                Locale loc = parts.length == 2
                        ? new Locale(parts[0], parts[1]) : new Locale(parts[0]);
                found.add(loc);
            }
        } catch (IOException ex) {
            log.warn("Locale discovery failed; falling back to English only", ex);
        }
        this.supported = List.copyOf(found);
        log.info("Discovered i18n locales: {}", found.stream()
                .map(Locale::toLanguageTag).toList());
    }

    @Override
    public List<Locale> getProvidedLocales() {
        return supported;
    }

    @Override
    public String getTranslation(String key, Locale locale, Object... params) {
        if (key == null) return "";
        Locale lookup = locale == null ? Locale.ENGLISH : locale;
        String value = lookup(key, lookup);
        if (value == null && !Locale.ENGLISH.equals(lookup)) {
            value = lookup(key, Locale.ENGLISH);
        }
        if (value == null) {
            log.warn("Missing i18n key '{}' (locale={})", key, locale);
            return "!" + key + "!";
        }
        if (params == null || params.length == 0) return value;
        try {
            return new MessageFormat(value, lookup).format(params);
        } catch (IllegalArgumentException ex) {
            log.warn("Bad MessageFormat pattern for '{}': {}", key, ex.getMessage());
            return value + " " + Arrays.toString(params);
        }
    }

    private String lookup(String key, Locale locale) {
        ResourceBundle bundle = bundleCache.computeIfAbsent(locale, this::loadBundle);
        if (bundle == null) return null;
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            return null;
        }
    }

    /** {@code null} on failure so we don't poison the cache with a sentinel. */
    private ResourceBundle loadBundle(Locale locale) {
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE, locale);
        } catch (MissingResourceException ex) {
            log.warn("No bundle found for locale {}", locale);
            return null;
        }
    }
}
