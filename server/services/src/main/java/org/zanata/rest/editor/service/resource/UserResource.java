package org.zanata.rest.editor.service.resource;

import java.util.Map;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.zanata.rest.editor.MediaTypes;
import org.zanata.rest.service.GlossaryService;

/**
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
@Produces({ MediaType.APPLICATION_JSON })
@Consumes({ MediaType.APPLICATION_JSON })
public interface UserResource {

    String SERVICE_PATH = "/user";

    /**
     * Retrieves user information of current authenticated user
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing user information <br>
     *         NOT FOUND(404) - If no authenticated user found. <br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @GET
    @Produces({ MediaTypes.APPLICATION_ZANATA_USER_JSON,
            MediaType.APPLICATION_JSON })
    Response getMyInfo();

    /**
     * Retrieves user information
     *
     * @param username username
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing user information <br>
     *         NOT FOUND(404) - If no authenticated user found. <br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @GET
    @Produces({ MediaTypes.APPLICATION_ZANATA_USER_JSON,
            MediaType.APPLICATION_JSON })
    @Path("/{username:[a-z\\d_]{3,20}}")
    Response getUserInfo(@PathParam("username") String username);


    /**
     * Retrieves account information of current authenticated user.
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing account information {@link org.zanata.rest.dto.Account}<br>
     *         FORBIDDEN(403) - If no authenticated user found. <br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @Path("/myaccount")
    @GET
    Response getAccountDetails();


    /**
     * Get permission for Glossary interaction of current authenticated user.
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing account information {@link org.zanata.rest.editor.dto.org.zanata.rest.editor.dto.Permission}<br>
     *         FORBIDDEN(403) - If no authenticated user found. <br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/permission/glossary")
    @GET
    Response getGlossaryPermission(
            @QueryParam("qualifiedName") @DefaultValue(GlossaryService.GLOBAL_QUALIFIED_NAME) String qualifiedName);

    /**
     * Get permission for Locales interaction of current authenticated user.
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing permission information {@link org.zanata.rest.editor.dto.org.zanata.rest.editor.dto.Permission}<br>
     *         FORBIDDEN(403) - If no authenticated user found. <br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/permission/locales")
    @GET
    Response getLocalesPermission();

    /**
     * Get permission for Translation and Review interactions of current authenticated user.
     *
     * @param projectSlug
     *            Project identifier
     *
     * @param localeId
     *            target locale
     *
     * @return The following response status codes will be returned from this
     *         operation:<br>
     *         OK(200) - Response containing permission information {@link org.zanata.rest.editor.dto.org.zanata.rest.editor.dto.Permission}<br>
     *         FORBIDDEN(403) - If no authenticated user found. <br>
     *         INTERNAL SERVER ERROR(500) - If there is an unexpected error in
     *         the server while performing this operation.
     */
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/permission/roles/project/{projectSlug}")
    @GET
    Response getTranslationPermission(@PathParam("projectSlug") String projectSlug,
            @QueryParam("localeId") String localeId);

    /**
     * Retrieve settings for the current authenticated user that begin with prefix.
     *
     * @param prefix only return settings that begin with this prefix, separated
     *               from the name with '.'
     * @return a JSON object with keys of setting name (without prefix) and values
     *         of setting value.
     */
    @Produces({ MediaType.APPLICATION_JSON })
    @Path("/settings/{prefix}")
    @GET
    Response getSettings(@PathParam("prefix") String prefix);

    /**
     * Add or update some settings for the current authenticated user.
     *
     * @param prefix add prefix then '.' to the front of each setting before persisting.
     * @param settings JSON object with setting names as keys
     */
    @Produces({ MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_JSON })
    @Path("/settings/{prefix}")
    @POST
    Response postSettings(@PathParam("prefix") String prefix, Map<String, String> settings);
}
