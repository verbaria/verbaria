package org.zanata.adapter.properties;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public final class PropertiesDocumentLayout extends AbstractPropertiesDocumentLayout {

    public PropertiesDocumentLayout() {
        super(PropWriter.CHARSET.Latin1);
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("properties");
    }
}
