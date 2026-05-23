/*
 * Copyright 2015, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 */
package org.zanata.rest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import com.fasterxml.jackson.jakarta.rs.cfg.Annotations;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import org.zanata.rest.internal.NoJackson;

/**
 * ResteasyJacksonProvider will use JAXB annotation as well as Jackson. This is
 * different from RESTEasy 2 which only use Jackson annotations. We need to
 * override this to make our REST api backward compatible.
 *
 * Originally relied on {@code org.jboss.resteasy.util.FindAnnotation} which
 * was removed in RESTEasy 6. The lookup is now inline.
 */
@Provider
@Consumes({ "application/*+json", "text/json" })
@Produces({ "application/*+json", "text/json" })
public class ZanataJacksonJsonProvider extends JacksonJsonProvider {

    public ZanataJacksonJsonProvider() {
        super(Annotations.JACKSON);
    }

    @Override
    public boolean isReadable(Class<?> aClass, Type type,
            Annotation[] annotations, MediaType mediaType) {
        if (findAnnotation(aClass, annotations, NoJackson.class) != null) {
            return false;
        }
        return super.isReadable(aClass, type, annotations, mediaType);
    }

    @Override
    public boolean isWriteable(Class<?> aClass, Type type,
            Annotation[] annotations, MediaType mediaType) {
        if (findAnnotation(aClass, annotations, NoJackson.class) != null) {
            return false;
        }
        return super.isWriteable(aClass, type, annotations, mediaType);
    }

    @SuppressWarnings("unchecked")
    private static <A extends Annotation> A findAnnotation(Class<?> clazz,
            Annotation[] annotations, Class<A> annoType) {
        if (annotations != null) {
            for (Annotation a : annotations) {
                if (annoType.isInstance(a)) {
                    return (A) a;
                }
            }
        }
        return clazz == null ? null : clazz.getAnnotation(annoType);
    }
}
