package org.verbaria.server.headless.extension.consulo;

import org.springframework.stereotype.Component;
import org.verbaria.server.headless.extension.TextFlowExtensionFactory;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

@Component
public class ConsuloExtensionFactory
        implements TextFlowExtensionFactory<ConsuloSubFile> {

    @Override
    public String type() {
        return ConsuloSubFile.ID;
    }

    @Override
    public Class<ConsuloSubFile> pojoType() {
        return ConsuloSubFile.class;
    }
}
