package org.zanata.spring.vaadin;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.spring.i18n.LocaleSelector;
import org.zanata.spring.service.ContactAdminService;
import org.zanata.spring.vaadin.admin.AdminHomeView;
import org.zanata.spring.vaadin.dashboard.DashboardHomeView;

/**
 * App shell: a minimal navbar with the drawer toggle (so it stays reachable
 * when the drawer is closed) and a left-rail drawer with logo, scrolling
 * SideNav, and an auth section pinned to the bottom.
 */
@AnonymousAllowed
public class MainLayout extends AppLayout implements com.vaadin.flow.router.BeforeEnterObserver {

    private final ContactAdminService contactAdminService;
    private final LocaleSelector localeSelector;

    public MainLayout(ContactAdminService contactAdminService,
                      LocaleSelector localeSelector) {
        this.contactAdminService = contactAdminService;
        this.localeSelector = localeSelector;
        // Apply the persisted-locale cookie BEFORE children render. Reading
        // the cookie doesn't need the session, only the inbound HttpServletRequest
        // — which is available here because MainLayout is instantiated during
        // the Vaadin request that's about to render the route.
        Locale cookieLocale = localeSelector.current();
        UI ui = UI.getCurrent();
        if (cookieLocale != null && ui != null && !cookieLocale.equals(ui.getLocale())) {
            ui.setLocale(cookieLocale);
        }
        setPrimarySection(Section.DRAWER);
        addNavbarToggle();
        addDrawerContent();
        installToggleVisibilityCss();
    }

    /**
     * Honour {@code ?lang=<tag>} as a deterministic locale switch (QA /
     * preview / direct-link sharing). The locale itself is restored by
     * {@link LocaleSelector}'s {@code UIInitListener} before any view
     * renders, so no setLocale needed here.
     */
    @Override
    public void beforeEnter(com.vaadin.flow.router.BeforeEnterEvent event) {
        var qp = event.getLocation().getQueryParameters().getSingleParameter("lang");
        if (qp.isEmpty()) return;
        Locale picked = Locale.forLanguageTag(qp.get());
        if (picked == null || "und".equals(picked.toLanguageTag())) return;
        UI ui = UI.getCurrent();
        if (ui != null && picked.equals(ui.getLocale())) return; // already applied
        localeSelector.select(picked);
        if (ui != null) ui.getPage().reload(); // ensure children re-render in new locale
    }

    /**
     * The navbar carries a "reopen" toggle so the user can recover an
     * accidentally-closed drawer. Hide it whenever the drawer is open
     * (the in-drawer toggle takes over) and shrink the navbar to just
     * fit the button so there's no empty bar above the content.
     */
    private void installToggleVisibilityCss() {
        String css = ""
                + "vaadin-app-layout::part(navbar) { min-height: 0; padding: 0.25rem 0.5rem; }"
                + "vaadin-app-layout[primary-section=\"drawer\"][drawer-opened]:not([overlay]) "
                + "  vaadin-drawer-toggle.nav-reopen-toggle { display: none; }"
                + "vaadin-app-layout:not([drawer-opened]) ::part(navbar) { box-shadow: none; }";
        getElement().executeJs(
                "if (!document.getElementById('zanata-nav-toggle-css')) {"
                + "  const s = document.createElement('style');"
                + "  s.id = 'zanata-nav-toggle-css';"
                + "  s.textContent = $0;"
                + "  document.head.appendChild(s);"
                + "}", css);
    }

    /**
     * Tiny navbar toggle — kept available so the drawer is reopenable
     * once it's been collapsed. Hidden via CSS when the drawer is open
     * (see styles.css) so it doesn't double-up with the in-drawer toggle.
     */
    private void addNavbarToggle() {
        DrawerToggle navToggle = new DrawerToggle();
        navToggle.setAriaLabel("Open menu");
        navToggle.addClassName("nav-reopen-toggle");
        addToNavbar(navToggle);
    }

    private void addDrawerContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Toggle menu");
        toggle.getStyle().set("margin", "0");

        Image logo = new Image("/resources/assets/img/logo/logo-text.svg", "Zanata");
        logo.setHeight("22px");
        logo.getStyle().set("max-width", "140px");
        Anchor home = new Anchor("/", logo);
        home.getStyle().set("display", "flex");
        home.getStyle().set("align-items", "center");
        home.getStyle().set("flex", "1 1 auto");
        home.getStyle().set("min-width", "0");

        Div header = new Div(toggle, home);
        header.getStyle().set("display", "flex");
        header.getStyle().set("align-items", "center");
        header.getStyle().set("gap", "0.5rem");
        header.getStyle().set("padding", "0.75rem 1rem");
        header.getStyle().set("box-sizing", "border-box");
        header.setWidthFull();

        SideNav nav = createSideNav();
        nav.getStyle().set("padding", "0 0.5rem");
        Scroller scroller = new Scroller(nav);
        scroller.setSizeFull();

        Div footer = buildAuthFooter();

        FlexLayout drawer = new FlexLayout(header, scroller, footer);
        drawer.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        drawer.setSizeFull();
        scroller.getStyle().set("flex", "1 1 auto");
        scroller.getStyle().set("min-height", "0");

