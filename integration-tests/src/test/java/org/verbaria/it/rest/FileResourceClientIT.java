package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import org.zanata.rest.DocumentFileUploadForm;

class FileResourceClientIT extends AbstractRestClientIT {

    // The one-API headless server implements raw source upload and translation
    // download, but not the legacy type catalog, translation upload, or raw
    // source download — each test asserts the server's actual behaviour.

    private DocumentFileUploadForm form() {
        DocumentFileUploadForm form = new DocumentFileUploadForm();
        form.setFileStream(new ByteArrayInputStream(new byte[0]));
        form.setFileType("odt");
        form.setHash("");
        form.setSize(0L);
        form.setFirst(true);
        form.setLast(true);
        return form;
    }

    @Test
    void testServerAcceptedType() throws Exception {
        assertThatThrownBy(() -> factory().getFileResourceClient()
                .acceptedFileTypes())
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void testFileTypeInfoList() throws Exception {
        assertThatThrownBy(() -> factory().getFileResourceClient()
                .fileTypeInfoList())
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void testSourceFileUpload() throws Exception {
        fixtures.ensureProject("rcfile", "master");
        // Raw source upload is supported by the headless server.
        assertThat(factory().getFileResourceClient()
                .uploadSourceFile("rcfile", "master", "test.odt", form()))
                .isNotNull();
    }

    @Test
    void testTranslationFileUpload() throws Exception {
        fixtures.ensureProject("rcfile", "master");
        assertThatThrownBy(() -> factory().getFileResourceClient()
                .uploadTranslationFile("rcfile", "master", "de", "test.odt",
                        "auto", form()))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void testDownloadSourceFile() throws Exception {
        fixtures.ensureProject("rcfile", "master");
        assertThatThrownBy(() -> factory().getFileResourceClient()
                .downloadSourceFile("rcfile", "master", "raw", "test.odt"))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void testDownloadTranslationFile() throws Exception {
        fixtures.ensureProject("rcfile", "master");
        // Translation download is supported by the headless server.
        assertThat(factory().getFileResourceClient()
                .downloadTranslationFile("rcfile", "master", "de", "po",
                        "test.odt").getStatusCode().is2xxSuccessful()).isTrue();
    }
}
