/* Local placeholder for org.richfaces.model.UploadedFile. */
package org.zanata.upload;
import java.io.InputStream;
public interface UploadedFile {
    String getName();
    InputStream getInputStream() throws java.io.IOException;
    long getSize();
    String getContentType();
    byte[] getData();
    void delete();
}
