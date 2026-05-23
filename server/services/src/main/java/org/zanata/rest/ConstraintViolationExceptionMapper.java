package org.zanata.rest;

import java.util.Set;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ConstraintViolationExceptionMapper
        implements ExceptionMapper<ConstraintViolationException> {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory
            .getLogger(ConstraintViolationExceptionMapper.class);

    @Override
    public Response toResponse(ConstraintViolationException e) {
        Set<ConstraintViolation<?>> invalidValues = e.getConstraintViolations();
        for (ConstraintViolation<?> invalidValue : invalidValues) {
            log.error("Invalid state for leaf bean {}: {}",
                    invalidValue.getLeafBean(), invalidValue, e);
        }
        return Response.status(Status.BAD_REQUEST).build();
    }
}
