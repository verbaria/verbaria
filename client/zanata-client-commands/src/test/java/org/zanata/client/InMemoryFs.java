package org.zanata.client;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension providing an in-memory (jimfs) working directory per test.
 * No real folders are ever created. Replaces the old temp-dir helpers for any
 * test whose code-under-test does its file I/O through {@code java.nio}.
 *
 * <p>Register with {@code @RegisterExtension}. Use {@link #getRoot()} as a
 * generic working dir (analogous to a temp folder) or {@link #getTransDir()} as
 * a translation base directory.
 */
public class InMemoryFs implements BeforeEachCallback, AfterEachCallback {
    private FileSystem fs;
    private Path root;
    private final AtomicInteger counter = new AtomicInteger();

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        fs = Jimfs.newFileSystem(Configuration.unix());
        root = fs.getPath("/work");
        Files.createDirectories(root);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (fs != null) {
            fs.close();
            fs = null;
        }
    }

    public Path getRoot() {
        return root;
    }

    /** Working directory used as the translation base dir. */
    public Path getTransDir() {
        return root;
    }

    public Path newFile() throws IOException {
        return newFile("file" + counter.incrementAndGet());
    }

    public Path newFile(String name) throws IOException {
        Path file = root.resolve(name);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        Files.createFile(file);
        return file;
    }

    public Path newFolder() throws IOException {
        return newFolder("folder" + counter.incrementAndGet());
    }

    public Path newFolder(String... names) throws IOException {
        Path dir = root;
        for (String name : names) {
            dir = dir.resolve(name);
        }
        Files.createDirectories(dir);
        return dir;
    }

    public Path createTransFileRelativeToTransDir(String path)
            throws IOException {
        Path file = root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
        return file;
    }

    public void addContentToFile(Path file, Charset charset, String content)
            throws IOException {
        Files.writeString(file, content, charset);
    }
}
