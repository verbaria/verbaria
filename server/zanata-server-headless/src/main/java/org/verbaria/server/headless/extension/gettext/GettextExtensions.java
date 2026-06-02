package org.verbaria.server.headless.extension.gettext;

import org.springframework.stereotype.Component;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
import org.zanata.model.HTextFlow;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;

@Component
public class GettextExtensions {

    private final TextFlowExtensionStore store;

    public GettextExtensions(TextFlowExtensionStore store) {
        this.store = store;
    }

    public String context(HTextFlow tf) {
        return store.get(tf, PotEntryHeader.class)
                .map(PotEntryHeader::getContext).orElse(null);
    }

    public String references(HTextFlow tf) {
        return store.get(tf, PotEntryHeader.class)
                .map(p -> p.getReferences().isEmpty() ? null
                        : String.join(",", p.getReferences()))
                .orElse(null);
    }

    public String flags(HTextFlow tf) {
        return store.get(tf, PotEntryHeader.class)
                .map(p -> p.getFlags().isEmpty() ? null
                        : String.join(",", p.getFlags()))
                .orElse(null);
    }
}
