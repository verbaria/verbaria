package org.zanata.adapter.properties;

import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public final class Utf8PropertiesDocumentLayout
        extends AbstractPropertiesDocumentLayout {

    public Utf8PropertiesDocumentLayout() {
        super(PropWriter.CHARSET.UTF8);
    }

    @Override
    public Set<String> supportedTypes() {
        return Set.of("utf8properties");
    }
}
