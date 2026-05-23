package org.zanata.rest;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ReadOnlyEntityExceptionMapper implements
        ExceptionMapper<ReadOnlyEntityException> {

    @Override
    public Response toResponse(ReadOnlyEntityException exception) {
        return Response.status(Status.FORBIDDEN).entity(exception.getMessage())
                .build();
    }

}
