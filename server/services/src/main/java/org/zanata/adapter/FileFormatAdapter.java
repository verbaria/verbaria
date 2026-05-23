/*
 * Copyright 2012, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.adapter;

import java.io.OutputStream;
import java.net.URI;
import java.util.Objects;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.zanata.common.LocaleId;
import org.zanata.common.dto.TranslatedDoc;
import org.zanata.exception.FileFormatAdapterException;
import org.zanata.model.HDocument;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

/**
 * Common interface for classes wrapping Okapi filters. Each implementation must
 * have a public no-arg constructor.
 *
 * @author David Mason, <a href="mailto:damason@redhat.com">damason@redhat.com</a>
 */
public interface FileFormatAdapter {

    final class ParserOptions {
        /** location of the document to parse */
        private final URI rawFile;
        /** locale of document */
        private final LocaleId locale;
        /** adapter-specific parameter string. See documentation for individual adapters. */
        private final String params;

        public ParserOptions(URI rawFile, LocaleId locale, String params) {
            this.rawFile = rawFile;
            this.locale = locale;
            this.params = params;
        }

        public URI getRawFile() {
            return rawFile;
        }

        public LocaleId getLocale() {
            return locale;
        }

        public String getParams() {
            return params;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ParserOptions that)) return false;
            return Objects.equals(rawFile, that.rawFile)
                    && Objects.equals(locale, that.locale)
                    && Objects.equals(params, that.params);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rawFile, locale, params);
        }

        @Override
        public String toString() {
            return "ParserOptions(rawFile=" + rawFile + ", locale=" + locale
                    + ", params=" + params + ")";
        }
    }

    final class WriterOptions {
        private final ParserOptions sourceParserOptions;
        private final TranslatedDoc translatedDoc;

        public WriterOptions(ParserOptions sourceParserOptions,
                TranslatedDoc translatedDoc) {
            this.sourceParserOptions = sourceParserOptions;
            this.translatedDoc = translatedDoc;
        }

        public ParserOptions getSourceParserOptions() {
            return sourceParserOptions;
        }

        public TranslatedDoc getTranslatedDoc() {
            return translatedDoc;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WriterOptions that)) return false;
            return Objects.equals(sourceParserOptions, that.sourceParserOptions)
                    && Objects.equals(translatedDoc, that.translatedDoc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceParserOptions, translatedDoc);
        }

        @Override
        public String toString() {
            return "WriterOptions(sourceParserOptions=" + sourceParserOptions
                    + ", translatedDoc=" + translatedDoc + ")";
        }
    }

    /**
     * Extract source strings from the given document content.
     *
     * @param options rawFile must not be null; locale is the source document's locale
     * @return representation of the strings in the document
     * @throws FileFormatAdapterException if the document cannot be parsed
     */
    Resource parseDocumentFile(ParserOptions options)
            throws FileFormatAdapterException;

    /**
     * Extract translation strings from the given translation document.
     *
     * @param options rawFile must not be null; locale is the translation locale
     * @return representation of the translations in the document
     * @throws FileFormatAdapterException if the document cannot be parsed
     */
    TranslationsResource parseTranslationFile(ParserOptions options)
            throws FileFormatAdapterException;

    /**
     * Write translated file to the given output, using the given list of translations.
     *
     * @param output stream to write translated document
     * @throws FileFormatAdapterException if there is any problem parsing the original file
     *         or writing the translated file
     * @throws IllegalArgumentException if any parameters are null
     */
    void writeTranslatedFile(OutputStream output, WriterOptions options,
            boolean approvedOnly)
            throws FileFormatAdapterException, IllegalArgumentException;

    /**
     * Generate translation file with locale and translation extension from DocumentType
     *
     * <p>Source file name will be used if translation extension cannot be found
     * in HRawDocument#DocumentType
     *
     * @param document document to provide name and extension information.
     *        document.rawDocument must not be null!
     * @param locale to provide for
     */
    default String generateTranslationFilename(HDocument document, String locale) {
        return DefaultImpls.generateTranslationFilename(this, document, locale);
    }

    default boolean getRawTranslationUploadAvailable() {
        return false;
    }

    /**
     * Kept for Java callers that previously invoked
     * {@code FileFormatAdapter.DefaultImpls.generateTranslationFilename(this, doc, locale)}
     * when this was a Kotlin interface.
     */
    final class DefaultImpls {
        private DefaultImpls() {
        }

        public static String generateTranslationFilename(FileFormatAdapter self,
                HDocument document, String locale) {
            String srcExt = FilenameUtils.getExtension(document.getName());
            String transExt = document.getRawDocument().getType()
                    .getExtensions().get(srcExt);
            if (StringUtils.isEmpty(transExt)) {
                return document.getName();
            }
            return FilenameUtils.removeExtension(document.getName()) + "."
                    + transExt;
        }
    }
}
