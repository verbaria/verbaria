package org.verbaria.server.ui;

import com.vaadin.flow.component.Component;

public interface TextFlowMetadataProvider {

    boolean appliesTo(TextFlowMetadataContext context);

    Component editor(TextFlowMetadataContext context);
}
