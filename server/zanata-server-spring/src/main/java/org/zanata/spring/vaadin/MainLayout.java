package org.zanata.spring.vaadin;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.spring.vaadin.admin.AdminHomeView;
import org.zanata.spring.vaadin.dashboard.DashboardHomeView;

/**
 * App shell: a minimal navbar with the drawer toggle (so it stays reachable
 * when the drawer is closed) and a left-rail drawer with logo, scrolling
 * SideNav, and an auth section pinned to the bottom.
 */
@AnonymousAllowed
public class MainLayout extends AppLayout {

    public MainLayout() {
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
            Anchor logout = new Anchor("/logout", "Sign out");
            footer.add(LineAwesomeIcon.USER_SOLID.create(), user, logout);
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
