package org.zanata.spring.vaadin.iteration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import org.springframework.transaction.annotation.Transactional;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.EntityStatus;
import org.zanata.common.ProjectType;
import org.zanata.model.HLocale;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.vaadin.MainLayout;

/**
 * Version Settings: General (project-type override, read-only toggle, delete)
 * and Languages (override + customized locale set). Mirrors the legacy
 * "Version settings" tabs documented at
 * /user-guide/versions/version-settings.
 */
@Route(value = "project/:projectSlug/version/:versionSlug/settings", layout = MainLayout.class)
@PageTitle("Version settings | Zanata")
@PermitAll
public class VersionSettingsView extends VerticalLayout implements BeforeEnterObserver {

    private final ProjectIterationRepository iterationRepository;
    private final LocaleRepository localeRepository;

    public VersionSettingsView(ProjectIterationRepository iterationRepository,
                               LocaleRepository localeRepository) {
        this.iterationRepository = iterationRepository;
        this.localeRepository = localeRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String projectSlug = event.getRouteParameters().get("projectSlug").orElse("");
        String versionSlug = event.getRouteParameters().get("versionSlug").orElse("");
        HProjectIteration iter = iterationRepository
                .findForSettings(projectSlug, versionSlug)
                .orElseThrow(() -> new NotFoundException(
                        "Version not found: " + projectSlug + "/" + versionSlug));

        add(buildCrumbs(projectSlug, versionSlug));
        add(new H2(projectSlug + " / " + versionSlug));

        add(buildGeneralCard(iter, projectSlug, versionSlug));
        add(buildLanguagesCard(iter, projectSlug, versionSlug));
    }

