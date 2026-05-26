/*
 * Copyright 2026, verbaria.org and Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.rest.service;

/**
 * Public REST API path constants shared by client modules.
 * <p>
 * The full JAX-RS service-interface contracts (with {@code @Path},
 * {@code @GET}, etc.) live in the {@code stub-server} test module and
 * the server's {@code @RestController}s — clients only need the path
 * and identifier strings.
 */
public final class ApiPaths {
    private ApiPaths() {}

    // ----- /file -----
    public static final String FILE_SERVICE_PATH = "/file";
    public static final String ACCEPTED_TYPE_LIST_RESOURCE =
            "/accepted_document_types";
    public static final String FILE_TYPE_INFO_RESOURCE = "/file_type_info";

    public static final String FILETYPE_RAW_SOURCE_DOCUMENT = "raw";
    public static final String FILETYPE_TRANSLATED_APPROVED = "baked";
    public static final String FILETYPE_TRANSLATED_APPROVED_AND_FUZZY =
            "half-baked";

    // ----- /glossary -----
    public static final String GLOSSARY_SERVICE_PATH = "/glossary";
    /**
     * Default qualified-name used when a glossary entry has no explicit
     * project scope (i.e. a global / cross-project glossary entry).
     */
    public static final String GLOBAL_QUALIFIED_NAME = "global/default";

    // ----- /projects -----
    public static final String PROJECTS_SERVICE_PATH = "/projects";
}
