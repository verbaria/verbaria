/*
 * Copyright 2016, Red Hat, Inc. and individual contributors
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.zanata.adapter.FileFormatAdapter.ParserOptions;
import org.zanata.adapter.FileFormatAdapter.WriterOptions;
import org.zanata.common.ContentState;
import org.zanata.common.ContentType;
import org.zanata.exception.FileFormatAdapterException;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.flattener.PrintMode;
import com.github.wnameless.json.unflattener.JsonUnflattener;

/**
 * Adapter to handle JavaScript Object Notation (JSON) documents.
 *
 * @see <a href="http://www.json.org/">JSON Specification</a>
 * @author Damian Jansen <a href="mailto:djansen@redhat.com">djansen@redhat.com</a>
 */
public class JsonAdapter implements FileFormatAdapter {

    @Override
    public boolean getRawTranslationUploadAvailable() {
        return true;
    }

    /**
     * Parse an origin JSON file as a Resource
     *
     * @param options ParserOptions containing the source locale and JSON file URI
     */
    @Override
    public Resource parseDocumentFile(ParserOptions options)
            throws FileFormatAdapterException, IllegalArgumentException {
        Resource document = new Resource();
        document.setLang(options.getLocale());
        document.setContentType(ContentType.TextPlain);
        List<TextFlow> resources = document.getTextFlows();
        Map<String, Object> flatMap = jsonFileToFlattenedMap(options.getRawFile());
        for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
            // Ignore numbers and other untranslatable objects
            if (entry.getValue() instanceof String s) {
                TextFlow textFlow = new TextFlow(entry.getKey(),
                        options.getLocale(), s);
                textFlow.setPlural(false);
                resources.add(textFlow);
            }
        }
        return document;
    }

    /**
     * Parse a translated JSON raw file as a TranslationsResource
     *
     * @param options ParserOptions containing a URI to the JSON file
     */
    @Override
    public TranslationsResource parseTranslationFile(ParserOptions options)
            throws FileFormatAdapterException, IllegalArgumentException {
        TranslationsResource transRes = new TranslationsResource();
        List<TextFlowTarget> translations = transRes.getTextFlowTargets();
        Map<String, Object> flatMap = jsonFileToFlattenedMap(options.getRawFile());
        for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
            // Ignore numbers and other untranslatable objects
            if (entry.getValue() instanceof String s) {
                TextFlowTarget textFlowTarget = new TextFlowTarget(entry.getKey());
                textFlowTarget.setContents(List.of(s));
                textFlowTarget.setState(ContentState.Translated);
                translations.add(textFlowTarget);
            }
        }
        return transRes;
    }

    /**
     * Reads an original JSON file, converts it to a Map and replaces entry
     * values with translations. Writes the result to the given output.
     *
     * @param output write destination for the processed result
     * @param options given writer options, containing translations (if any)
     *     and original JSON file location
     * @param approvedOnly specify whether to include Translated translations,
     *     or only Approved
     */
    @Override
    public void writeTranslatedFile(OutputStream output, WriterOptions options,
            boolean approvedOnly) {
        Map<String, Object> flatMap = jsonFileToFlattenedMap(
                options.getSourceParserOptions().getRawFile());
        replaceWithTranslations(flatMap,
                options.getTranslatedDoc().getTranslation().getTextFlowTargets(),
                approvedOnly);
        try (Writer writer =
                new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(flattenedMapToJson(flatMap));
        } catch (IOException exception) {
            throw new FileFormatAdapterException(
                    "Cannot create the translated file");
        }
    }

    /**
     * Replace entries in the flattened map with corresponding translations
     *
     * @param flatMap original JSON file as a map
     * @param transTargets list of translations to apply to the map
     * @param approvedOnly whether to include Translated state entries
     */
    private void replaceWithTranslations(Map<String, Object> flatMap,
            List<TextFlowTarget> transTargets, boolean approvedOnly) {
        Map<String, TextFlowTarget> translations =
                transformToMapByResId(transTargets);
        for (String key : flatMap.keySet()) {
            TextFlowTarget tft = translations.get(key);
            if (tft != null && usable(tft, approvedOnly)) {
                flatMap.put(key, tft.getContents().get(0));
            }
        }
    }

    /**
     * Transform list of TextFlowTarget to map with TextFlowTarget.resId as key
     */
    private Map<String, TextFlowTarget> transformToMapByResId(
            List<TextFlowTarget> targets) {
        Map<String, TextFlowTarget> resIdTargetMap = new HashMap<>();
        for (TextFlowTarget target : targets) {
            resIdTargetMap.put(target.getResId(), target);
        }
        return resIdTargetMap;
    }

    /**
     * Determine translation is usable, based on the approved only flag
     */
    private boolean usable(TextFlowTarget target, boolean approvedOnly) {
        return target.getState().isApproved()
                || (!approvedOnly && target.getState().isTranslated());
    }

    /**
     * Read a json file and return a Map of key value pairs
     */
    private Map<String, Object> jsonFileToFlattenedMap(URI rawFile) {
        try {
            return new JsonFlattener(
                    new InputStreamReader(new FileInputStream(new File(rawFile))))
                    .flattenAsMap();
        } catch (IOException e) {
            throw new FileFormatAdapterException("Cannot open the source file");
        }
    }

    /**
     * Convert a Map to pretty JSON
     */
    private String flattenedMapToJson(Map<String, Object> flatMap) {
        return new JsonUnflattener(flatMap.toString())
                .withPrintMode(PrintMode.PRETTY)
                .unflatten();
    }
}
