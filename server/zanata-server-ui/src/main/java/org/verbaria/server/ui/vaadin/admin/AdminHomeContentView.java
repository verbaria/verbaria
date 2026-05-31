package org.verbaria.server.ui.vaadin.admin;

import jakarta.annotation.security.RolesAllowed;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;
import de.f0rce.ace.enums.AceTheme;

import org.verbaria.server.headless.service.HomeContentService;
import org.verbaria.server.headless.service.MarkdownRenderer;
import org.verbaria.server.ui.vaadin.MainLayout;

/**
 * Admin editor for the public home page Markdown stored under
 * {@link org.zanata.model.HApplicationConfiguration#KEY_HOME_CONTENT}.
 *
 * <p>Two-pane layout: Ace editor on the left (markdown mode), live HTML
 * preview on the right (debounced, rendered through {@link MarkdownRenderer}
 * so it matches exactly what visitors will see at {@code /}). Save persists
 * via {@link HomeContentService}; the public view rereads on every navigation
 * so changes are visible immediately.</p>
 */
@Route(value = "admin/home-content", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminHomeContentView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.homeContent"; }


    public AdminHomeContentView(HomeContentService homeContentService,
                                MarkdownRenderer markdownRenderer) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H3 title = new H3(getTranslation("adminHomeContent.heading"));
        title.addClassNames(AuraUtility.Margin.NONE);
        Paragraph hint = new Paragraph(getTranslation("adminHomeContent.hint"));
        hint.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL,
                AuraUtility.Margin.NONE);
        add(title, hint);

        AceEditor editor = new AceEditor();
        editor.setMode(AceMode.markdown);
        editor.setTheme(AceTheme.textmate);
        // Fill the flex parent (the HorizontalLayout split has setSizeFull
        // and setFlexGrow(1, ...) on both panes). 60vh was a viewport-bound
        // hardcode that ignored the actual content area.
        editor.setHeight("100%");
        editor.setWidth("100%");
        editor.setShowPrintMargin(false);
        editor.setLiveAutocompletion(false);
        editor.setValue(homeContentService.getMarkdown());

        Div preview = new Div();
        preview.setWidthFull();
        preview.addClassNames(AuraUtility.Height.FULL,
                AuraUtility.Overflow.AUTO, AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM, AuraUtility.Background.BASE);
        preview.getElement().setProperty("innerHTML",
                markdownRenderer.render(editor.getValue()));

        // Live preview — Ace emits a value change per keystroke; the
        // sanitiser is fast enough that we can re-render inline.
        editor.addValueChangeListener(e ->
                preview.getElement().setProperty("innerHTML",
                        markdownRenderer.render(e.getValue())));

        Div editorPane = wrap(getTranslation("adminHomeContent.markdownSource"), editor);
        Div previewPane = wrap(getTranslation("adminHomeContent.preview"), preview);
        HorizontalLayout split = new HorizontalLayout(editorPane, previewPane);
        split.setSizeFull();
        split.setFlexGrow(1, editorPane, previewPane);
        split.setSpacing(true);
        add(split);

        Button save = new Button(getTranslation("adminHomeContent.save"), e -> {
            homeContentService.save(editor.getValue());
            Notification.show(getTranslation("adminHomeContent.saved"), 2500,
                    Notification.Position.BOTTOM_START);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);

        Button reset = new Button(getTranslation("adminHomeContent.reload"), e -> {
            String fresh = homeContentService.getMarkdown();
            editor.setValue(fresh);
            preview.getElement().setProperty("innerHTML",
                    markdownRenderer.render(fresh));
        });
        reset.addThemeVariants(ButtonVariant.TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, reset);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        add(actions);
    }

    private static Div wrap(String label, com.vaadin.flow.component.Component body) {
        H3 h = new H3(label);
        h.addClassNames(AuraUtility.FontSize.SMALL, AuraUtility.TextColor.SECONDARY,
                AuraUtility.Margin.NONE);
        Div pane = new Div(h, body);
        pane.setWidthFull();
        pane.addClassNames(AuraUtility.Display.FLEX, AuraUtility.FlexDirection.COLUMN, AuraUtility.Gap.SMALL);
        return pane;
    }
}
