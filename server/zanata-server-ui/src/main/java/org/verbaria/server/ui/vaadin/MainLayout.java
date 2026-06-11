package org.verbaria.server.ui.vaadin;

import org.verbaria.server.headless.security.Roles;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.breadcrumbs.Breadcrumbs;
import com.vaadin.flow.component.breadcrumbs.BreadcrumbsItem;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HighlightActions;
import com.vaadin.flow.router.HighlightConditions;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.verbaria.server.ui.vaadin.i18n.LocaleSelector;
import org.verbaria.server.ui.vaadin.i18n.ThemeSelector;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import org.verbaria.server.headless.service.ContactAdminService;
import org.verbaria.server.headless.service.HomeContentService;
import org.verbaria.server.ui.vaadin.admin.AdminHomeView;
import org.verbaria.server.ui.vaadin.group.GroupsView;
import org.verbaria.server.ui.vaadin.profile.ProfileView;

/**
 * App shell. Two stacked glass clouds inside the content area (the drawer is
 * unused — navigation is horizontal):
 * <pre>
 *  ┌── toolbar cloud ────────────────────────────────────────────────────┐
 *  │ Verbaria  Home Projects Groups …   [ search ]   🛡 🌗 🌐▾  Sign in   │
 *  └─────────────────────────────────────────────────────────────────────┘
 *  ┌── content cloud ────────────────────────────────────────────────────┐
 *  │ Home › Projects › … ▸ leaf                       [ view actions ]    │ ← crumb + actions row
 *  │                                                                     │
 *  │                         routed view content                         │
 *  └─────────────────────────────────────────────────────────────────────┘
 * </pre>
 *
 * <p>The toolbar cloud holds the brand, the horizontal primary nav, an
 * optional per-view search field ({@link HasToolbarSearch}), and the global
 * controls cluster: admin shortcut (icon, admins only), theme toggle, locale
 * picker, and sign-in popover / user menu (which also hosts Profile).</p>
 *
 * <p>The content cloud opens with a row that pairs the per-view breadcrumbs
 * (left, set through {@link BreadcrumbsService}) with optional per-view
 * actions (right, {@link HasToolbarActions}) — e.g. the editor's doc switcher,
 * stats, filter and settings. The routed view fills the rest.</p>
 */
