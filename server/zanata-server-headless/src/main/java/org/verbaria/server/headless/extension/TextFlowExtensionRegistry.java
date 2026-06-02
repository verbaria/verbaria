package org.verbaria.server.headless.extension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class TextFlowExtensionRegistry {

    private final Map<String, TextFlowExtensionFactory<?>> byType = new HashMap<>();
    private final Map<Class<?>, TextFlowExtensionFactory<?>> byPojo = new HashMap<>();

    public TextFlowExtensionRegistry(List<TextFlowExtensionFactory<?>> factories) {
        for (TextFlowExtensionFactory<?> f : factories) {
            byType.put(f.type(), f);
            byPojo.put(f.pojoType(), f);
        }
    }

    public Collection<TextFlowExtensionFactory<?>> all() {
        return byType.values();
    }

    public TextFlowExtensionFactory<?> byType(String type) {
        return byType.get(type);
    }

    public TextFlowExtensionFactory<?> byPojo(Class<?> pojoType) {
        return byPojo.get(pojoType);
    }
}
