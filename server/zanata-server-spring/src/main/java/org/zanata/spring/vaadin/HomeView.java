package org.zanata.spring.vaadin;

import org.zanata.spring.security.Roles;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.zanata.spring.vaadin.theme.AuraUtility;

import org.zanata.spring.service.HomeContentService;
import org.zanata.spring.service.MarkdownRenderer;
import org.zanata.spring.vaadin.admin.AdminHomeContentView;

/**
 * Public landing page at {@code /}. Renders the admin-edited Markdown stored
 * under {@code pages.home.content} as sanitised HTML. Admins see an extra
 * "Edit home page" button that jumps to {@link AdminHomeContentView}.
 *
 * <p>This replaces the legacy JSF {@code /public/home.xhtml} which was the
 * target of {@code Join.path("/").to("/public/home.xhtml")} in the old
 * {@code UrlRewriteConfig}.</p>
 */
@AnonymousAllowed
@Route(value = "", layout = MainLayout.class)
public class HomeView extends VerticalLayout implements BeforeEnterObserver, TitleKey{

    @Override public String pageTitleKey() { return "page.home"; }


    private final HomeContentService homeContentService;
    private final MarkdownRenderer markdownRenderer;

    public HomeView(HomeContentService homeContentService,
                    MarkdownRenderer markdownRenderer) {
        this.homeContentService = homeContentService;
        this.markdownRenderer = markdownRenderer;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        setAlignItems(FlexComponent.Alignment.STRETCH);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();

        String markdown = homeContentService.getMarkdown();
        String html = markdownRenderer.render(markdown);

        Div container = new Div();
        container.setWidthFull();
        container.getStyle().set("max-width", "880px");
        container.addClassNames(AuraUtility.Margin.Horizontal.AUTO, AuraUtility.Padding.MEDIUM, AuraUtility.BoxSizing.BORDER);

        if (html.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("home.empty"));
            container.add(empty);
        } else {
            Div rendered = new Div();
            rendered.getElement().setProperty("innerHTML", html);
            rendered.addClassNames("zanata-home-content");
            container.add(rendered);
        }

        if (Roles.isCurrentUserAdmin()) {
            Button edit = new Button(getTranslation("home.editButton"),
                    e -> getUI().ifPresent(ui -> ui.navigate(AdminHomeContentView.class)));
            edit.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
            HorizontalLayout actions = new HorizontalLayout(edit);
            actions.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
            actions.setWidthFull();
            container.add(actions);
        }

        add(container);
    }

}
