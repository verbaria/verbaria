package org.verbaria.server.headless.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Teaches Spring's content-negotiation about the legacy Zanata
 * {@code application/vnd.zanata.*+json} vendor media types so the
 * {@code zanata-rest-client} (and therefore the {@code zanata-cli}) can keep
 * sending its long-standing {@code Accept}/{@code Content-Type} headers
 * without each having to be wired explicitly on every endpoint.
 */
@Configuration
public class ZanataMediaTypeConfig implements WebMvcConfigurer {

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer
                .favorParameter(false)
                .ignoreAcceptHeader(false)
                .defaultContentType(MediaType.APPLICATION_JSON);
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> c : converters) {
            if (c instanceof MappingJackson2HttpMessageConverter jc) {
                List<MediaType> types = new ArrayList<>(jc.getSupportedMediaTypes());
                types.add(MediaType.parseMediaType("application/*+json"));
                types.add(MediaType.parseMediaType("application/vnd.zanata.*+json"));
                jc.setSupportedMediaTypes(types);
            }
        }
    }
}
