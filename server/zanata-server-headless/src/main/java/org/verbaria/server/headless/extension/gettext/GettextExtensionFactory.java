package org.verbaria.server.headless.extension.gettext;

import org.springframework.stereotype.Component;
import org.verbaria.server.headless.extension.TextFlowExtensionFactory;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;

@Component
public class GettextExtensionFactory
        implements TextFlowExtensionFactory<PotEntryHeader> {

    @Override
    public String type() {
        return PotEntryHeader.ID;
    }

    @Override
    public Class<PotEntryHeader> pojoType() {
        return PotEntryHeader.class;
    }

    @Override
    public String searchText(PotEntryHeader pojo) {
        return pojo == null ? null : pojo.getContext();
    }
}
