package org.zanata.spring.web;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serves the React frontend shell HTML for every SPA route.
 *
 * The webpack build emits hashed bundle filenames (e.g.
 * {@code frontend.eaf7bb22.cache.js}) and writes a manifest.json that maps
 * logical names like {@code frontend.js} to the hashed paths. This controller
 * reads the manifest at startup and renders a static shell page in-line.
 *
 * Catch-all paths route through here so the React router can take over —
 * e.g. {@code /explore}, {@code /glossary/project/foo} all return the same
 * index page. Real REST endpoints (anything under {@code /rest/**} or
 * {@code /api/**}) are matched by their own controllers first.
 */
@Controller
public class FrontendShellController {

    private static final String MANIFEST_PATH = "META-INF/resources/manifest.json";

    private final FrontendManifest manifest;
    private final String contextPath;

    public FrontendShellController(
            @Value("${server.servlet.context-path:}") String contextPath)
            throws Exception {
        this.contextPath = contextPath;
        this.manifest = loadManifest();
    }

    private static FrontendManifest loadManifest() throws Exception {
        ClassPathResource resource = new ClassPathResource(MANIFEST_PATH);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Cannot find " + MANIFEST_PATH + " on the classpath. "
                            + "Did you build zanata-frontend?");
        }
        Map<String, String> raw;
        try (var in = resource.getInputStream()) {
            raw = new ObjectMapper().readValue(in,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
        }
        return new FrontendManifest(raw);
    }

    // Match every non-API path so React-router can take over. REST and
    // static-asset handlers register their mappings first, so this only
    // fires for paths Spring couldn't otherwise resolve. Spring's path
    // matcher uses most-specific-wins, so dedicated @Controllers for
    // server-rendered pages take precedence over the catch-all patterns
    // here. Redirect "/" to /explore (the React frontend's landing screen)
    // so users land on real content instead of an empty #root.
    @GetMapping("/")
    public RedirectView root() {
        return new RedirectView(contextPath + "/explore");
    }

    // The login form lives at /account/login (LoginController). Forward
    // /login as an alias so the React shell doesn't catch it and render an
    // empty #root.
    @GetMapping("/login")
    public RedirectView login() {
        return new RedirectView(contextPath + "/account/login");
    }

    @GetMapping("/logout")
    public RedirectView logout() {
        return new RedirectView(contextPath + "/account/logout");
    }

    @GetMapping(value = {
            "/explore", "/explore/**",
            "/glossary", "/glossary/**",
            "/languages", "/languages/**",
            "/profile/**",
            "/admin/**",
            "/project/translate/**",
            "/project/{slug}/version/{version}",
            "/project/{slug}/version/{version}/**"
    }, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String index(jakarta.servlet.http.HttpServletRequest request) {
        // The translation editor ships as a separate webpack bundle
        // (editor.{hash}.cache.js / editor.{hash}.cache.css) and mounts
        // under /project/translate/**. Every other React route is served
        // by the main "frontend" bundle. Pick the right one based on the
        // path so we don't have to fork the SPA into two shell controllers.
        String path = request.getRequestURI();
        boolean editor = path != null && path.startsWith("/project/translate");
        return renderShell(editor);
    }

    private String renderShell(boolean editor) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n<head>\n");
        sb.append("  <meta charset=\"UTF-8\">\n");
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        sb.append("  <title>Zanata</title>\n");
        // Webpack maps "runtime.css" in the manifest even when the runtime
        // chunk has no CSS content — the JAR never contains that file, so
        // emitting the link gives a 404 in the browser console.  Skip it.
        appendCssLink(sb, editor ? manifest.editorCss() : manifest.frontendCss());
        sb.append("</head>\n<body>\n");
        // Dashboard bundle (frontend.js) mounts ReactDOM into #root, the
        // editor bundle (editor.js) mounts into #appRoot.  Emit the right
        // element so each entrypoint finds its mount node.
        sb.append("  <div id=\"").append(editor ? "appRoot" : "root").append("\"></div>\n");
        // The React app reads window.config for apiUrl, current user, etc.
        // Without it config.js falls back to undefined/empty values which
        // breaks several screens (the app shows a blank page on /explore
        // and the admin links never appear).
        sb.append("  <script>window.config = {\n");
        sb.append("    apiServerUrl: \"").append(contextPath).append("/rest\",\n");
        // appUrl must NOT end with a slash — the editor concatenates
        // route patterns to it (`appUrl + '/project/translate/...'`) and
        // a trailing slash produces "//project/..." which the router
        // can't match.  Keep contextPath verbatim (empty for the root
        // deployment).
        sb.append("    appUrl: \"").append(contextPath).append("\",\n");
        sb.append("    serverUrl: \"\",\n");
        sb.append("    appLocale: \"en-US\",\n");
        // Read the real authenticated principal from SecurityContext so the
        // React UI sees the same user Spring Security sees. Anonymous
        // visitors still get a placeholder with isLoggedIn=false so React
        // doesn't explode when it tries to read user.username.
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
        boolean isAdmin = loggedIn && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        String name = loggedIn ? auth.getName() : "anonymous";
        sb.append("    permission: { isLoggedIn: ").append(loggedIn)
          .append(", isAdmin: ").append(isAdmin).append(" },\n");
        sb.append("    user: { username: \"").append(name)
          .append("\", email: \"\", name: \"").append(name)
          .append("\", imageUrl: \"\", languageTeams: \"\" },\n");
        sb.append("    allowRegister: false,\n");
        sb.append("    links: {}\n");
        sb.append("  };</script>\n");
        appendScript(sb, manifest.runtimeJs());
        appendScript(sb, manifest.intlPolyfillJs());
        appendScript(sb, editor ? manifest.editorJs() : manifest.frontendJs());
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    private void appendCssLink(StringBuilder sb, String href) {
        if (href == null) return;
        sb.append("  <link rel=\"stylesheet\" href=\"").append(contextPath)
                .append("/").append(href).append("\">\n");
    }

    private void appendScript(StringBuilder sb, String src) {
        if (src == null) return;
        sb.append("  <script src=\"").append(contextPath).append("/")
                .append(src).append("\"></script>\n");
    }

    private record FrontendManifest(Map<String, String> entries) {
        String runtimeJs()       { return entries.get("runtime.js"); }
        String runtimeCss()      { return entries.get("runtime.css"); }
        String frontendJs()      { return entries.get("frontend.js"); }
        String frontendCss()     { return entries.get("frontend.css"); }
        String editorJs()        { return entries.get("editor.js"); }
        String editorCss()       { return entries.get("editor.css"); }
        String intlPolyfillJs()  { return entries.get("intl-polyfill.js"); }
    }
}