    private Breadcrumbs buildCrumbs(String projectSlug, String versionSlug) {
        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb("Home", "/"),
                new Breadcrumb("Projects", "/explore"),
                new Breadcrumb(projectSlug, "/project/view/" + projectSlug),
                new Breadcrumb(versionSlug,
                        "/project/" + projectSlug + "/version/" + versionSlug),
                new Breadcrumb("Settings", "#", true));
        return crumbs;
    }

    private VerticalLayout buildGeneralCard(HProjectIteration iter,
                                            String projectSlug, String versionSlug) {
        VerticalLayout card = card();
        card.add(new H3("General"));

        ComboBox<ProjectType> typeBox = new ComboBox<>("Project type");
        typeBox.setItems(ProjectType.values());
        typeBox.setValue(iter.getProjectType());
        typeBox.setClearButtonVisible(true);
        typeBox.setHelperText("Leave empty to inherit from the project's default type.");

        ComboBox<EntityStatus> statusBox = new ComboBox<>("Status");
        statusBox.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY, EntityStatus.OBSOLETE);
        statusBox.setValue(iter.getStatus() == null ? EntityStatus.ACTIVE : iter.getStatus());
        statusBox.setHelperText("READONLY hides translation buttons in the editor.");

        FormLayout form = new FormLayout(typeBox, statusBox);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        card.add(form);

        Button save = new Button("Save general", LineAwesomeIcon.SAVE_SOLID.create(),
                e -> saveGeneral(iter.getId(), typeBox.getValue(), statusBox.getValue()));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button toggleRO = new Button(
                iter.getStatus() == EntityStatus.READONLY ? "Mark as writable" : "Mark as read-only",
                LineAwesomeIcon.LOCK_SOLID.create(),
                e -> {
                    EntityStatus next = iter.getStatus() == EntityStatus.READONLY
                            ? EntityStatus.ACTIVE : EntityStatus.READONLY;
                    saveGeneral(iter.getId(), typeBox.getValue(), next);
                    UI.getCurrent().getPage().reload();
                });
        toggleRO.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button delete = new Button("Delete version", LineAwesomeIcon.TRASH_SOLID.create(),
                e -> confirmDelete(iter.getId(), projectSlug, versionSlug));
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout actions = new HorizontalLayout(save, toggleRO, delete);
        actions.setSpacing(true);
        card.add(actions);
        return card;
    }

    private VerticalLayout buildLanguagesCard(HProjectIteration iter,
                                              String projectSlug, String versionSlug) {
        VerticalLayout card = card();
        card.add(new H3("Languages"));
        Paragraph hint = new Paragraph(
                "When OFF, this version inherits the project's locale list. "
                + "When ON, only the languages picked below are available for translation.");
        hint.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        hint.getStyle().set("font-size", "0.9rem");
        card.add(hint);

        Checkbox override = new Checkbox("Override the project's locale list");
        override.setValue(iter.isOverrideLocales());

        MultiSelectComboBox<HLocale> locales = new MultiSelectComboBox<>("Customized locales");
        List<HLocale> all = localeRepository.findAll();
        locales.setItems(all);
        locales.setItemLabelGenerator(this::localeLabel);
        locales.setWidthFull();
        locales.setValue(new LinkedHashSet<>(iter.getCustomizedLocales()));
        locales.setEnabled(override.getValue());
        override.addValueChangeListener(e -> locales.setEnabled(Boolean.TRUE.equals(e.getValue())));

        Button save = new Button("Save languages", LineAwesomeIcon.SAVE_SOLID.create(), e -> {
            saveLanguages(iter.getId(), override.getValue(), locales.getValue());
            UI.getCurrent().getPage().reload();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(override, locales, save);
        return card;
    }

    private void confirmDelete(Long iterId, String projectSlug, String versionSlug) {
        ConfirmDialog dlg = new ConfirmDialog(
                "Delete version " + projectSlug + "/" + versionSlug + "?",
                "This will mark the version OBSOLETE — it disappears from project lists "
                + "and from the API. Data is kept in the database so it can be restored "
                + "by an admin if needed. Continue?",
                "Delete",
                ev -> {
                    softDelete(iterId);
                    Notification n = Notification.show(
                            "Version " + versionSlug + " marked OBSOLETE",
                            3000, Notification.Position.BOTTOM_END);
                    n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
                    UI.getCurrent().navigate("project/view/" + projectSlug);
                },
                "Cancel",
                ev -> {});
        dlg.setConfirmButtonTheme("primary error");
        dlg.open();
    }

    @Transactional
    public void saveGeneral(Long iterId, ProjectType type, EntityStatus status) {
        iterationRepository.findById(iterId).ifPresent(it -> {
            it.setProjectType(type);
            it.setStatus(status == null ? EntityStatus.ACTIVE : status);
            iterationRepository.save(it);
        });
        Notification.show("Saved", 2000, Notification.Position.BOTTOM_END);
    }

    @Transactional
    public void saveLanguages(Long iterId, boolean override, Set<HLocale> picked) {
        iterationRepository.findForSettingsById(iterId).ifPresent(it -> {
            it.setOverrideLocales(override);
            it.getCustomizedLocales().clear();
            if (override && picked != null && !picked.isEmpty()) {
                it.getCustomizedLocales().addAll(picked);
            }
            iterationRepository.save(it);
        });
        Notification.show("Saved", 2000, Notification.Position.BOTTOM_END);
    }

    @Transactional
    public void softDelete(Long iterId) {
        iterationRepository.findById(iterId).ifPresent(it -> {
            it.setStatus(EntityStatus.OBSOLETE);
            iterationRepository.save(it);
        });
    }

    private static VerticalLayout card() {
        VerticalLayout c = new VerticalLayout();
        c.setWidthFull();
        c.setPadding(true);
        c.setSpacing(true);
        c.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        c.getStyle().set("border-radius", "8px");
        c.getStyle().set("background", "var(--vaadin-background-color)");
        c.getStyle().set("margin-bottom", "1rem");
        return c;
    }

    private String localeLabel(HLocale l) {
        String code = l.getLocaleId() == null ? "" : l.getLocaleId().getId();
        return (l.getDisplayName() == null ? code : l.getDisplayName()) + " (" + code + ")";
    }
}
