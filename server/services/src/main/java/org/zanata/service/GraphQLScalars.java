/*
 * Copyright Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 */
package org.zanata.service;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;

import org.zanata.common.ContentType;
import org.zanata.common.LocaleId;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.annotation.Nullable;

/**
 * T must have a public constructor which accepts a String, and a suitable toString() method.
 *
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
public final class GraphQLScalars {

    private GraphQLScalars() {
    }

    public static <T> Coercing<T, String> coercingAsString(final Class<T> tClass) {
        final Constructor<T> ctor;
        try {
            ctor = tClass.getConstructor(String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class must have a String constructor", e);
        }
        return new Coercing<T, String>() {
            @Override
            @Nullable
            public T parseLiteral(Object input) {
                if (input instanceof StringValue sv) {
                    return newInstance(sv.getValue());
                }
                return null;
            }

            @Override
            public T parseValue(Object input) {
                if (input instanceof String s) {
                    return newInstance(s);
                }
                throw new CoercingParseValueException(
                        "Expected type 'String' but was '"
                                + input.getClass().getSimpleName() + "'.");
            }

            @Override
            public String serialize(Object input) {
                if (tClass.isInstance(input)) {
                    return input.toString();
                }
                throw new CoercingSerializeException(
                        "Expected type '" + tClass.getSimpleName()
                                + "' but was '"
                                + input.getClass().getSimpleName() + "'.");
            }

            private T newInstance(String value) {
                try {
                    return ctor.newInstance(value);
                } catch (InstantiationException | IllegalAccessException
                        | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private static final GraphQLScalarType CONTENT_TYPE = new GraphQLScalarType(
            "ContentType", "ContentType", coercingAsString(ContentType.class));
    private static final GraphQLScalarType LOCALE_ID = new GraphQLScalarType(
            "LocaleId", "LocaleId", coercingAsString(LocaleId.class));

    private static final List<GraphQLType> ALL_SCALAR_TYPES =
            List.of(CONTENT_TYPE, LOCALE_ID);

    public static List<GraphQLType> getAllScalarTypes() {
        return ALL_SCALAR_TYPES;
    }
}
