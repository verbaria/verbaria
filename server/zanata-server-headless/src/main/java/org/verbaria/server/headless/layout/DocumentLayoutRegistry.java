package org.verbaria.server.headless.layout;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.common.ProjectType;

@Component
public class DocumentLayoutRegistry {

    private final Map<ProjectType, DocumentLayout> byType =
            new EnumMap<>(ProjectType.class);

    public DocumentLayoutRegistry(List<DocumentLayout> layouts) {
        for (DocumentLayout layout : layouts) {
            for (ProjectType type : layout.supportedTypes()) {
                byType.put(type, layout);
            }
        }
    }

    public Optional<DocumentLayout> forType(ProjectType type) {
        return Optional.ofNullable(byType.get(type));
    }
}
