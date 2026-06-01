/*
 * Copyright 2011, Red Hat, Inc. and individual contributors
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

package org.zanata.client.commands.pull;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.zanata.client.dto.LocaleMappedTranslatedDoc;
import org.zanata.common.io.FileDetails;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.resource.Resource;

/**
 * @author Sean Flanigan <a
 *         href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 *
 */
public class XmlStrategy extends AbstractPullStrategy {
    private XmlMapper xmlMapper;
    StringSet extensions = new StringSet("comment;gettext");

    protected XmlStrategy(PullOptions opts) {
        super(opts);
    }

    private synchronized XmlMapper xmlMapper() {
        if (xmlMapper == null) {
            xmlMapper = (XmlMapper) new XmlMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT);
        }
        return xmlMapper;
    }

    @Override
    public boolean needsDocToWriteTrans() {
        return false;
    }

    private String docNameToFilename(String docName) {
        return docName + ".xml";
    }

    @Override
    public void writeSrcFile(Resource doc) throws IOException {
        String filename = docNameToFilename(doc.getName());
        writeXml(getOpts().getSrcDir().resolve(filename), doc);
    }

    @Override
    public FileDetails writeTransFile(String docName,
            LocaleMappedTranslatedDoc translatedDoc)
            throws IOException {
        Path transFile =
                getTransFileToWrite(docName, translatedDoc.getLocale());
        writeXml(transFile, translatedDoc.getTranslation());
        return null;
    }

    private void writeXml(Path file, Object value) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream out = Files.newOutputStream(file)) {
            xmlMapper().writeValue(out, value);
        }
    }

    @Override
    public StringSet getExtensions() {
        return extensions;
    }
}