        addToDrawer(drawer);
    }

    private Div buildAuthFooter() {
        Div footer = new Div();
        footer.getStyle().set("border-top", "1px solid var(--vaadin-border-color)");
        footer.getStyle().set("padding", "0.6rem 1rem");
        footer.getStyle().set("display", "flex");
        footer.getStyle().set("align-items", "center");
        footer.getStyle().set("gap", "0.5rem");
        if (isAuthenticated()) {
            Span user = new Span(currentUsername());
            user.getStyle().set("font-weight", "600");
            user.getStyle().set("flex", "1 1 auto");
            Button contact = new Button(LineAwesomeIcon.ENVELOPE_SOLID.create(),
                    e -> openContactAdminDialog());
            contact.addThemeVariants(ButtonVariant.TERTIARY,
                    ButtonVariant.SMALL, ButtonVariant.LUMO_ICON);
            String contactLabel = getTranslation("auth.contactAdmin");
            contact.getElement().setAttribute("title", contactLabel);
            contact.getElement().setAttribute("aria-label", contactLabel);
            Anchor logout = new Anchor("/logout", getTranslation("auth.signOut"));
            footer.add(LineAwesomeIcon.USER_SOLID.create(), user,
                    contact, buildLocalePicker(), logout);
        } else {
            Anchor login = new Anchor("/login", getTranslation("auth.signIn"));
            Span sep = new Span("·");
            sep.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            Anchor register = new Anchor("/account/register", getTranslation("auth.signUp"));
            HorizontalLayout row = new HorizontalLayout(
                    LineAwesomeIcon.USER_SOLID.create(), login, sep, register,
                    buildLocalePicker());
            row.setSpacing(true);
            row.setPadding(false);
            row.setAlignItems(HorizontalLayout.Alignment.CENTER);
            footer.add(row);
        }
        return footer;
    }

    /**
     * Drawer-footer language picker — lets the user switch UI locale on the
     * fly. Items come from the {@link I18NProvider}'s discovered list (which
     * scans the i18n/ classpath at startup), so adding a new translation
     * file is the only thing needed to grow this menu.
     */
    private Component buildLocalePicker() {
        Select<Locale> picker =
                new Select<>();
        List<Locale> locales =
                VaadinService.getCurrent() != null
                        && VaadinService.getCurrent().getInstantiator() != null
                        && VaadinService.getCurrent().getInstantiator().getI18NProvider() != null
                        ? VaadinService.getCurrent()
                                .getInstantiator().getI18NProvider().getProvidedLocales()
                        : List.of(Locale.ENGLISH);
        picker.setItems(locales);
        picker.setItemLabelGenerator(l -> l.getDisplayLanguage(l)
                + (l.getCountry().isEmpty() ? "" : " (" + l.getCountry() + ")"));
        picker.setValue(UI.getCurrent() == null
                ? Locale.ENGLISH : UI.getCurrent().getLocale());
        picker.setWidth("9rem");
        picker.addValueChangeListener(ev -> {
            if (ev.getValue() == null) return;
            localeSelector.select(ev.getValue());
            var ui = UI.getCurrent();
            if (ui != null) ui.getPage().reload();
        });
        return picker;
    }

    /**
     * Contact-admin dialog: subject + message form. Persists into
     * {@link ContactAdminService}'s log + in-memory inbox. Until SMTP is
     * wired the admin reads these from the server log.
     */
    private void openContactAdminDialog() {
        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("contact.title"));
        dlg.setWidth("520px");
        dlg.setCloseOnEsc(true);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? "" : auth.getName();
        Paragraph intro = new Paragraph(getTranslation("contact.intro"));
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        intro.getStyle().set("font-size", "0.9rem");

        TextField subject = new TextField(getTranslation("contact.subject"));
        subject.setWidthFull();
        TextArea body = new TextArea(getTranslation("contact.message"));
        body.setWidthFull();
        body.setMinHeight("6rem");
        body.setRequired(true);

        Button cancel = new Button(getTranslation("common.cancel"), e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        Button send = new Button(getTranslation("contact.send"), e -> {
            if (body.getValue() == null || body.getValue().trim().isEmpty()) {
                body.setInvalid(true);
                body.setErrorMessage(getTranslation("contact.empty"));
                return;
            }
            try {
                contactAdminService.send(username, "", subject.getValue(), body.getValue());
                Notification n = Notification.show(
                        getTranslation("contact.sent"),
                        2500, Notification.Position.BOTTOM_END);
                n.addThemeVariants(NotificationVariant.SUCCESS);
                dlg.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("common.failed", ex.getMessage()),
                        4000, Notification.Position.MIDDLE);
            }
        });
        send.addThemeVariants(ButtonVariant.PRIMARY);

        dlg.add(intro, subject, body);
        dlg.getFooter().add(cancel, send);
        dlg.open();
    }

    private SideNav createSideNav() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem(getTranslation("nav.home"),      org.zanata.spring.vaadin.HomeView.class, LineAwesomeIcon.HOME_SOLID.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.explore"),   ExploreView.class,       LineAwesomeIcon.SEARCH_SOLID.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.groups"),    org.zanata.spring.vaadin.group.GroupsView.class, LineAwesomeIcon.LAYER_GROUP_SOLID.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.languages"), LanguagesView.class,     LineAwesomeIcon.GLOBE_SOLID.create()));
        nav.addItem(new SideNavItem(getTranslation("nav.glossary"),  GlossaryView.class,      LineAwesomeIcon.BOOK_SOLID.create()));
        if (isAuthenticated()) {
            nav.addItem(new SideNavItem(getTranslation("nav.dashboard"), DashboardHomeView.class, LineAwesomeIcon.TACHOMETER_ALT_SOLID.create()));
            nav.addItem(new SideNavItem(getTranslation("nav.profile"),   org.zanata.spring.vaadin.profile.ProfileView.class, LineAwesomeIcon.USER_SOLID.create()));
        }
        if (isAdmin()) {
            nav.addItem(new SideNavItem(getTranslation("nav.admin"), AdminHomeView.class, LineAwesomeIcon.COG_SOLID.create()));
        }
        return nav;
    }

    private static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "" : auth.getName();
    }
}
