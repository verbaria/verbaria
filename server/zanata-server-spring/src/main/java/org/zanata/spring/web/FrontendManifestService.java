package org.zanata.spring.web;

import java.util.Collections;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads zanata-frontend's webpack manifest.json once at startup and
 * exposes the hashed bundle filenames to controllers (via inject) and to
 * Thymeleaf templates (via a request-scope model attribute injected by
 * the bundled interceptor).
 *
 * Both the React SPA shell (FrontendShellController) and the legacy JSF
 * pages now ported to Thymeleaf need these so the same React chrome
 * (header, search, language picker) renders around server-rendered
 * page content.
 */
@Component
public class FrontendManifestService implements WebMvcConfigurer {

    public static final String MANIFEST_PATH = "META-INF/resources/manifest.json";

    private final Map<String, String> entries;

    public FrontendManifestService() throws Exception {
        ClassPathResource resource = new ClassPathResource(MANIFEST_PATH);
        if (!resource.exists()) {
            this.entries = Collections.emptyMap();
            return;
        }
        try (var in = resource.getInputStream()) {
            this.entries = new ObjectMapper().readValue(in, new TypeReference<>() {});
        }
    }

    public String runtimeJs()       { return entries.get("runtime.js"); }
    public String runtimeCss()      { return entries.get("runtime.css"); }
    public String frontendJs()      { return entries.get("frontend.js"); }
    public String frontendCss()     { return entries.get("frontend.css"); }
    public String legacyJs()        { return entries.get("frontend.legacy.js"); }
    public String editorJs()        { return entries.get("editor.js"); }
    public String editorCss()       { return entries.get("editor.css"); }
    public String intlPolyfillJs()  { return entries.get("intl-polyfill.js"); }

    /** Inject the manifest into every Thymeleaf model under "manifest". */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest req, HttpServletResponse res,
                                   Object handler, ModelAndView mav) {
                if (mav != null && !mav.getModel().containsKey("manifest")) {
                    mav.addObject("manifest", FrontendManifestService.this);
                }
            }
        });
    }
}
