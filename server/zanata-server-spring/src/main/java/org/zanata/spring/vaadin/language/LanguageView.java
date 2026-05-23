package org.zanata.spring.vaadin.language;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.common.LocaleId;
import org.zanata.model.HLocale;
import org.zanata.spring.repository.LocaleMemberRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "language/:slug", layout = MainLayout.class)
@PageTitle("Language | Zanata")
@AnonymousAllowed
public class LanguageView extends VerticalLayout implements BeforeEnterObserver {

    private final LocaleRepository localeRepository;
    private final LocaleMemberRepository localeMemberRepository;

    public LanguageView(LocaleRepository localeRepository,
                        LocaleMemberRepository localeMemberRepository) {
        this.localeRepository = localeRepository;
        this.localeMemberRepository = localeMemberRepository;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HLocale locale = localeRepository.findByLocaleId(new LocaleId(slug))
                .orElseThrow(() -> new NotFoundException("Language not found: " + slug));

        add(new H2(locale.getLocaleId().getId()));
        add(new Paragraph("Display name: "
                + (locale.getDisplayName() == null ? "" : locale.getDisplayName())));
        long members = localeMemberRepository.countByLocaleId(locale.getId());
        add(new Paragraph("Members: " + members));
    }
}
