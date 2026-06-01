/*
 * Copyright 2026, verbaria.org and Red Hat, Inc. and individual contributors
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
package org.zanata.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Comparator;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import java.nio.file.Path;

/**
 * A small JUnit 5 replacement for JUnit 4's {@code TemporaryFolder} rule,
 * exposing the same {@code getRoot()/newFile()/newFolder()} API so existing
 * tests need only swap {@code @Rule} for {@code @RegisterExtension}.
 */
public class TemporaryFolderExtension
        implements BeforeEachCallback, AfterEachCallback {
    private File root;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        root = Files.createTempDirectory("junit").toFile();
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (root != null && root.exists()) {
            try (var paths = Files.walk(root.toPath())) {
                paths.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        root = null;
    }

    public File getRoot() {
        return root;
    }

    public File newFile() throws IOException {
        return File.createTempFile("junit", null, root);
    }

    public File newFile(String name) throws IOException {
        File file = new File(root, name);
        if (!file.createNewFile()) {
            throw new IOException("a file with the name '" + name
                    + "' already exists in the test folder");
        }
        return file;
    }

    public File newFolder() throws IOException {
        return Files.createTempDirectory(root.toPath(), "junit").toFile();
    }

    public File newFolder(String... names) throws IOException {
        File folder = root;
        for (String name : names) {
            folder = new File(folder, name);
        }
        if (!folder.mkdirs() && !folder.isDirectory()) {
            throw new IOException("could not create folder " + folder);
        }
        return folder;
    }
}
