package org.zanata.spring.web;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2CollectionHttpMessageConverter;
import org.springframework.http.converter.xml.Jaxb2RootElementHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Teaches Spring's content-negotiation about the legacy Zanata
 * {@code application/vnd.zanata.*+xml} / {@code +json} vendor media types so
 * the {@code zanata-rest-client} (and therefore the {@code zanata-cli}) can
 * keep sending its long-standing {@code Accept}/{@code Content-Type} headers
 * without each having to be wired explicitly on every endpoint.
 *
 * <p>XML stays on the canonical {@link Jaxb2RootElementHttpMessageConverter}
 * — the legacy CLI's RESTEasy unmarshaller insists on the namespaces declared
 * by the package-level {@code @XmlSchema(namespace = ZANATA_API)} in
 * zanata-common-api, and only the JAXB converter honors that. A second
 * {@link Jaxb2CollectionHttpMessageConverter} is registered so endpoints can
 * return {@code List<ResourceMeta>} etc. without dragging in Jackson XML
 * (which would re-emit roots in PascalCase without the package namespace).</p>
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
            } else if (c instanceof Jaxb2RootElementHttpMessageConverter xc) {
                List<MediaType> types = new ArrayList<>(xc.getSupportedMediaTypes());
                types.add(MediaType.parseMediaType("application/*+xml"));
                types.add(MediaType.parseMediaType("application/vnd.zanata.*+xml"));
                xc.setSupportedMediaTypes(types);
            }
        }

        // Register a JAXB collection converter so endpoints returning
        // List<X> (e.g. /rest/projects/p/{slug}/iterations/i/{iter}/r) can
        // serialize as XML. Spring Boot doesn't add this by default.
        Jaxb2CollectionHttpMessageConverter<List<?>> collection =
                new Jaxb2CollectionHttpMessageConverter<>();
        List<MediaType> collMedia = new ArrayList<>(collection.getSupportedMediaTypes());
        collMedia.add(MediaType.parseMediaType("application/*+xml"));
        collMedia.add(MediaType.parseMediaType("application/vnd.zanata.*+xml"));
        collection.setSupportedMediaTypes(collMedia);
        converters.add(0, collection);
    }
}
