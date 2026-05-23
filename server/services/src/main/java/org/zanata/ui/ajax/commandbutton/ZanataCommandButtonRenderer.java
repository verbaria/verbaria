/*
 * Copyright 2015, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 */
package org.zanata.ui.ajax.commandbutton;

import jakarta.faces.component.UIComponent;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ResponseWriter;
import jakarta.faces.render.FacesRenderer;
import jakarta.faces.render.Renderer;

import java.io.IOException;

/**
 * Renderer that emits the component as a real {@code <button>} HTML element
 * (with {@code onclick} bridged to the JSF submit machinery). Replaces the
 * previous RichFaces-based renderer.
 */
@FacesRenderer(componentFamily = CommandButton.COMPONENT_FAMILY,
        rendererType = ZanataCommandButtonRenderer.RENDERER_TYPE)
public class ZanataCommandButtonRenderer extends Renderer {

    public static final String RENDERER_TYPE = "org.zanata.CommandButtonRenderer";

    /** HTML attributes that should be passed straight through if set on the component. */
    private static final String[] PASS_THROUGH = {
            "accept", "accesskey", "align", "alt", "checked", "dir",
            "disabled", "lang", "maxlength", "onblur", "onchange",
            "ondblclick", "onfocus", "onkeydown", "onkeypress", "onkeyup",
            "onmousedown", "onmousemove", "onmouseout", "onmouseover",
            "onmouseup", "onselect", "readonly", "role", "size", "src",
            "style", "tabindex", "title", "usemap"
    };

    @Override
    public void encodeEnd(FacesContext facesContext, UIComponent component) throws IOException {
        ResponseWriter w = facesContext.getResponseWriter();
        String clientId = component.getClientId(facesContext);

        w.startElement("button", component);
        if (clientId != null && !clientId.isEmpty()) {
            w.writeAttribute("id", clientId, null);
            w.writeAttribute("name", clientId, null);
        }

        Object onclick = component.getAttributes().get("onclick");
        if (onclick != null) {
            w.writeAttribute("onclick", onclick, "onclick");
        }
        Object value = component.getAttributes().get("value");
        if (value != null) {
            w.writeAttribute("value", value, "value");
        }
        String styleClass = (String) component.getAttributes().get("styleClass");
        if (styleClass != null && !styleClass.isEmpty()) {
            w.writeAttribute("class", styleClass, "styleClass");
        }

        for (String attr : PASS_THROUGH) {
            Object v = component.getAttributes().get(attr);
            if (v != null && !v.toString().isEmpty()) {
                w.writeAttribute(attr, v, attr);
            }
        }

        // Render children inside the button
        if (component.getChildCount() > 0) {
            for (UIComponent child : component.getChildren()) {
                child.encodeAll(facesContext);
            }
        }
        w.endElement("button");
    }

    @Override
    public boolean getRendersChildren() {
        return true;
    }
}
