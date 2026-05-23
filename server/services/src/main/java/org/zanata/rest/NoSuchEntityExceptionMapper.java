package org.zanata.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NoSuchEntityExceptionMapper implements
        ExceptionMapper<NoSuchEntityException> {

    @Override
    public Response toResponse(NoSuchEntityException exception) {
        return Response.status(Status.NOT_FOUND).entity(exception.getMessage())
                .build();
    }

}
