package org.zanata.rest.service;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import org.zanata.rest.dto.VersionInfo;
import org.zanata.util.VersionUtility;

@RequestScoped
@Named("versionService")
@Path(VersionResource.SERVICE_PATH)
public class VersionService implements VersionResource {

    private VersionInfo version;

    @PostConstruct
    public void postConstruct() {
        this.version = VersionUtility.getAPIVersionInfo();
    }

    @Override
    public Response get() {
        return Response.ok(version).build();
    }
}
