package org.zanata.rest.editor.service.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.zanata.rest.editor.MediaTypes;

/**
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
@Produces({ MediaType.APPLICATION_JSON })
@Consumes({ MediaType.APPLICATION_JSON })
public interface TextFlowResource {

    public static final String SERVICE_PATH = "/source";

    /**
     * Retrieves a list TextFlow in given id.
     *
     * @param ids list textFlow's id (comma separated)
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing a full list of TextFlow. <br>
     *         Forbidden(403) - If ids list is too long<br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @GET
    @Produces({ MediaTypes.APPLICATION_ZANATA_SOURCE_JSON,
        MediaType.APPLICATION_JSON })
    public Response get(@QueryParam("ids") String ids);
}
