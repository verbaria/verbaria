package org.zanata.rest;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

public interface SampleDataResource {


    @PUT
    @Path("/languages")
    Response makeSampleLanguages();

    @PUT
    @Path("/languages/l/{locale}")
    Response addLanguage(@PathParam("locale") String localeId,
            @QueryParam("pluralForms")
            @DefaultValue("nplurals=2; plural=(n != 1);")
            String pluralForms);

    @PUT
    @Path("/accounts/u/{username}/languages")
    Response userJoinsLanguageTeams(@PathParam("username") String username,
            @QueryParam("locales") String localesCSV);

    @PUT
    @Path("/users")
    Response makeSampleUsers();

    @PUT
    @Path("/project")
    Response makeSampleProject();

    @DELETE
    Response deleteExceptEssentialData();

    @PUT
    @Path("/allowAnonymousUser/{value}")
    Response allowAnonymousUser(@PathParam("value") boolean value);

    /**
     * This dummy service can be used to simulate long running operation or throws exception.
     *
     * @param timeInMillis time used running this service
     * @param qualifiedExceptionClass exception to be thrown if not null
     * @return ok otherwise
     * @throws Throwable represented by qualifiedExceptionClass
     */
    @GET
    @Path("/dummy")
    Response dummyService(@QueryParam("timeUsedInMillis") long timeInMillis,
            @QueryParam("exception") String qualifiedExceptionClass) throws Throwable;
}
