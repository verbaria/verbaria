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
package org.zanata.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

import org.fedorahosted.openprops.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.common.DocumentType;
import org.zanata.common.ProjectType;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HRawDocument;
import org.zanata.model.HSimpleComment;
import org.zanata.model.HTextFlow;
import org.zanata.model.po.HPoTargetHeader;
import org.zanata.rest.service.PoUtility;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
@ApplicationScoped
public class AttributionService {

    public static final String ATTRIBUTION_KEY = "X-Zanata-MT-Attribution";

    private static final Logger log =
            LoggerFactory.getLogger(AttributionService.class);

    @Nullable
    public DocumentType inferDocumentType(HDocument doc) {
        ProjectType projectType = doc.getProjectIteration().getEffectiveProjectType();
        if (projectType == null) {
            // A very old project with no effective project type
            return null;
        }
        switch (projectType) {
            case Gettext:
            case Podir:
                return DocumentType.GETTEXT;
            case Properties:
                return DocumentType.PROPERTIES;
            case Utf8Properties:
                return DocumentType.PROPERTIES_UTF8;
            case Xml:
                return DocumentType.XML;
            case Xliff:
                return DocumentType.XLIFF;
            case File:
                // null rawDoc is assumed to be gettext
                HRawDocument rawDocument = doc.getRawDocument();
                if (rawDocument == null) {
                    return DocumentType.GETTEXT;
                }
                return rawDocument.getType();
            default:
                return null;
        }
    }

    public boolean supportsAttribution(HDocument doc) {
        DocumentType type = inferDocumentType(doc);
        if (type == null) return false;
        switch (type) {
            case GETTEXT:
            case PROPERTIES:
            case PROPERTIES_UTF8:
                return true;
            default:
                return false;
        }
    }

    public void addAttribution(HDocument doc, HLocale locale, String backendId) {
        if (doc.getTextFlows().isEmpty()) return;
        String attributionMessage = getAttributionMessage(backendId);

        DocumentType docType = inferDocumentType(doc);
        if (docType == null) {
            throw new RuntimeException("null DocumentType for " + doc);
        }
        switch (docType) {
            case GETTEXT:
                HPoTargetHeader poTargetHeader =
                        doc.getPoTargetHeaders().get(locale);
                if (poTargetHeader == null) {
                    poTargetHeader = new HPoTargetHeader();
                    poTargetHeader.setTargetLanguage(locale);
                    poTargetHeader.setDocument(doc);
                    doc.getPoTargetHeaders().put(locale, poTargetHeader);
                }
                ensureAttributionForGettext(poTargetHeader, attributionMessage);
                break;
            case PROPERTIES:
            case PROPERTIES_UTF8:
                ensureAttributionForProperties(doc.getTextFlows().get(0),
                        attributionMessage);
                break;
            default:
                throw new RuntimeException("unexpected DocumentType for " + doc);
        }
    }

    void ensureAttributionForGettext(HPoTargetHeader header,
            String attributionMessage) {
        Properties props;
        if (header.getEntries() == null) {
            props = new Properties();
        } else {
            props = PoUtility.headerToProperties(header.getEntries());
        }
        props.setProperty(ATTRIBUTION_KEY, attributionMessage);
        header.setEntries(PoUtility.propertiesToHeader(props));
    }

    void ensureAttributionForProperties(HTextFlow textFlow,
            String attributionMessage) {
        String prefix = ATTRIBUTION_KEY + ":";
        String attributionLine = prefix + " " + attributionMessage;
        if (textFlow.getComment() != null) {
            List<String> oldLines = Arrays.asList(
                    textFlow.getComment().getComment().split("\n", -1));
            textFlow.getComment().setComment(
                    ensureLine(oldLines, prefix, attributionLine));
        } else {
            textFlow.setComment(new HSimpleComment(attributionLine));
        }
    }

    private String ensureLine(List<String> lines, String prefix,
            String attributionLine) {
        boolean found = false;
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                found = true;
                break;
            }
        }
        List<String> newLines = new ArrayList<>(lines.size() + 1);
        if (found) {
            for (String line : lines) {
                if (line.startsWith(prefix)) {
                    newLines.add(attributionLine);
                } else {
                    newLines.add(line);
                }
            }
        } else {
            newLines.addAll(lines);
            newLines.add(attributionLine);
        }
        return String.join("\n", newLines);
    }

    public String getAttributionMessage(String backendId) {
        switch (backendId) {
            case "GOOGLE":
                return "Translated by Google";
            case "DEV":
                return "Pseudo-translated by MT (DEV)";
            default:
                log.warn("Unexpected MT backendId: {}", backendId);
                return "Translated by MT backendId: " + backendId;
        }
    }
}
