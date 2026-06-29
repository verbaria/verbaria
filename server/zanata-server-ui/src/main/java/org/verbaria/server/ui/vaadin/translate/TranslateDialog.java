package org.verbaria.server.ui.vaadin.translate;

import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;
import org.zanata.common.ContentState;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;

/**
 * Modal editor for one text flow: a borderless tab sheet with the translation
 * editor in the first tab and its history in the second. Fixed size so the
 * dialog doesn't jump when switching between tabs. Closing with unsaved edits
 * asks for confirmation first.
 *
 * <p>A Spring prototype bean that builds its own {@link TranslationRow}: obtain
 * one per open from an {@code ObjectProvider<TranslateDialog>} and call
 * {@link #show}.</p>
 */
@SpringComponent
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TranslateDialog extends Dialog {

    private final ObjectProvider<TranslationRow> rowFactory;
    private TranslationRow row;

    public TranslateDialog(ObjectProvider<TranslationRow> rowFactory) {
        this.rowFactory = rowFactory;
    }

    /** Builds the editor for one text flow and opens the dialog. */
    public void show(TranslationRow.RowContext ctx, HTextFlow flow,
            Optional<HTextFlowTarget> existing, ContentState state,
            String source) {
        row = rowFactory.getObject()
                .populate(ctx, flow, existing, state, source, false, true);

        setHeaderTitle(source);
        setWidth("min(1100px, 95vw)");
        setHeight("min(720px, 90vh)");
        setCloseOnEsc(true);
        setCloseOnOutsideClick(false);
        setDraggable(true);
        setResizable(true);
        // Intercept Esc (and the X button) so unsaved edits prompt first.
        addDialogCloseActionListener(e -> attemptClose());

        Button close = new Button(LineAwesomeIcon.TIMES_SOLID.create(),
                e -> attemptClose());
        close.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        close.getElement().setAttribute("aria-label", getTranslation("common.close"));
        close.getElement().setAttribute("title", getTranslation("common.close"));
        getHeader().add(close);

        TabSheet tabs = new TabSheet();
        tabs.setSizeFull();
        tabs.addThemeVariants(TabSheetVariant.AURA_NO_BORDER);
        tabs.addClassNames("fill-tabsheet", AuraUtility.MinHeight.NONE);
        tabs.add(new Tab(getTranslation("translate.tab.translations")),
                scroll(row));
        tabs.add(new Tab(getTranslation("translate.tab.history")),
                scroll(row.historyTab()));
        add(tabs);
        open();
    }

    /** Close, but confirm first when there are unsaved edits. */
    private void attemptClose() {
        if (row != null && row.isDirty()) {
            ConfirmDialog confirm = new ConfirmDialog();
            confirm.setHeader(getTranslation("translate.unsaved.title"));
            confirm.setText(getTranslation("translate.unsaved.message"));
            confirm.setCancelable(true);
            confirm.setCancelText(getTranslation("translate.unsaved.keep"));
            confirm.setConfirmText(getTranslation("translate.unsaved.discard"));
            confirm.setConfirmButtonTheme("error primary");
            confirm.addConfirmListener(e -> close());
            confirm.open();
        } else {
            close();
        }
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
