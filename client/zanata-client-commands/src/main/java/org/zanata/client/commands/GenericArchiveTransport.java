package org.zanata.client.commands;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import java.net.URLEncoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.zanata.client.commands.pull.PullOptions;
import org.zanata.client.commands.push.PushOptions;
import org.zanata.client.config.LocaleMapping;

public class GenericArchiveTransport {

    private static final Logger log =
            LoggerFactory.getLogger(GenericArchiveTransport.class);

    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final RestTemplate rest = new RestTemplate();

    public void push(PushOptions opts) throws IOException {
        Path srcDir = opts.getSrcDir();
        List<String> includes = resolveIncludes(opts);
        List<String> paths = collect(srcDir, includes,
                opts.getExcludes(), opts.getCaseSensitive());
        log.info("Scanning {} - found {} candidate file(s)",
                srcDir == null ? null : srcDir.toAbsolutePath().normalize(),
                paths.size());
        if (paths.isEmpty()) {
            log.warn("No files matched; nothing to push.");
            return;
        }
        PushPullType pushType = opts.getPushType();
        boolean wantSource = pushType != PushPullType.Trans;
        boolean wantTrans = pushType != PushPullType.Source;
        log.info("Asking server which files to send...");
        List<String> toSend = new ArrayList<>();
        int sources = 0;
        int translations = 0;
        for (Map<String, Object> entry : requestPlan(opts, paths)) {
            boolean source = Boolean.TRUE.equals(entry.get("source"));
            if ((source && wantSource) || (!source && wantTrans)) {
                String path = String.valueOf(entry.get("path"));
                toSend.add(path);
                if (source) {
                    sources++;
                } else {
                    translations++;
                }
                log.info("  + {} [{}]", path, source ? "source" : "translation");
            }
        }
        if (toSend.isEmpty()) {
            log.warn("Server selected nothing to send for push-type '{}'.",
                    pushType);
            return;
        }
        log.info("Sending {} file(s): {} source, {} translation",
                toSend.size(), sources, translations);
        uploadArchive(opts, srcDir, toSend);
    }

    public void pull(PullOptions opts) throws IOException {
        StringBuilder url = new StringBuilder(base(opts))
                .append("rest/pull-archive?project=").append(enc(opts.getProj()))
                .append("&version=").append(enc(opts.getProjectVersion()));
        if (opts.getProjectType() != null && !opts.getProjectType().isBlank()) {
            url.append("&projectType=").append(enc(opts.getProjectType()));
        }
        if (opts.getPullType() != null) {
            url.append("&pullType=").append(enc(opts.getPullType().name()));
        }
        if (opts.getLocaleMapList() != null) {
            for (LocaleMapping m : opts.getLocaleMapList()) {
                url.append("&targetLocales=").append(enc(m.getLocale()));
            }
        }
        log.info("Pulling '{}' documents for {} {} from {}...",
                opts.getPullType(), opts.getProj(), opts.getProjectVersion(),
                base(opts));
        ResponseEntity<byte[]> resp = rest.exchange(url.toString(), HttpMethod.GET,
                new HttpEntity<>(authHeaders(opts.getUsername(), opts.getKey())),
                byte[].class);
        Path transDir = opts.getTransDir();
        int written = extract(resp.getBody(), transDir);
        log.info("Pull succeeded: wrote {} file(s) into {}", written,
                transDir == null ? null : transDir.toAbsolutePath().normalize());
    }

    private List<String> resolveIncludes(PushOptions opts) {
        List<String> own = opts.getIncludes();
        if (own != null && !own.isEmpty()) {
            return own;
        }
        return serverIncludes(opts);
    }

    @SuppressWarnings("unchecked")
    private List<String> serverIncludes(PushOptions opts) {
        String proj = opts.getProj();
        if (proj == null || proj.indexOf('*') >= 0 || proj.indexOf('?') >= 0) {
            return List.of();
        }
        String url = base(opts) + "rest/projects/p/" + proj
                + "/iterations/i/" + opts.getProjectVersion() + "/config";
        try {
            Map<String, Object> cfg = rest.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(authHeaders(opts.getUsername(), opts.getKey())),
                    Map.class).getBody();
            Object inc = cfg == null ? null : cfg.get("includes");
            return inc instanceof List ? (List<String>) inc : List.of();
        } catch (RestClientException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> requestPlan(PushOptions opts,
            List<String> paths) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("projectType", opts.getProjectType());
        body.put("project", opts.getProj());
        body.put("paths", paths);
        body.put("targetLocales", targetLocales(opts));
        body.put("sourceLang", opts.getSourceLang());
        HttpHeaders headers = authHeaders(opts.getUsername(), opts.getKey());
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> result = rest.postForObject(
                base(opts) + "rest/push-plan",
                new HttpEntity<>(body, headers), Map.class);
        Object entries = result == null ? null : result.get("entries");
        return entries instanceof List
                ? (List<Map<String, Object>>) entries : List.of();
    }

