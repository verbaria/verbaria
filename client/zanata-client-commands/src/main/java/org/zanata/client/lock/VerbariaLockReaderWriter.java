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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reads and writes {@code verbaria-lock.json} as pretty-printed JSON with
 * deterministic key ordering, so that commits produce minimal, reviewable
 * diffs.
 */
public final class VerbariaLockReaderWriter {

    /** Default file name, written at the project root (the cache dir). */
    public static final String FILE_NAME = "verbaria-lock.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            // tolerate forward-compatible additions to the schema
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    false);

    private VerbariaLockReaderWriter() {
    }

    /** Reads a lock from {@code file}, or returns {@code null} if absent. */
    public static VerbariaLock readOrNull(File file) {
        return file == null ? null : readOrNull(file.toPath());
    }

    /**
     * Reads a lock from {@code file}, or returns {@code null} if absent. Path
     * based so it works on any {@link java.nio.file.FileSystem} (incl. jimfs).
     */
    public static VerbariaLock readOrNull(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            return MAPPER.readValue(in, VerbariaLock.class);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not read lock file: " + file, e);
        }
    }

    /** Reads a lock from {@code file}, or an empty lock if absent. */
    public static VerbariaLock readOrEmpty(File file) {
        VerbariaLock lock = readOrNull(file);
        return lock == null ? new VerbariaLock() : lock;
    }

    /**
     * Writes the lock only if its meaningful content differs from what is
     * already on disk — ignoring the volatile {@code generatedAt} timestamp.
     * Returns {@code true} if the file was (re)written. This avoids a spurious
     * diff (a fake "change" in CI) when a sync produced identical data and only
     * the timestamp would have moved.
     */
    public static boolean writeIfChanged(VerbariaLock lock, Path file) {
        VerbariaLock existing = readOrNull(file);
        if (existing != null && equalsIgnoringGeneratedAt(existing, lock)) {
            return false;
        }
        write(lock, file);
        return true;
    }

    private static boolean equalsIgnoringGeneratedAt(VerbariaLock a, VerbariaLock b) {
        return withoutGeneratedAt(a).equals(withoutGeneratedAt(b));
    }

    private static JsonNode withoutGeneratedAt(VerbariaLock lock) {
        ObjectNode node = MAPPER.valueToTree(lock);
        node.remove("generatedAt");
        return node;
    }

    public static void write(VerbariaLock lock, Path file) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] json = MAPPER.writeValueAsBytes(lock);
            try (OutputStream os = Files.newOutputStream(file)) {
                os.write(json);
                // trailing newline keeps git diffs line-oriented
                os.write('\n');
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "Could not write lock file: " + file, e);
        }
    }

    /** Serialises a lock to a JSON string (used by tests). */
    public static String toJson(VerbariaLock lock) {
        try {
            return new String(MAPPER.writeValueAsBytes(lock),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
