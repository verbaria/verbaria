package org.zanata.spring.vaadin;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.spring.vaadin.dashboard.DashboardHomeView;

@AnonymousAllowed
public class MainLayout extends AppLayout {

    public MainLayout() {
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menu toggle");

        H1 title = new H1("Zanata");
        title.addClassNames(
                LumoUtility.FontSize.LARGE,
                LumoUtility.Margin.NONE);

        HorizontalLayout authLinks = new HorizontalLayout();
        authLinks.setSpacing(true);
        authLinks.setAlignItems(HorizontalLayout.Alignment.CENTER);
        authLinks.getStyle().set("margin-left", "auto");
        authLinks.getStyle().set("padding-right", "1rem");
        if (isAuthenticated()) {
            Anchor logout = new Anchor("/logout", "Sign out");
            authLinks.add(new com.vaadin.flow.component.html.Span(currentUsername()), logout);
        } else {
            Anchor login = new Anchor("/login", "Sign in");
            Anchor register = new Anchor("/account/register", "Sign up");
            authLinks.add(login, register);
        }

        HorizontalLayout header = new HorizontalLayout(toggle, title, authLinks);
        header.setWidthFull();
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);
        addToNavbar(true, header);
    }

    private void addDrawerContent() {
        Image logo = new Image("/resources/assets/img/logo/logo-text.svg", "Zanata");
        logo.setHeight("20px");
        logo.getStyle().set("max-width", "120px");
        Anchor home = new Anchor("/", logo);
        home.getStyle().set("padding", "0.75rem 1rem 0.25rem 1rem");
        home.getStyle().set("display", "block");

        SideNav nav = createSideNav();

        VerticalLayout drawer = new VerticalLayout(home, nav);
        drawer.setSizeFull();
        drawer.setPadding(false);
        drawer.setSpacing(false);

        addToDrawer(drawer);
    }

    private SideNav createSideNav() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Explore",   ExploreView.class,       VaadinIcon.SEARCH.create()));
        nav.addItem(new SideNavItem("Languages", LanguagesView.class,     VaadinIcon.GLOBE.create()));
        nav.addItem(new SideNavItem("Glossary",  GlossaryView.class,      VaadinIcon.BOOK.create()));
        if (isAuthenticated()) {
            nav.addItem(new SideNavItem("Dashboard", DashboardHomeView.class, VaadinIcon.DASHBOARD.create()));
        }
        return nav;
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
