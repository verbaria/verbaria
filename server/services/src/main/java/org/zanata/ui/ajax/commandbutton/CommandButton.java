/*
 * Copyright 2015, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 */
package org.zanata.ui.ajax.commandbutton;

import jakarta.faces.component.FacesComponent;
import jakarta.faces.component.html.HtmlCommandButton;

/**
 * Command button extension that renders as an html {@code <button>} element
 * instead of {@code <input type=button>}. Originally extended
 * {@code org.richfaces.component.UICommandButton}; RichFaces 4.x is not
 * Jakarta-EE-compatible, so this is now built on the standard Faces 4
 * {@link HtmlCommandButton} and a small custom renderer.
 */
@FacesComponent(CommandButton.COMPONENT_TYPE)
public class CommandButton extends HtmlCommandButton {

    public static final String COMPONENT_FAMILY = "org.zanata";
    public static final String COMPONENT_TYPE = "org.zanata.CommandButton";

    @Override
    public String getFamily() {
        return COMPONENT_FAMILY;
    }

    @Override
    public String getRendererType() {
        return ZanataCommandButtonRenderer.RENDERER_TYPE;
    }
}
