package org.verbaria.server.ui.vaadin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.common.LocaleId;
import org.zanata.model.HLocale;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.security.Roles;

@AnonymousAllowed
@Route(value = "languages", layout = MainLayout.class)
public class LanguagesView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.languages"; }

    private final LocaleRepository localeRepository;
    private final Grid<HLocale> grid = new Grid<>(HLocale.class, false);

    public LanguagesView(LocaleRepository localeRepository) {
        this.localeRepository = localeRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H2 title = new H2(getTranslation("languages.title"));
        HorizontalLayout header = new HorizontalLayout(title);
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        // Adding a language is an admin action; the read-only list stays public.
        if (Roles.isCurrentUserAdmin()) {
            Button add = new Button(getTranslation("languages.add"),
                    e -> openAddDialog());
            add.addThemeVariants(ButtonVariant.PRIMARY);
            header.add(add);
        }
        add(header);

        grid.addColumn(l -> l.getLocaleId() == null ? "" : l.getLocaleId().getId())
                .setHeader(getTranslation("languages.colLocale")).setSortable(true).setAutoWidth(true);
        grid.addColumn(HLocale::getDisplayName).setHeader(getTranslation("languages.colDisplayName")).setAutoWidth(true);
        grid.addColumn(HLocale::getNativeName).setHeader(getTranslation("languages.colNativeName")).setAutoWidth(true);
        grid.addColumn(l -> l.isActive() ? getTranslation("languages.statusActive") : getTranslation("languages.statusDisabled"))
                .setHeader(getTranslation("languages.colStatus")).setAutoWidth(true);
        grid.addColumn(l -> l.isEnabledByDefault() ? getTranslation("languages.defaultYes") : getTranslation("languages.defaultNo"))
                .setHeader(getTranslation("languages.colDefault")).setAutoWidth(true);

        refresh();
        grid.setSizeFull();
        grid.addItemClickListener(e -> {
            HLocale l = e.getItem();
            if (l != null && l.getLocaleId() != null) {
                getUI().ifPresent(ui -> ui.navigate(
                        "language/" + l.getLocaleId().getId()));
            }
        });

        add(grid);
    }

    private void refresh() {
        grid.setItems(localeRepository.findAll());
    }

    private void openAddDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("languages.add.title"));

        TextField locale = new TextField(getTranslation("languages.field.locale"));
        locale.setHelperText(getTranslation("languages.field.localeHint"));
        locale.setRequiredIndicatorVisible(true);
        TextField displayName =
                new TextField(getTranslation("languages.colDisplayName"));
        TextField nativeName =
                new TextField(getTranslation("languages.colNativeName"));
        Checkbox active = new Checkbox(getTranslation("languages.field.active"));
        active.setValue(true);

        FormLayout form = new FormLayout(locale, displayName, nativeName, active);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        dialog.add(form);

        Button save = new Button(getTranslation("languages.add.save"),
                e -> save(dialog, locale, displayName, nativeName, active));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(getTranslation("languages.add.cancel"),
                e -> dialog.close());
        dialog.getFooter().add(cancel, save);

        dialog.open();
    }

    private void save(Dialog dialog, TextField localeField,
            TextField displayName, TextField nativeName, Checkbox active) {
        // Re-check authorization on the action itself, not just the button.
        if (!Roles.isCurrentUserAdmin()) {
            return;
        }
        String code = localeField.getValue() == null ? ""
                : localeField.getValue().trim();
        if (code.isEmpty()) {
            localeField.setInvalid(true);
            localeField.setErrorMessage(
                    getTranslation("languages.add.localeRequired"));
            return;
        }
        try {
            LocaleId localeId = new LocaleId(code);
            if (localeRepository.findByLocaleId(localeId).isPresent()) {
                localeField.setInvalid(true);
                localeField.setErrorMessage(
                        getTranslation("languages.add.duplicate"));
                return;
            }
            // enabledByDefault stays false — projects pick their own languages.
            HLocale entity = new HLocale(localeId, false, active.getValue());
            entity.setDisplayName(trimToNull(displayName.getValue()));
            entity.setNativeName(trimToNull(nativeName.getValue()));
            localeRepository.save(entity);
            refresh();
            dialog.close();
            Notification n = Notification.show(
                    getTranslation("languages.add.saved", code));
            n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (RuntimeException ex) {
            localeField.setInvalid(true);
            localeField.setErrorMessage(
                    getTranslation("languages.add.invalid"));
        }
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
