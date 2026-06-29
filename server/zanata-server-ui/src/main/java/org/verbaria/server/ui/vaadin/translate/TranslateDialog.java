package org.verbaria.server.ui.vaadin.translate;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

/**
 * Modal editor for one text flow: a borderless tab sheet with the translation
 * editor in the first tab and its history in the second. Fixed size so the
 * dialog doesn't jump when switching between tabs.
 */
public class TranslateDialog extends Dialog {

    public TranslateDialog(String title, Component translations,
            Component history) {
        setHeaderTitle(title);
        setWidth("min(1100px, 95vw)");
        setHeight("min(720px, 90vh)");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);
        setDraggable(true);
        setResizable(true);

        Button close = new Button(LineAwesomeIcon.TIMES_SOLID.create(),
                e -> close());
        close.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        close.getElement().setAttribute("aria-label", getTranslation("common.close"));
        close.getElement().setAttribute("title", getTranslation("common.close"));
        getHeader().add(close);

        TabSheet tabs = new TabSheet();
        tabs.setSizeFull();
        tabs.addThemeVariants(TabSheetVariant.AURA_NO_BORDER);
        tabs.addClassNames("fill-tabsheet", AuraUtility.MinHeight.NONE);
        tabs.add(new Tab(getTranslation("translate.tab.translations")),
                scroll(translations));
        tabs.add(new Tab(getTranslation("translate.tab.history")),
                scroll(history));
        add(tabs);
    }

    /**
     * Scrolling wrapper for tab content. A {@code vaadin-vertical-layout} so the
     * {@code fill-tabsheet} CSS pins it to fill the content area; its own
     * {@code overflow:auto} then provides a single inner scrollbar.
     */
    private static VerticalLayout scroll(Component content) {
        VerticalLayout box = new VerticalLayout(content);
        box.setSizeFull();
        box.setPadding(false);
        box.setSpacing(false);
        box.getStyle().set("overflow", "auto");
        return box;
    }
}
