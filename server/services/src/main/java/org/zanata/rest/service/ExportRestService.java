/*
 * Copyright 2018, Red Hat, Inc. and individual contributors as indicated by the
 *  @author tags. See the copyright.txt file in the distribution for a full
 *  listing of individual contributors.
 *
 *  This is free software; you can redistribute it and/or modify it under the
 *  terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 */
package org.zanata.rest.service;

import java.util.Map;

import graphql.ExecutionResult;

import org.zanata.seam.security.CurrentUser;
import org.zanata.service.GraphQLService;
import org.zanata.util.JsonUtil;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>,
 *         Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
@Path("/export")
public class ExportRestService {

    private CurrentUser currentUser;
    private GraphQLService graphQLService;

    @Inject
    public ExportRestService(CurrentUser currentUser,
            GraphQLService graphQLService) {
        this.currentUser = currentUser;
        this.graphQLService = graphQLService;
    }

    @SuppressWarnings("unused")
    protected ExportRestService() {
    }

    @GET
    @Path("/userData")
    public Response exportUserData() {
        if (!currentUser.isLoggedIn()) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }

        String graphqlQuery = """
                {
                  account (username: "%s") {
                    username
                    creationDate
                    lastChanged
                    enabled
                    roles { name }
                    person {
                      name
                      email
                      # global language teams
                      languageTeamMemberships {
                        locale { localeId }
                        isCoordinator
                        isReviewer
                        isTranslator
                      }
                      # project language teams
                      projectLocaleMemberships {
                        project {
                          slug
                          name
                          # no projectIterations
                        }
                        locale { localeId }
                        role
                      }
                      # project maintainer/owner or translation maintainer
                      projectMemberships {
                        project {
                          slug
                          name
                          projectIterations { slug }
                        }
                        role
                      }
                    }
                  }
                }""".formatted(currentUser.getUsername());
        ExecutionResult result = graphQLService.query(graphqlQuery);

        if (!result.getErrors().isEmpty()) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(result.getErrors()).build();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data =
                (Map<String, Object>) result.toSpecification().get("data");
        String json = JsonUtil.toJson(data, true);
        return Response.ok(json).build();
    }
}
