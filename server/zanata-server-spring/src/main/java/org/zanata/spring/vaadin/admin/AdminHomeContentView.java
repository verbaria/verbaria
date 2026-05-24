package org.zanata.spring.vaadin.admin;

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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;
import de.f0rce.ace.enums.AceTheme;

import org.zanata.spring.service.HomeContentService;
import org.zanata.spring.service.MarkdownRenderer;
import org.zanata.spring.vaadin.MainLayout;

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
@PageTitle("Home page content | Zanata admin")
@RolesAllowed("ADMIN")
public class AdminHomeContentView extends VerticalLayout {

    public AdminHomeContentView(HomeContentService homeContentService,
                                MarkdownRenderer markdownRenderer) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H3 title = new H3("Home page content");
        title.addClassNames(LumoUtility.Margin.NONE);
        Paragraph hint = new Paragraph(
                "Markdown is rendered on the public home page ( / ). "
                        + "HTML inside the markdown is sanitised before rendering.");
        hint.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL,
                LumoUtility.Margin.NONE);
        add(title, hint);

        AceEditor editor = new AceEditor();
        editor.setMode(AceMode.markdown);
        editor.setTheme(AceTheme.textmate);
        editor.setHeight("60vh");
        editor.setWidth("100%");
        editor.setShowPrintMargin(false);
        editor.setLiveAutocompletion(false);
        editor.setValue(homeContentService.getMarkdown());

        Div preview = new Div();
        preview.setWidthFull();
        preview.getStyle().set("height", "60vh");
        preview.getStyle().set("overflow", "auto");
        preview.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        preview.getStyle().set("border-radius", "6px");
        preview.getStyle().set("padding", "1rem");
        preview.getStyle().set("background", "var(--vaadin-background-color)");
        preview.getElement().setProperty("innerHTML",
                markdownRenderer.render(editor.getValue()));

        // Live preview — Ace emits a value change per keystroke; the
        // sanitiser is fast enough that we can re-render inline.
        editor.addValueChangeListener(e ->
                preview.getElement().setProperty("innerHTML",
                        markdownRenderer.render(e.getValue())));

        Div editorPane = wrap("Markdown source", editor);
        Div previewPane = wrap("Preview", preview);
        HorizontalLayout split = new HorizontalLayout(editorPane, previewPane);
        split.setSizeFull();
        split.setFlexGrow(1, editorPane, previewPane);
        split.setSpacing(true);
        add(split);

        Button save = new Button("Save", e -> {
            homeContentService.save(editor.getValue());
            Notification.show("Home page saved", 2500,
                    Notification.Position.BOTTOM_START);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button reset = new Button("Reload from server", e -> {
            String fresh = homeContentService.getMarkdown();
            editor.setValue(fresh);
            preview.getElement().setProperty("innerHTML",
                    markdownRenderer.render(fresh));
        });
        reset.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, reset);
        actions.setAlignItems(FlexComponent.Alignment.CENTER);
        add(actions);
    }

    private static Div wrap(String label, com.vaadin.flow.component.Component body) {
        H3 h = new H3(label);
        h.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY,
                LumoUtility.Margin.NONE);
        Div pane = new Div(h, body);
        pane.setWidthFull();
        pane.getStyle().set("display", "flex");
        pane.getStyle().set("flex-direction", "column");
        pane.getStyle().set("gap", "0.4rem");
        return pane;
    }
}
