package org.zanata.spring.vaadin;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.zanata.spring.i18n.TitleKey;

/**
 * Compatibility entry point at {@code /login}. Spring Security redirects
 * unauthenticated users here when they hit a protected route, and old
 * bookmarks / deep links still resolve. Visually this view is just a blank
 * backdrop — the sign-in form is rendered as a popover by
 * {@link LoginDialogService}.
 *
 * <p>On successful login the user is sent back to the originally-requested
 * URL (Spring Security's {@code SavedRequest}) or the home page if there
 * isn't one.
 */
@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, TitleKey {

    @Override public String pageTitleKey() { return "page.signIn"; }

    private final LoginDialogService loginDialogService;

    public LoginView(LoginDialogService loginDialogService) {
        this.loginDialogService = loginDialogService;
        setSizeFull();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Open the popover automatically — without this the user lands on a
        // blank page when Spring Security redirects them here.
        loginDialogService.open(resolveSavedRequestPath());
    }

    /**
     * If Spring Security stashed a {@code SavedRequest} in the session before
     * redirecting here, return its URL so a successful sign-in restores the
     * deep link. Otherwise return {@code "/"}.
     */
    private static String resolveSavedRequestPath() {
        var req = VaadinService.getCurrentRequest();
        if (!(req instanceof VaadinServletRequest vsr)) return "/";
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        SavedRequest saved = cache.getRequest(vsr.getHttpServletRequest(), null);
        return saved != null ? saved.getRedirectUrl() : "/";
    }
}
