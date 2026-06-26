package org.zanata.adapter.properties;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.zanata.common.ProjectType;

@Component
public final class PropertiesDocumentLayout extends AbstractPropertiesDocumentLayout {

    public PropertiesDocumentLayout() {
        super(PropWriter.CHARSET.Latin1);
    }

    @Override
    public Set<ProjectType> supportedTypes() {
        return Set.of(ProjectType.Properties);
    }
}
