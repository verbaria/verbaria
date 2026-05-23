/*
 * Copyright 2015, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 */
package org.zanata.exception.handler;

import org.apache.commons.fileupload.FileUploadException;
import org.apache.deltaspike.core.api.exception.control.ExceptionHandler;
import org.apache.deltaspike.core.api.exception.control.Handles;
import org.apache.deltaspike.core.api.exception.control.event.ExceptionEvent;

import jakarta.faces.application.FacesMessage;

/**
 * Originally handled {@code org.richfaces.exception.FileUploadException}.
 * RichFaces 4.x is not Jakarta-EE-compatible; switched to Apache Commons
 * FileUpload's {@link FileUploadException} which is the most common cause
 * of file-upload failures.
 */
@ExceptionHandler
public class FileUploadExceptionHandler extends AbstractExceptionHandler {

    public void handleException(@Handles ExceptionEvent<FileUploadException> event) {
        handle(event, LogLevel.Warn, FacesMessage.SEVERITY_ERROR,
                "jsf.DetailError", event.getException().getMessage());
    }
}
