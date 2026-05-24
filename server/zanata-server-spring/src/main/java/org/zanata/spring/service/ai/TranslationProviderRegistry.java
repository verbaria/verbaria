package org.zanata.spring.service.ai;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class TranslationProviderRegistry {

    private final List<TranslationProvider> providers;

    public TranslationProviderRegistry(List<TranslationProvider> providers) {
        this.providers = providers;
    }

    public List<TranslationProvider> available() {
        return providers.stream().filter(TranslationProvider::isAvailable).toList();
    }

    public List<TranslationProvider> all() {
        return providers;
    }

    public Optional<TranslationProvider> byId(String id) {
        return providers.stream().filter(p -> p.id().equals(id)).findFirst();
    }
}
