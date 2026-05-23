package org.zanata.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.zanata.exception.NotLoggedInException;

@Provider
public class NotLoggedInExceptionMapper implements
        ExceptionMapper<NotLoggedInException> {

    @Override
    public Response toResponse(NotLoggedInException exception) {
        return Response.status(Status.UNAUTHORIZED).build();
    }

}
