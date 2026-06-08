package org.zanata.client;

import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension that provides an in-memory (jimfs) translation directory per
 * test. No real folders are created. Register it with {@code @RegisterExtension}
 * and obtain the directory via {@link #getTransDir()}.
 */
public class InMemoryTransFiles
        implements BeforeEachCallback, AfterEachCallback {
    private FileSystem fs;
    private Path transDir;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        fs = Jimfs.newFileSystem(Configuration.unix());
        transDir = fs.getPath("/trans");
        Files.createDirectories(transDir);
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (fs != null) {
            fs.close();
            fs = null;
        }
    }

    public Path getTransDir() {
        return transDir;
    }

    public Path createTransFileRelativeToTransDir(String path)
            throws Exception {
        Path file = transDir.resolve(path);
        Files.createDirectories(file.getParent());
        Files.createFile(file);
        return file;
    }

    public void addContentToFile(Path file, Charset charset, String content)
            throws Exception {
        Files.writeString(file, content, charset);
    }
}
