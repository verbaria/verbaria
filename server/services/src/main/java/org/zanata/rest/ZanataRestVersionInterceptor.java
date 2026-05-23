package org.zanata.rest;

import jakarta.annotation.Priority;
import jakarta.ws.rs.ConstrainedTo;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.RuntimeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;

import org.zanata.rest.service.RestUtils;
import org.zanata.service.impl.VersionManager;
import org.zanata.util.ServiceLocator;
import org.zanata.util.VersionUtility;

import java.io.IOException;

@ConstrainedTo(RuntimeType.SERVER)
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class ZanataRestVersionInterceptor implements ReaderInterceptor {

    @Override
    public Object aroundReadFrom(ReaderInterceptorContext context)
            throws IOException, WebApplicationException {
        MultivaluedMap<String, String> headers = context.getHeaders();
        String clientApiVer =
                headers.getFirst(RestConstant.HEADER_VERSION_NO);
        String serverApiVer = VersionUtility.getAPIVersionInfo().getVersionNo();
        VersionManager verManager =
                ServiceLocator.instance().getInstance(VersionManager.class);

        // NB checkVersion doesn't actually reject outdated versions yet
        return verManager.checkVersion(clientApiVer, serverApiVer) ?
                context.proceed() :
                RestUtils.copyIfNotServerResponse(Response
                        .status(Status.PRECONDITION_FAILED)
                        .entity("Client API Version '"
                                + clientApiVer
                                + "'  and Server API Version '"
                                + serverApiVer
                                +
                                "' do not match. Please update your Zanata client")
                        .build());
    }
}