@AnonymousAllowed
public class MainLayout extends AppLayout
        implements BeforeEnterObserver, AfterNavigationObserver {

    private final ContactAdminService contactAdminService;
    private final LocaleSelector localeSelector;
    private final ThemeSelector themeSelector;
    private final LoginDialogService loginDialogService;
    private final BreadcrumbsService breadcrumbsService;
    private final HomeContentService homeContentService;
    private final AvatarService avatarService;

    // Live placeholder in the toolbar — re-populated on every navigation by
    // {@link #afterNavigation}. Owning it as a field avoids querying the
    // DOM each time.
    private final Span breadcrumbsSlot = new Span();
    private final Span searchSlot = new Span();
    private final Span actionsSlot = new Span();
    private final Span subtitleSlot = new Span();

    /**
     * Wrapper for the routed view. AppLayout's {@code content} slot holds
     * this; it stacks the toolbar (with breadcrumbs + global controls) on
     * top of the actual routed view. Result: the toolbar sits in the main
     * content layout area, only spanning the content width — the drawer
     * extends the full height to its left.
     */
    private VerticalLayout contentWrapper;
    private VerticalLayout contentCard;
    private Div crumbBar;
    private HasElement currentRoutedContent;

    public MainLayout(ContactAdminService contactAdminService,
                      LocaleSelector localeSelector,
                      ThemeSelector themeSelector,
                      LoginDialogService loginDialogService,
                      BreadcrumbsService breadcrumbsService,
                      HomeContentService homeContentService,
                      AvatarService avatarService) {
        this.contactAdminService = contactAdminService;
        this.localeSelector = localeSelector;
        this.themeSelector = themeSelector;
        this.loginDialogService = loginDialogService;
        this.breadcrumbsService = breadcrumbsService;
        this.homeContentService = homeContentService;
        this.avatarService = avatarService;
        // Apply the persisted-locale cookie BEFORE children render. Reading
        // the cookie doesn't need the session, only the inbound HttpServletRequest
        // — which is available here because MainLayout is instantiated during
        // the Vaadin request that's about to render the route.
        Locale cookieLocale = localeSelector.current();
        UI ui = UI.getCurrent();
        if (cookieLocale != null && ui != null && !cookieLocale.equals(ui.getLocale())) {
            ui.setLocale(cookieLocale);
        }
        setPrimarySection(Section.NAVBAR);
        setDrawerOpened(false);
        contentWrapper = new VerticalLayout();
        contentWrapper.setSizeFull();
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        contentWrapper.addClassNames("app-shell-content", AuraUtility.Overflow.HIDDEN);

        Component toolbar = buildToolbar();
        toolbar.getElement().getClassList().add("glass-surface");
        contentWrapper.add(toolbar);

        contentCard = new VerticalLayout();
        contentCard.setPadding(false);
        contentCard.setSpacing(false);
        contentCard.addClassNames("glass-surface", "app-content-card",
                AuraUtility.Overflow.HIDDEN);
        breadcrumbsSlot.addClassNames(AuraUtility.Flex.SHRINK_NONE);
        subtitleSlot.addClassNames(AuraUtility.Flex.AUTO, AuraUtility.MinWidth.NONE,
                AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL,
                AuraUtility.Overflow.HIDDEN, AuraUtility.TextOverflow.ELLIPSIS,
                AuraUtility.Whitespace.NOWRAP);
        crumbBar = new Div(breadcrumbsSlot, subtitleSlot, actionsSlot);
        crumbBar.setWidthFull();
        crumbBar.addClassNames(AuraUtility.Display.FLEX, AuraUtility.AlignItems.CENTER,
                AuraUtility.Gap.MEDIUM, AuraUtility.Padding.Horizontal.MEDIUM,
                AuraUtility.Padding.Vertical.XSMALL, AuraUtility.BoxSizing.BORDER);
        actionsSlot.addClassNames(AuraUtility.Flex.SHRINK_NONE);
        contentCard.add(crumbBar);
        contentWrapper.add(contentCard);

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
            el.getClassList().add(AuraUtility.Flex.AUTO);
            el.getClassList().add(AuraUtility.MinHeight.NONE);
            el.getClassList().add(AuraUtility.MinWidth.NONE);
            contentCard.getElement().appendChild(el);
        }
    }

    /**
     * Honour {@code ?lang=<tag>} as a deterministic locale switch (QA /
     * preview / direct-link sharing). The locale itself is restored by
     * {@link LocaleSelector}'s {@code UIInitListener} before any view
     * renders, so no setLocale needed here.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
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
        // Views that publish a non-trivial trail themselves implement
        // {@link HasBreadcrumbs}; for everything else we synthesize a
        // default "Home → <page title>" trail (or just "Home" when we're
        // already on the home page) so every top-level route surfaces a
        // breadcrumb without each view having to duplicate the wiring.
        if (!(currentRoutedContent instanceof HasBreadcrumbs)) {
            breadcrumbsService.clear();
            publishDefaultBreadcrumb(event);
        }
        renderToolbarActions();
        renderToolbarSubtitle();
        renderBreadcrumbs();
        renderToolbarSearch();
    }

    private void renderToolbarActions() {
        actionsSlot.removeAll();
        if (currentRoutedContent instanceof HasToolbarActions a) {
            Component c = a.toolbarActions();
            if (c != null) {
                actionsSlot.add(c);
            }
        }
    }

    private void renderToolbarSubtitle() {
        subtitleSlot.removeAll();
        if (currentRoutedContent instanceof HasToolbarSubtitle s) {
            Component c = s.toolbarSubtitle();
            if (c != null) {
                subtitleSlot.add(c);
            }
        }
    }

    private void renderToolbarSearch() {
        searchSlot.removeAll();
        if (currentRoutedContent instanceof HasToolbarSearch s) {
            Component c = s.toolbarSearch();
            if (c != null) {
                searchSlot.add(c);
            }
        }
    }

    /**
     * Build the fallback trail for views that don't implement
     * {@link HasBreadcrumbs}. The view's page-title bundle key (via
     * {@link TitleKey}) drives the leaf label, so the trail is locale-aware
     * for free. Skips when the routed view has no title or when the user
     * is already on the home page (no point in "Home → Home").
     */
    private void publishDefaultBreadcrumb(AfterNavigationEvent event) {
        if (!(currentRoutedContent instanceof TitleKey titled)) {
            return;
        }
        String path = event.getLocation().getPath();
        String homeLabel = getTranslation("translate.breadcrumb.home");
        String pageLabel = getTranslation(titled.pageTitleKey());
        if (path == null || path.isEmpty() || "/".equals(path)) {
            // Home page: no breadcrumb.
            breadcrumbsService.clear();
        } else if (path.startsWith("admin/") && !path.equals("admin/home")) {
            // Admin sub-pages get an intermediate "Administration" crumb that
            // links back to the admin landing page.
            breadcrumbsService.set(
                    BreadcrumbsService.Crumb.of(homeLabel, "/"),
                    BreadcrumbsService.Crumb.of(
                            getTranslation("page.administration"),
                            "/admin/home"),
                    BreadcrumbsService.Crumb.here(pageLabel)
            );
        } else if (!path.contains("/")) {
            // Top-level nav page (Projects, Groups, …): the horizontal nav
            // already shows where you are, so no breadcrumb.
            breadcrumbsService.clear();
        } else {
            breadcrumbsService.set(
                    BreadcrumbsService.Crumb.of(homeLabel, "/"),
                    BreadcrumbsService.Crumb.here(pageLabel)
            );
        }
    }

    private void renderBreadcrumbs() {
        breadcrumbsSlot.removeAll();
        List<BreadcrumbsService.Crumb> crumbs = breadcrumbsService.current();
        boolean hasActions = actionsSlot.getElement().getChildCount() > 0;
        if (crumbBar != null) {
            crumbBar.setVisible(!crumbs.isEmpty() || hasActions);
        }
        if (crumbs.isEmpty()) return;
        Breadcrumbs widget = new Breadcrumbs();
        for (BreadcrumbsService.Crumb c : crumbs) {
            // Current-page crumbs use the single-arg (text-only) constructor —
            // an item with no path renders as a non-link and the web component
            // marks the last such item as aria-current="page". Passing null to
            // the (String text, String path) constructor would be an ambiguous
            // overload (String vs Class), so branch explicitly.
            BreadcrumbsItem item = c.current()
                    ? new BreadcrumbsItem(c.label())
                    : new BreadcrumbsItem(c.label(), c.href());
            widget.add(item);
        }
        breadcrumbsSlot.add(widget);
    }

    /**
     * The in-content toolbar: drawer toggle (single, always-visible), the
     * per-view breadcrumbs (left), and the global controls cluster on the
     * right (theme toggle, locale picker, sign-in / user menu).
     */
    private Component buildToolbar() {
        Span logo = new Span(getTranslation("brand.name"));
        logo.addClassNames(AuraUtility.FontSize.LARGE, AuraUtility.FontWeight.BOLD,
                AuraUtility.LineHeight.NONE);
        logo.getStyle().set("letter-spacing", "-0.02em");
        logo.getStyle().set("color",
                "var(--aura-green-text, var(--vaadin-input-field-label-color))");
        Anchor brand = new Anchor("/", logo);
        brand.addClassNames(AuraUtility.TextDecoration.NONE, AuraUtility.Display.FLEX,
                AuraUtility.AlignItems.CENTER);
        brand.getStyle().set("flex", "0 0 auto");

        HorizontalLayout left = new HorizontalLayout(brand, buildHorizontalNav());
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(true);
        left.addClassNames(AuraUtility.MinWidth.NONE);

        searchSlot.addClassName("toolbar-search");

        Div controls = new Div();
        controls.addClassName("controls");
        if (Roles.isCurrentUserAdmin()) {
            controls.add(buildAdminButton());
        }
        controls.add(buildThemeButton(), buildLocalePicker(), buildAuthControl());

        Div row = new Div(left, searchSlot, controls);
        row.addClassName("zanata-toolbar");
        return row;
    }

    /** Horizontal primary navigation (Home, Projects, Groups, …) in the navbar. */
    private Component buildHorizontalNav() {
        HorizontalLayout nav = new HorizontalLayout();
        nav.setSpacing(true);
        nav.setAlignItems(FlexComponent.Alignment.CENTER);
        nav.addClassNames(AuraUtility.MinWidth.NONE);
        if (homeContentService.isHomeEnabled()) {
            nav.add(navLink(getTranslation("nav.home"), HomeView.class));
        }
        nav.add(navLink(getTranslation("nav.explore"), ExploreView.class));
        nav.add(navLink(getTranslation("nav.groups"), GroupsView.class));
        nav.add(navLink(getTranslation("nav.languages"), LanguagesView.class));
        nav.add(navLink(getTranslation("nav.glossary"), GlossaryView.class));
        nav.add(navLink(getTranslation("nav.activity"), ActivityView.class));
        return nav;
    }

    private RouterLink navLink(String label,
            Class<? extends Component> view) {
        RouterLink link = new RouterLink(label, view);
        link.addClassNames("nav-link", AuraUtility.TextDecoration.NONE,
                AuraUtility.FontWeight.MEDIUM);
        link.getStyle().set("white-space", "nowrap");
        link.setHighlightCondition(HighlightConditions.sameLocation());
        link.setHighlightAction(HighlightActions.toggleAttribute("highlight"));
        return link;
    }


    // ------------------------------------------------------------------
    // Navbar controls (right cluster)
    // ------------------------------------------------------------------

    /**
     * Admin shortcut — an icon-only button next to the theme switcher, shown
     * only to admins. Replaces the old text "Admin" entry in the primary nav.
     */
    private Component buildAdminButton() {
        Button admin = new Button(LineAwesomeIcon.USER_SHIELD_SOLID.create(),
                e -> UI.getCurrent().navigate(AdminHomeView.class));
        admin.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        admin.getElement().setAttribute("title", getTranslation("nav.admin"));
        admin.getElement().setAttribute("aria-label", getTranslation("nav.admin"));
        return admin;
    }

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

        List<MenuItem> choices = new ArrayList<>();
        for (ThemeSelector.Mode target : ThemeSelector.Mode.values()) {
            String key = "theme." + target.name().toLowerCase(Locale.ROOT);
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
        label.addClassNames(AuraUtility.Gap.SMALL);
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
                avatarService.avatarForUsername(currentUsername(), 18),
                new Span(currentUsername()));
        label.setSpacing(false);
        label.setAlignItems(FlexComponent.Alignment.CENTER);
        label.addClassNames(AuraUtility.Gap.SMALL);
        MenuItem trigger = bar.addItem(label);
        trigger.getSubMenu().addItem(getTranslation("nav.profile"),
                e -> UI.getCurrent().navigate(ProfileView.class));
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
        intro.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);

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
