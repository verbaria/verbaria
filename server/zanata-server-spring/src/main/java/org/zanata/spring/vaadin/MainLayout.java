package org.zanata.spring.vaadin;

import java.util.List;
import java.util.Locale;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.spring.i18n.LocaleSelector;
import org.zanata.spring.i18n.ThemeSelector;
import org.zanata.spring.service.ContactAdminService;
import org.zanata.spring.vaadin.admin.AdminHomeView;
import org.zanata.spring.vaadin.dashboard.DashboardHomeView;

/**
 * App shell. Layout:
 * <pre>
 *  ┌─────────────────────────────────────────────────────────────────┐
 *  │ ☰  &lt;breadcrumbs&gt;                       🌗  🌐 ▾   Sign in / out  │   ← navbar
 *  ├─────────────────────────────────────────────────────────────────┤
 *  │ logo                                                             │
 *  │ ─── Home                                                         │
 *  │ ─── Explore                                       view content   │   ← body
 *  │ ─── …                                                            │
 *  │                                                                  │
 *  │ ─────────── user · contact ─────────                             │   ← drawer
 *  └─────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>The navbar is the global toolbar — it holds the breadcrumbs (left, set
 * by each view through {@link BreadcrumbsService}) and the global controls
 * (right): theme toggle, locale picker, sign-in popover or user menu.</p>
 */
