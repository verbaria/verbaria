package org.zanata.client.commands;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;

public final class ServerRestClient {

    private static final RestTemplate REST = new RestTemplate();

    private ServerRestClient() {
    }

    public static String serverVersion(URL url, String user, String key) {
        Map<String, Object> body = get(url, "rest/version", user, key,
                Map.class);
        return body == null ? null : String.valueOf(body.get("versionNo"));
    }

    public static String fetchConfig(URL url, String user, String key,
            String project, String version) {
        return get(url, "rest/projects/p/" + project + "/iterations/i/"
                + version + "/config", user, key, String.class);
    }

    @SuppressWarnings("unchecked")
    public static LocaleList fetchLocales(URL url, String user, String key,
            String project, String version) {
        List<Map<String, Object>> body = get(url, "rest/projects/p/" + project
                + "/iterations/i/" + version + "/locales", user, key,
                List.class);
        LocaleList locales = new LocaleList();
        if (body == null) {
            return locales;
        }
        List<LocaleMapping> mappings = new ArrayList<>();
        for (Map<String, Object> entry : body) {
            Object id = entry.get("localeId");
            if (id == null) {
                continue;
            }
            Object alias = entry.get("alias");
            mappings.add(new LocaleMapping(String.valueOf(id),
                    alias == null ? null : String.valueOf(alias)));
        }
        locales.addAll(mappings);
        return locales;
    }

    private static <T> T get(URL url, String path, String user, String key,
            Class<T> type) {
        return REST.exchange(base(url) + path, HttpMethod.GET,
                new HttpEntity<>(auth(user, key)), type).getBody();
    }

    private static HttpHeaders auth(String user, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-User", user == null ? "" : user);
        headers.set("X-Auth-Token", key == null ? "" : key);
        return headers;
    }

    private static String base(URL url) {
        String u = url.toString();
        return u.endsWith("/") ? u : u + "/";
    }
}
