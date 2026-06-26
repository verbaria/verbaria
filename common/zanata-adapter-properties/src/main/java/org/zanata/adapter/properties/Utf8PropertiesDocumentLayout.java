package org.zanata.adapter.properties;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.zanata.common.ProjectType;

@Component
public final class Utf8PropertiesDocumentLayout
        extends AbstractPropertiesDocumentLayout {

    public Utf8PropertiesDocumentLayout() {
        super(PropWriter.CHARSET.UTF8);
    }

    @Override
    public Set<ProjectType> supportedTypes() {
        return Set.of(ProjectType.Utf8Properties);
    }
}
