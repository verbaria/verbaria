/*
 * Copyright 2026, Verbaria contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, see the FSF site: http://www.fsf.org.
 */
package org.zanata.client.lock;

import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zanata.client.lock.VerbariaLock.SourceLock;

import static org.assertj.core.api.Assertions.assertThat;

class VerbariaLockReaderWriterTest {

    private static VerbariaLock lock(String generatedAt, int srcRevision) {
        VerbariaLock l = new VerbariaLock();
        l.setProject("playground");
        l.setProjectVersion("master");
        l.setGeneratedAt(generatedAt);
        l.document("messages").setSource(new SourceLock(srcRevision));
        return l;
    }

    private FileSystem fs;
    private Path tmp;

    @BeforeEach
    void setUp() throws Exception {
        fs = Jimfs.newFileSystem(Configuration.unix());
        tmp = fs.getPath("/work");
        Files.createDirectories(tmp);
    }

    @AfterEach
    void tearDown() throws Exception {
        fs.close();
    }

    @Test
    void writesWhenAbsent() {
        Path file = tmp.resolve("verbaria-lock.json");
        assertThat(VerbariaLockReaderWriter.writeIfChanged(lock("T1", 1), file))
                .as("first write must happen").isTrue();
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    void doesNotRewriteWhenOnlyGeneratedAtDiffers() throws Exception {
        Path file = tmp.resolve("verbaria-lock.json");
        VerbariaLockReaderWriter.writeIfChanged(lock("T1", 1), file);
        String before = Files.readString(file);

        boolean wrote = VerbariaLockReaderWriter
                .writeIfChanged(lock("T2-later-timestamp", 1), file);

        assertThat(wrote)
                .as("a timestamp-only change must not rewrite the lock").isFalse();
        assertThat(Files.readString(file))
                .as("file (incl. original generatedAt) must be untouched")
                .isEqualTo(before);
    }

    @Test
    void rewritesWhenMeaningfulContentDiffers() throws Exception {
        Path file = tmp.resolve("verbaria-lock.json");
        VerbariaLockReaderWriter.writeIfChanged(lock("T1", 1), file);

        boolean wrote = VerbariaLockReaderWriter
                .writeIfChanged(lock("T2", 2), file);

        assertThat(wrote)
                .as("a real revision change must rewrite the lock").isTrue();
        assertThat(Files.readString(file)).contains("\"revision\" : 2");
    }
}
