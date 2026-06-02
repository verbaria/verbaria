package org.verbaria.server.headless.extension;

import org.zanata.rest.dto.extensions.gettext.TextFlowExtension;

public interface TextFlowExtensionFactory<T extends TextFlowExtension> {

    String type();

    Class<T> pojoType();

    default String searchText(T pojo) {
        return null;
    }
}
