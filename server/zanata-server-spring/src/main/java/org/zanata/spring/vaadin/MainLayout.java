package org.zanata.spring.vaadin;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
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
import org.zanata.spring.service.ContactAdminService;
import org.zanata.spring.vaadin.admin.AdminHomeView;
import org.zanata.spring.vaadin.dashboard.DashboardHomeView;

/**
 * App shell: a minimal navbar with the drawer toggle (so it stays reachable
 * when the drawer is closed) and a left-rail drawer with logo, scrolling
 * SideNav, and an auth section pinned to the bottom.
 */
@AnonymousAllowed
public class MainLayout extends AppLayout {

    private final ContactAdminService contactAdminService;

    public MainLayout(ContactAdminService contactAdminService) {
        this.contactAdminService = contactAdminService;
        setPrimarySection(Section.DRAWER);
        addNavbarToggle();
        addDrawerContent();
        installToggleVisibilityCss();
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
            contact.addThemeVariants(ButtonVariant.LUMO_TERTIARY,
                    ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
            contact.getElement().setAttribute("title", "Contact admin");
            contact.getElement().setAttribute("aria-label", "Contact admin");
            Anchor logout = new Anchor("/logout", "Sign out");
            footer.add(LineAwesomeIcon.USER_SOLID.create(), user, contact, logout);
        } else {
            Anchor login = new Anchor("/login", "Sign in");
            Span sep = new Span("·");
            sep.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            Anchor register = new Anchor("/account/register", "Sign up");
            HorizontalLayout row = new HorizontalLayout(
                    LineAwesomeIcon.USER_SOLID.create(), login, sep, register);
            row.setSpacing(true);
            row.setPadding(false);
            row.setAlignItems(HorizontalLayout.Alignment.CENTER);
            footer.add(row);
        }
        return footer;
    }

    /**
     * Contact-admin dialog: subject + message form. Persists into
     * {@link ContactAdminService}'s log + in-memory inbox. Until SMTP is
     * wired the admin reads these from the server log.
     */
    private void openContactAdminDialog() {
        Dialog dlg = new Dialog();
        dlg.setHeaderTitle("Contact admin");
        dlg.setWidth("520px");
        dlg.setCloseOnEsc(true);

        var auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? "" : auth.getName();
        Paragraph intro = new Paragraph(
                "Send a message to the server administrator. They'll reach out "
                + "via the email on your profile if needed.");
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        intro.getStyle().set("font-size", "0.9rem");

        TextField subject = new TextField("Subject");
        subject.setWidthFull();
        TextArea body = new TextArea("Message");
        body.setWidthFull();
        body.setMinHeight("6rem");
        body.setRequired(true);

        Button cancel = new Button("Cancel", e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        Button send = new Button("Send", e -> {
            if (body.getValue() == null || body.getValue().trim().isEmpty()) {
                body.setInvalid(true);
                body.setErrorMessage("Message can't be empty");
                return;
            }
            try {
                contactAdminService.send(username, "", subject.getValue(), body.getValue());
                Notification n = Notification.show(
                        "Message sent to the admin.",
                        2500, Notification.Position.BOTTOM_END);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dlg.close();
            } catch (Exception ex) {
                Notification.show("Failed: " + ex.getMessage(),
                        4000, Notification.Position.MIDDLE);
            }
        });
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        dlg.add(intro, subject, body);
        dlg.getFooter().add(cancel, send);
        dlg.open();
    }

    private SideNav createSideNav() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Home",      org.zanata.spring.vaadin.HomeView.class, LineAwesomeIcon.HOME_SOLID.create()));
        nav.addItem(new SideNavItem("Explore",   ExploreView.class,       LineAwesomeIcon.SEARCH_SOLID.create()));
        nav.addItem(new SideNavItem("Groups",    org.zanata.spring.vaadin.group.GroupsView.class, LineAwesomeIcon.LAYER_GROUP_SOLID.create()));
        nav.addItem(new SideNavItem("Languages", LanguagesView.class,     LineAwesomeIcon.GLOBE_SOLID.create()));
        nav.addItem(new SideNavItem("Glossary",  GlossaryView.class,      LineAwesomeIcon.BOOK_SOLID.create()));
        if (isAuthenticated()) {
            nav.addItem(new SideNavItem("Dashboard", DashboardHomeView.class, LineAwesomeIcon.TACHOMETER_ALT_SOLID.create()));
            nav.addItem(new SideNavItem("Profile",   org.zanata.spring.vaadin.profile.ProfileView.class, LineAwesomeIcon.USER_SOLID.create()));
        }
        if (isAdmin()) {
            nav.addItem(new SideNavItem("Admin", AdminHomeView.class, LineAwesomeIcon.COG_SOLID.create()));
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
