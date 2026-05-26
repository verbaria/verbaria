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

package org.zanata.client.commands.push;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FilenameUtils;
import org.zanata.client.commands.TransFileResolver;
import org.zanata.client.commands.DocNameWithoutExt;
import org.zanata.client.commands.push.PushCommand.TranslationResourcesVisitor;
import org.zanata.client.config.LocaleMapping;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

/**
 * @author Sean Flanigan <a
 *         href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 *
 */
public class XmlStrategy extends AbstractPushStrategy {
    private XmlMapper xmlMapper;

    public XmlStrategy() {
        super(new StringSet("comment;gettext"), ".xml");
    }

    @VisibleForTesting
    protected XmlStrategy(XmlMapper xmlMapper) {
        super(new StringSet("comment;gettext"), ".xml");
        this.xmlMapper = xmlMapper;
    }

    private synchronized XmlMapper xmlMapper() {
        if (xmlMapper == null) {
            xmlMapper = new XmlMapper();
        }
        return xmlMapper;
    }

    @Override
    public Set<String> findDocNames(File srcDir, ImmutableList<String> includes,
            ImmutableList<String> excludes, boolean useDefaultExclude,
            boolean caseSensitive, boolean excludeLocaleFilenames)
            throws IOException {
        Set<String> localDocNames = new HashSet<String>();

        String[] files =
                getSrcFiles(srcDir, includes, excludes, excludeLocaleFilenames,
                        useDefaultExclude, caseSensitive);

        for (String relativeFilePath : files) {
            String baseName = FilenameUtils.removeExtension(relativeFilePath);
            localDocNames.add(baseName);
        }
        return localDocNames;
    }

    @Override
    public Resource loadSrcDoc(File sourceDir, String docName)
            throws IOException {
        String filename = docNameToFilename(docName);
        File srcFile = new File(sourceDir, filename);
        return xmlMapper().readValue(srcFile, Resource.class);
    }

    @Override
    public void visitTranslationResources(String docName, Resource srcDoc,
            TranslationResourcesVisitor visitor) throws IOException {
        for (LocaleMapping locale : getOpts().getLocaleMapList()) {
            File transFile = new TransFileResolver(getOpts()).getTransFile(
                    DocNameWithoutExt.from(docName),
                    locale);
            if (transFile.exists()) {
                TranslationsResource targetDoc = xmlMapper()
                        .readValue(transFile, TranslationsResource.class);
                visitor.visit(locale, targetDoc);
            } else {
                // no translation found in 'locale' for current doc
            }
        }
    }
}
