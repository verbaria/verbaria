package org.verbaria.server.ui;

import java.util.List;
import java.util.Optional;

import org.zanata.rest.dto.extensions.gettext.TextFlowExtension;

public record TextFlowSnapshot(String projectType, String sourceText,
        List<TextFlowExtension> extensions) {

    public <T extends TextFlowExtension> Optional<T> extension(Class<T> type) {
        if (extensions == null) {
            return Optional.empty();
        }
        return extensions.stream().filter(type::isInstance).map(type::cast)
                .findFirst();
    }
}
