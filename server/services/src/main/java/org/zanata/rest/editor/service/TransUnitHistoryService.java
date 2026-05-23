/*
 * Copyright 2018, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 */
package org.zanata.rest.editor.service;

import com.oath.cyclops.types.persistent.PersistentMap;
import cyclops.data.HashMap;

import org.zanata.rest.editor.service.resource.TransUnitHistoryResource;
import org.zanata.rest.service.RestUtils;
import org.zanata.webtrans.server.rpc.GetTranslationHistoryHandler;
import org.zanata.webtrans.shared.rpc.GetTranslationHistoryResult;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

/**
 * @author Earl Floden <a href="mailto:efloden@redhat.com">efloden@redhat.com</a>
 */
@RequestScoped
@Path(TransUnitHistoryResource.SERVICE_PATH)
@Transactional
public class TransUnitHistoryService implements TransUnitHistoryResource {

    private GetTranslationHistoryHandler historyHandler;

    @Inject
    public TransUnitHistoryService(
            @Any GetTranslationHistoryHandler historyHandler) {
        this.historyHandler = historyHandler;
    }

    @SuppressWarnings("unused")
    protected TransUnitHistoryService() {
    }

    @Override
    public Response get(String localeId, Long transUnitId, String projectSlug,
            String versionSlug) {
        PersistentMap<String, Object> params = HashMap.<String, Object>empty()
                .put("localeId", localeId)
                .put("projectSlug", projectSlug)
                .put("versionSlug", versionSlug)
                .put("transUnitId", transUnitId);
        Response error = RestUtils.checkParams(params);
        if (error != null) return error;
        GetTranslationHistoryResult result = historyHandler.getTranslationHistory(
                localeId, transUnitId, projectSlug, versionSlug);
        return Response.ok(result).build();
    }
}