    private void uploadArchive(PushOptions opts, Path srcDir, List<String> paths)
            throws IOException {
        byte[] zip = zip(srcDir, paths);
        log.info("Uploading archive ({} file(s), {} KB)...",
                paths.size(), Math.max(1, zip.length / 1024));
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        if (opts.getProjectType() != null && !opts.getProjectType().isBlank()) {
            parts.add("projectType", opts.getProjectType());
        }
        parts.add("project", opts.getProj());
        parts.add("version", opts.getProjectVersion());
        parts.add("force", String.valueOf(opts.isForce()));
        if (opts.getSourceLang() != null) {
            parts.add("sourceLang", opts.getSourceLang());
        }
        for (String locale : targetLocales(opts)) {
            parts.add("targetLocales", locale);
        }
        parts.add("archive", new ByteArrayResource(zip) {
            @Override
            public String getFilename() {
                return "archive.zip";
            }
        });
        HttpHeaders headers = authHeaders(opts.getUsername(), opts.getKey());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = rest.postForObject(
                base(opts) + "rest/push-archive",
                new HttpEntity<>(parts, headers), Map.class);
        Object imported = resp == null ? null : resp.get("imported");
        if (imported instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    log.info("  -> {} as locale '{}' [{}]", m.get("docId"),
                            m.get("localeId"),
                            Boolean.TRUE.equals(m.get("source"))
                                    ? "source" : "translation");
                }
            }
            log.info("Push succeeded: server imported {} entr{}.",
                    list.size(), list.size() == 1 ? "y" : "ies");
        } else {
            log.info("Push succeeded.");
        }
        Object lock = resp == null ? null : resp.get("lock");
        if (lock != null) {
            Files.write(srcDir.resolve("verbaria-lock.json"),
                    PRETTY.writeValueAsBytes(lock));
            log.info("Wrote verbaria-lock.json");
        }
    }

    public static List<String> collect(Path baseDir, List<String> includes,
            List<String> excludes, boolean caseSensitive) throws IOException {
        List<String> out = new ArrayList<>();
        if (baseDir == null || !Files.isDirectory(baseDir)) {
            return out;
        }
        List<String> inc = includes == null || includes.isEmpty()
                ? List.of("**") : includes;
        List<String> exc = excludes == null ? List.of() : excludes;
        AntPathMatcher matcher = new AntPathMatcher();
        matcher.setCaseSensitive(caseSensitive);
        matcher.setPathSeparator("/");
        Files.walkFileTree(baseDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes a) {
                String rel = baseDir.relativize(file).toString().replace('\\', '/');
                if (matchesAny(matcher, rel, inc)
                        && !matchesAny(matcher, rel, exc)) {
                    out.add(rel);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    private static boolean matchesAny(AntPathMatcher matcher, String path,
            List<String> patterns) {
        for (String p : patterns) {
            if (matcher.match(p, path)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] zip(Path srcDir, List<String> paths) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String rel : paths) {
                Path file = srcDir.resolve(rel);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(rel));
                zip.write(Files.readAllBytes(file));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static int extract(byte[] archive, Path transDir) throws IOException {
        if (archive == null) {
            return 0;
        }
        int written = 0;
        try (ZipInputStream zip =
                new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path target = transDir.resolve(entry.getName());
                Files.createDirectories(target.getParent());
                Files.write(target, zip.readAllBytes());
                log.info("  - {}", entry.getName());
                written++;
            }
        }
        return written;
    }

    private static List<String> targetLocales(PushOptions opts) {
        List<String> out = new ArrayList<>();
        if (opts.getLocaleMapList() != null) {
            for (LocaleMapping m : opts.getLocaleMapList()) {
                out.add(m.getLocale());
            }
        }
        return out;
    }

    private static HttpHeaders authHeaders(String user, String key) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-User", user == null ? "" : user);
        headers.set("X-Auth-Token", key == null ? "" : key);
        return headers;
    }

    private static String base(PushOptions opts) {
        return base(opts.getUrl().toString());
    }

    private static String base(PullOptions opts) {
        return base(opts.getUrl().toString());
    }

    private static String base(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