@AnonymousAllowed
public class MainLayout extends AppLayout
        implements com.vaadin.flow.router.BeforeEnterObserver, AfterNavigationObserver {

    private final ContactAdminService contactAdminService;
    private final LocaleSelector localeSelector;
    private final ThemeSelector themeSelector;
    private final LoginDialogService loginDialogService;
    private final BreadcrumbsService breadcrumbsService;

    // Live placeholder in the toolbar — re-populated on every navigation by
    // {@link #afterNavigation}. Owning it as a field avoids querying the
    // DOM each time.
    private final Span breadcrumbsSlot = new Span();

    /**
     * Wrapper for the routed view. AppLayout's {@code content} slot holds
     * this; it stacks the toolbar (with breadcrumbs + global controls) on
     * top of the actual routed view. Result: the toolbar sits in the main
     * content layout area, only spanning the content width — the drawer
     * extends the full height to its left.
     */
    private VerticalLayout contentWrapper;
    private HasElement currentRoutedContent;

    public MainLayout(ContactAdminService contactAdminService,
                      LocaleSelector localeSelector,
                      ThemeSelector themeSelector,
                      LoginDialogService loginDialogService,
                      BreadcrumbsService breadcrumbsService) {
        this.contactAdminService = contactAdminService;
        this.localeSelector = localeSelector;
        this.themeSelector = themeSelector;
        this.loginDialogService = loginDialogService;
        this.breadcrumbsService = breadcrumbsService;
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
        addDrawerContent();
        // Build the in-content toolbar once. {@link #showRouterLayoutContent}
        // re-uses it across navigations, swapping the routed view underneath.
        contentWrapper = new VerticalLayout();
        contentWrapper.setSizeFull();
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        // Clip overflow at the wrapper — the toolbar is fixed-height and the
        // routed view is flex:1 with min-height:0 (set in showRouterLayoutContent),
        // so a scrolling view contains its own overflow inside the wrapper.
        contentWrapper.getStyle().set("overflow", "hidden");
        contentWrapper.add(buildToolbar());
        setContent(contentWrapper);
    }

    /**
     * Replace the AppLayout default content-swap so the routed view goes
     * <i>inside</i> our {@link #contentWrapper} — below the toolbar — rather
     * than directly into the content slot. The toolbar sits in the main
     * content layout area (above the routed view, right of the drawer); the
     * drawer extends the full height to its left.
     *
     * <p>We hold a reference to the {@link HasElement} so
     * {@link #afterNavigation} can ask the <i>routed view</i> (not the
     * wrapper) whether it implements {@link HasBreadcrumbs}.</p>
     */
    @Override
    public void showRouterLayoutContent(HasElement newContent) {
        if (currentRoutedContent != null) {
            currentRoutedContent.getElement().removeFromParent();
        }
        currentRoutedContent = newContent;
        if (newContent != null) {
            Element el = newContent.getElement();
            // Make the routed view fill the remaining height inside the
            // flex column (toolbar at top is fixed-height). Without
            // {@code min-height:0} a scrolling view (e.g. TranslateView)
            // would push its content past the bottom of the wrapper.
            el.getStyle().set("flex", "1 1 auto");
            el.getStyle().set("min-height", "0");
            el.getStyle().set("min-width", "0");
            contentWrapper.getElement().appendChild(el);
        }
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
     * Read the view's breadcrumbs (set in its constructor through
     * {@link BreadcrumbsService}) and render them in the navbar's left slot.
     *
     * <p>If the routed view doesn't implement {@link HasBreadcrumbs} we
     * clear the trail first — that way navigating from a project page back
     * to the home or explore view doesn't leave stale crumbs behind.</p>
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        // Check the routed view (not our wrapper) — see showRouterLayoutContent.
        if (!(currentRoutedContent instanceof HasBreadcrumbs)) {
            breadcrumbsService.clear();
        }
        renderBreadcrumbs();
    }

    private void renderBreadcrumbs() {
        breadcrumbsSlot.removeAll();
        List<BreadcrumbsService.Crumb> crumbs = breadcrumbsService.current();
        if (crumbs.isEmpty()) return;
        Breadcrumbs widget = new Breadcrumbs();
        for (BreadcrumbsService.Crumb c : crumbs) {
            widget.add(new Breadcrumb(c.label(),
                    c.href() == null ? "#" : c.href(),
                    c.current()));
        }
        breadcrumbsSlot.add(widget);
    }

    /**
     * The in-content toolbar: drawer toggle (single, always-visible), the
     * per-view breadcrumbs (left), and the global controls cluster on the
     * right (theme toggle, locale picker, sign-in / user menu).
     */
    private Component buildToolbar() {
        DrawerToggle drawerToggle = new DrawerToggle();
        drawerToggle.addThemeVariants(ButtonVariant.TERTIARY);
        drawerToggle.setAriaLabel(getTranslation("nav.toggleMenu"));

        breadcrumbsSlot.addClassName("crumbs");

        Div controls = new Div(buildThemeButton(), buildLocalePicker(),
                buildAuthControl());
        controls.addClassName("controls");

        Div row = new Div(drawerToggle, breadcrumbsSlot, controls);
        row.addClassName("zanata-toolbar");
        return row;
    }

    private void addDrawerContent() {
        // No toggle inside the drawer header — the toolbar holds the single
        // drawer toggle (opens AND closes). Drops the CSS hack that used to
        // hide the in-toolbar "reopen" toggle while the drawer was open.
        //
        // Brand mark is text, not an SVG — one bundle key controls the
        // wordmark, the theme controls the colour, and we don't ship a
        // separate asset to keep in sync with rebrands.
        Span logo = new Span(getTranslation("brand.name"));
        logo.getStyle().set("font-size", "1.35rem");
        logo.getStyle().set("font-weight", "700");
        logo.getStyle().set("letter-spacing", "-0.02em");
        logo.getStyle().set("color",
                "var(--aura-green-text, var(--vaadin-input-field-label-color))");
        logo.getStyle().set("line-height", "1");
        Anchor home = new Anchor("/", logo);
        home.getStyle().set("display", "flex");
        home.getStyle().set("align-items", "center");
        home.getStyle().set("justify-content", "center");
        home.getStyle().set("text-decoration", "none");
        home.getStyle().set("flex", "1 1 auto");
        home.getStyle().set("min-width", "0");

        Div header = new Div(home);
        header.getStyle().set("display", "flex");
        header.getStyle().set("align-items", "center");
        header.getStyle().set("justify-content", "center");
        header.getStyle().set("padding", "0.35rem 0.5rem");
        header.getStyle().set("box-sizing", "border-box");
        header.setWidthFull();

        // Hairline divider between brand and nav. `--view-divider-color` is
        // a single-value token defined in verbaria.css (the card border
        // token is a 3-value shorthand and can't be used in `border-top`).
        Hr divider = new Hr();
        // Vertical margin lifts the line clear of the logo above and the
        // first nav item below; horizontal margin keeps it from running
        // flush against the drawer card border.
        divider.getStyle().set("margin", "0.5rem 0.5rem 0.75rem");
        divider.getStyle().set("border", "none");
        divider.getStyle().set("border-top",
                "1px solid var(--view-divider-color, var(--vaadin-border-color))");

        SideNav nav = createSideNav();
        nav.getStyle().set("padding", "0 0.5rem");
        Scroller scroller = new Scroller(nav);
        scroller.setSizeFull();

        // No drawer footer — sign-in / locale / theme / contact / sign-out
        // all moved into the navbar's user menu. The old border-top divider
        // (left over from where the sign-in link used to live) is gone too.
        FlexLayout drawer = new FlexLayout(header, divider, scroller);
        drawer.setFlexDirection(FlexLayout.FlexDirection.COLUMN);
        drawer.setSizeFull();
        scroller.getStyle().set("flex", "1 1 auto");
        scroller.getStyle().set("min-height", "0");

        addToDrawer(drawer);
    }


    // ------------------------------------------------------------------
    // Navbar controls (right cluster)
    // ------------------------------------------------------------------

    /**
     * Theme toggle. Three modes (light / dark / follow system) shown as a
     * radio-group in the submenu — picking one unchecks the others, so the
     * "checked" state always reflects the active mode.
     */
    private Component buildThemeButton() {
        ThemeSelector.Mode mode = themeSelector.current();
        MenuBar bar = new MenuBar();
        // Theme-agnostic variants only — Aura is the active theme, no Lumo.
        bar.addThemeVariants(MenuBarVariant.TERTIARY, MenuBarVariant.SMALL);
        MenuItem trigger = bar.addItem(themeIcon(mode));
        trigger.getElement().setAttribute("aria-label", getTranslation("theme.label"));
        trigger.getElement().setAttribute("title", getTranslation("theme.label"));

        java.util.List<MenuItem> choices = new java.util.ArrayList<>();
        for (ThemeSelector.Mode target : ThemeSelector.Mode.values()) {
            String key = "theme." + target.name().toLowerCase(java.util.Locale.ROOT);
            MenuItem item = trigger.getSubMenu().addItem(getTranslation(key));
            item.setCheckable(true);
            item.setChecked(target == mode);
            choices.add(item);
            // Radio-group behavior: picking one item unchecks the others.
            // Without this MenuItem.setCheckable independently toggles each.
            // Also swap the trigger's icon so it reflects the new mode without
            // requiring a page refresh.
            item.addClickListener(e -> {
                themeSelector.select(target);
                for (MenuItem other : choices) other.setChecked(other == item);
                trigger.removeAll();
                trigger.add(themeIcon(target));
            });
        }
        return bar;
    }

    /**
     * Glyph for the theme trigger: moon for dark, sun otherwise (light or
     * follow-system both show the sun — the system-controlled rendering can
     * itself be dark, but the trigger is keyed off the user's stated choice
     * to keep the affordance predictable). Recomputed on every theme switch
     * by {@link #buildThemeButton}'s click handler.
     */
    private static Component themeIcon(ThemeSelector.Mode mode) {
        return mode == ThemeSelector.Mode.DARK
                ? LineAwesomeIcon.MOON_SOLID.create()
                : LineAwesomeIcon.SUN_SOLID.create();
    }

    /**
     * Locale picker — a MenuBar with a globe icon and the current language's
     * name. Each available locale is a checkable submenu item. Replaces the
     * old {@code Select} which didn't really suit the navbar visually.
     */
    private Component buildLocalePicker() {
        List<Locale> locales =
                VaadinService.getCurrent() != null
                        && VaadinService.getCurrent().getInstantiator() != null
                        && VaadinService.getCurrent().getInstantiator().getI18NProvider() != null
                        ? VaadinService.getCurrent()
                                .getInstantiator().getI18NProvider().getProvidedLocales()
                        : List.of(Locale.ENGLISH);
        Locale active = UI.getCurrent() == null ? Locale.ENGLISH : UI.getCurrent().getLocale();

        MenuBar bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.TERTIARY, MenuBarVariant.SMALL);
        HorizontalLayout label = new HorizontalLayout(
                LineAwesomeIcon.GLOBE_SOLID.create(),
                new Span(displayName(active)));
        label.setSpacing(false);
        label.setAlignItems(FlexComponent.Alignment.CENTER);
        label.getStyle().set("gap", "0.35rem");
        MenuItem trigger = bar.addItem(label);
        trigger.getElement().setAttribute("aria-label", getTranslation("locale.label"));
        trigger.getElement().setAttribute("title", getTranslation("locale.label"));
        for (Locale loc : locales) {
            MenuItem item = trigger.getSubMenu().addItem(displayName(loc),
                    e -> {
                        localeSelector.select(loc);
                        var ui = UI.getCurrent();
                        if (ui != null) ui.getPage().reload();
                    });
            item.setCheckable(true);
            item.setChecked(loc.equals(active));
        }
        return bar;
    }

    private static String displayName(Locale l) {
        String lang = l.getDisplayLanguage(l);
        return lang.isEmpty() ? l.toLanguageTag() :
                Character.toUpperCase(lang.charAt(0)) + lang.substring(1);
    }

    /**
     * Sign-in popover trigger (unauthenticated) or user menu (authenticated).
     * Both live in the navbar so users can sign in / out without opening
     * the drawer.
     */
    private Component buildAuthControl() {
        if (!isAuthenticated()) {
            Button signInOrUp = new Button(getTranslation("auth.signInOrUp"),
                    LineAwesomeIcon.USER_SOLID.create(),
                    e -> loginDialogService.open());
            signInOrUp.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            return signInOrUp;
        }
        MenuBar bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.TERTIARY, MenuBarVariant.SMALL);
        HorizontalLayout label = new HorizontalLayout(
                LineAwesomeIcon.USER_SOLID.create(),
                new Span(currentUsername()));
        label.setSpacing(false);
        label.setAlignItems(FlexComponent.Alignment.CENTER);
        label.getStyle().set("gap", "0.35rem");
        MenuItem trigger = bar.addItem(label);
        trigger.getSubMenu().addItem(getTranslation("auth.contactAdmin"),
                e -> openContactAdminDialog());
        // Sign-out hits the Spring Security logout endpoint via a full
        // browser navigation — SubMenu only takes addItem (no separators or
        // raw anchors), so we navigate from the click listener instead.
        trigger.getSubMenu().addItem(getTranslation("auth.signOut"),
                e -> UI.getCurrent().getPage().setLocation("/logout"));
        return bar;
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
