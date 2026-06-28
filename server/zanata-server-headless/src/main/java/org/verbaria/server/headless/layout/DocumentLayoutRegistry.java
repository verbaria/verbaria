package org.verbaria.server.headless.layout;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Component;
import org.zanata.adapter.layout.DocumentLayout;

@Component
public class DocumentLayoutRegistry {

    private final Map<String, DocumentLayout> byType =
            new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public DocumentLayoutRegistry(List<DocumentLayout> layouts) {
        for (DocumentLayout layout : layouts) {
            for (String type : layout.supportedTypes()) {
                byType.put(type, layout);
            }
        }
    }

    public Optional<DocumentLayout> forType(String type) {
        return type == null ? Optional.empty()
                : Optional.ofNullable(byType.get(type.trim()));
    }

    public Set<String> knownTypes() {
        return new TreeSet<>(byType.keySet());
    }

    public boolean isKnown(String type) {
        return type != null && byType.containsKey(type.trim());
    }
}
