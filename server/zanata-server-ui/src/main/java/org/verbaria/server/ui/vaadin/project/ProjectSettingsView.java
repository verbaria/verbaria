package org.verbaria.server.ui.vaadin.project;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import org.springframework.transaction.annotation.Transactional;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.zanata.common.EntityStatus;
import org.zanata.common.MessageEvaluateType;
import org.zanata.common.ProjectType;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.ui.vaadin.BreadcrumbsService;
import org.verbaria.server.ui.vaadin.HasBreadcrumbs;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "project/view/:slug/settings", layout = MainLayout.class)
@PermitAll
public class ProjectSettingsView extends VerticalLayout implements BeforeEnterObserver, TitleKey, HasBreadcrumbs {

    @Override public String pageTitleKey() { return "page.projectSettings"; }


    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final LocaleRepository localeRepository;
    private final BreadcrumbsService breadcrumbsService;

    public ProjectSettingsView(ProjectRepository projectRepository,
                               ProjectIterationRepository iterationRepository,
                               LocaleRepository localeRepository,
                               BreadcrumbsService breadcrumbsService) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.localeRepository = localeRepository;
        this.breadcrumbsService = breadcrumbsService;
        setWidthFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HProject project = projectRepository.findBySlugWithLocales(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));

        breadcrumbsService.set(
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.home"), "/"),
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.projects"), "/explore"),
                BreadcrumbsService.Crumb.of(slug, "/project/view/" + slug),
                BreadcrumbsService.Crumb.here(getTranslation("page.settings")));
        add(new H2(project.getName() == null ? slug : project.getName()));

        List<HLocale> allLocales = localeRepository.findAll();
        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(getTranslation("projectSettings.tab.general"),
                buildDetailsTab(project, slug, allLocales));
        tabs.add(getTranslation("project.tab.versions"),
                buildVersionsTab(slug, allLocales));
        add(tabs);
    }

    private VerticalLayout buildDetailsTab(HProject project, String slug,
                                           List<HLocale> allLocales) {
        VerticalLayout tab = new VerticalLayout();
        tab.setPadding(false);
        tab.setSpacing(true);

        TextField name = new TextField(getTranslation("projectCreate.name"));
        name.setValue(project.getName() == null ? "" : project.getName());
        TextArea description = new TextArea(getTranslation("projectCreate.description"));
        description.setMaxLength(255);
        description.setValue(project.getDescription() == null ? "" : project.getDescription());
        ComboBox<ProjectType> defaultType = new ComboBox<>(getTranslation("projectCreate.type"));
        defaultType.setItems(ProjectType.values());
        defaultType.setValue(project.getDefaultProjectType() == null
                ? ProjectType.Gettext : project.getDefaultProjectType());
        ComboBox<EntityStatus> status = new ComboBox<>(getTranslation("projectCreate.status"));
        status.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY, EntityStatus.OBSOLETE);
        status.setValue(project.getStatus() == null ? EntityStatus.ACTIVE : project.getStatus());
        ComboBox<MessageEvaluateType> messageFormat =
                new ComboBox<>(getTranslation("projectSettings.messageFormat"));
        messageFormat.setItems(MessageEvaluateType.values());
        messageFormat.setHelperText(
                getTranslation("projectSettings.messageFormatHint"));
        messageFormat.setValue(project.getMessageEvaluateType());
        TextField sourceView = new TextField(getTranslation("projectSettings.sourceViewUrl"));
        sourceView.setValue(project.getSourceViewURL() == null ? "" : project.getSourceViewURL());
        TextField sourceCheckout = new TextField(getTranslation("projectSettings.sourceCheckoutUrl"));
        sourceCheckout.setValue(project.getSourceCheckoutURL() == null ? "" : project.getSourceCheckoutURL());

        ComboBox<HLocale> sourceLocale = new ComboBox<>(getTranslation("projectCreate.sourceLocale"));
        sourceLocale.setItems(allLocales);
        sourceLocale.setItemLabelGenerator(this::localeLabel);
        if (project.getDefaultSourceLocale() != null) {
            sourceLocale.setValue(project.getDefaultSourceLocale());
        }

        FormLayout form = new FormLayout(name, description, defaultType, status,
                messageFormat, sourceLocale, sourceView, sourceCheckout);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        tab.add(form);

        tab.add(new H3(getTranslation("projectSettings.targetLanguages")));
        Paragraph hint = new Paragraph(getTranslation("projectSettings.targetLanguagesHint"));
        hint.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.Bottom.SMALL);
        tab.add(hint);

        MultiSelectComboBox<HLocale> targetLocales =
                new MultiSelectComboBox<>(getTranslation("projectSettings.targetLocales"));
        targetLocales.setItems(allLocales);
        targetLocales.setItemLabelGenerator(this::localeLabel);
        targetLocales.setWidthFull();
        targetLocales.setAllowCustomValue(false);
        if (project.getCustomizedLocales() != null) {
            targetLocales.setValue(project.getCustomizedLocales());
        }
        tab.add(targetLocales);

        Button save = new Button(getTranslation("common.save"), e -> {
            project.setName(name.getValue());
            project.setDescription(description.getValue());
            project.setDefaultProjectType(defaultType.getValue());
            project.setStatus(status.getValue());
            project.setMessageEvaluateType(messageFormat.getValue() == null
                    ? MessageEvaluateType.NONE : messageFormat.getValue());
            project.setSourceViewURL(sourceView.getValue());
            project.setSourceCheckoutURL(sourceCheckout.getValue());
            project.setDefaultSourceLocale(sourceLocale.getValue());
            Set<HLocale> picked = new LinkedHashSet<>(targetLocales.getValue());
            project.setCustomizedLocales(picked);
            project.setOverrideLocales(!picked.isEmpty());
            projectRepository.save(project);
            Notification.show(getTranslation("common.saved"), 2000, Notification.Position.BOTTOM_START);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        Button back = new Button(getTranslation("projectSettings.back"),
                e -> getUI().ifPresent(ui -> ui.navigate("project/view/" + slug)));
        tab.add(new HorizontalLayout(save, back));
        return tab;
    }

    private VerticalLayout buildVersionsTab(String slug, List<HLocale> allLocales) {
        VerticalLayout tab = new VerticalLayout();
        tab.setPadding(false);
        tab.setSpacing(true);
        tab.setWidthFull();

        Button addVersion = new Button(getTranslation("projectSettings.addVersion"),
                LineAwesomeIcon.PLUS_SOLID.create(), e -> openAddVersionDialog(slug));
        addVersion.addThemeVariants(ButtonVariant.PRIMARY);
        tab.add(addVersion);

        List<HProjectIteration> versions =
                iterationRepository.findForSettingsByProject(slug);
        if (versions.isEmpty()) {
            Paragraph none = new Paragraph(getTranslation("projectSettings.noVersions"));
            none.addClassNames(AuraUtility.TextColor.SECONDARY);
            tab.add(none);
        } else {
            for (HProjectIteration iter : versions) {
                tab.add(buildVersionCard(iter, slug, allLocales));
            }
        }
        return tab;
    }

    private void openAddVersionDialog(String slug) {
        TextField versionSlug = new TextField(getTranslation("projectSettings.versionSlug"));
        versionSlug.setWidthFull();
        versionSlug.setHelperText(getTranslation("projectSettings.versionSlugHint"));
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(getTranslation("projectSettings.addVersion"));
        dialog.add(versionSlug);
        Button create = new Button(getTranslation("common.create"), e -> {
            String v = versionSlug.getValue() == null ? "" : versionSlug.getValue().trim();
            if (v.isBlank()) {
                versionSlug.setInvalid(true);
                versionSlug.setErrorMessage(getTranslation("projectSettings.versionSlugRequired"));
                return;
            }
            if (iterationRepository.findByProjectAndSlug(slug, v).isPresent()) {
                versionSlug.setInvalid(true);
                versionSlug.setErrorMessage(getTranslation("projectSettings.versionExists"));
                return;
            }
            createVersion(slug, v);
            dialog.close();
            UI.getCurrent().getPage().reload();
        });
        create.addThemeVariants(ButtonVariant.PRIMARY);
        Button cancel = new Button(getTranslation("common.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, create);
        dialog.open();
    }

    @Transactional
    public void createVersion(String projectSlug, String versionSlug) {
        projectRepository.findBySlug(projectSlug).ifPresent(project -> {
            HProjectIteration v = new HProjectIteration();
            v.setSlug(versionSlug);
            v.setStatus(EntityStatus.ACTIVE);
            v.setProject(project);
            iterationRepository.save(v);
        });
        Notification.show(getTranslation("common.saved"), 2000, Notification.Position.BOTTOM_END);
    }

    private VerticalLayout buildVersionCard(HProjectIteration iter, String slug,
                                            List<HLocale> allLocales) {
        VerticalLayout card = new VerticalLayout();
        card.setWidthFull();
        card.setPadding(true);
        card.setSpacing(true);
        card.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Margin.Bottom.MEDIUM);

        HorizontalLayout head = new HorizontalLayout(
                new H3(iter.getSlug()));
        head.setAlignItems(FlexComponent.Alignment.CENTER);
        if (iter.getStatus() == EntityStatus.READONLY) {
            Span ro = new Span(getTranslation("versionSettings.status") + ": "
                    + EntityStatus.READONLY);
            ro.addClassNames(AuraUtility.FontSize.SMALL, AuraUtility.TextColor.SECONDARY);
            head.add(ro);
        }
        card.add(head);

        ComboBox<ProjectType> typeBox = new ComboBox<>(getTranslation("versionSettings.projectType"));
        typeBox.setItems(ProjectType.values());
        typeBox.setValue(iter.getProjectType());
        typeBox.setClearButtonVisible(true);
        typeBox.setHelperText(getTranslation("versionSettings.projectTypeHint"));

        ComboBox<EntityStatus> statusBox = new ComboBox<>(getTranslation("versionSettings.status"));
        statusBox.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY, EntityStatus.OBSOLETE);
        statusBox.setValue(iter.getStatus() == null ? EntityStatus.ACTIVE : iter.getStatus());

        Checkbox override = new Checkbox(getTranslation("versionSettings.overrideLocales"));
        override.setValue(iter.isOverrideLocales());
        MultiSelectComboBox<HLocale> locales =
                new MultiSelectComboBox<>(getTranslation("versionSettings.customizedLocales"));
        locales.setItems(allLocales);
        locales.setItemLabelGenerator(this::localeLabel);
        locales.setWidthFull();
        locales.setValue(new LinkedHashSet<>(iter.getCustomizedLocales()));
        locales.setEnabled(override.getValue());
        override.addValueChangeListener(e -> locales.setEnabled(Boolean.TRUE.equals(e.getValue())));

        FormLayout form = new FormLayout(typeBox, statusBox);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        card.add(form, override, locales);

        Button saveVersion = new Button(getTranslation("common.save"),
                LineAwesomeIcon.SAVE_SOLID.create(), e -> {
            saveVersion(iter.getId(), typeBox.getValue(), statusBox.getValue(),
                    override.getValue(), locales.getValue());
            UI.getCurrent().getPage().reload();
        });
        saveVersion.addThemeVariants(ButtonVariant.PRIMARY);
        Button delete = new Button(getTranslation("versionSettings.delete"),
                LineAwesomeIcon.TRASH_SOLID.create(),
                e -> confirmDelete(iter.getId(), slug, iter.getSlug()));
        delete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        card.add(new HorizontalLayout(saveVersion, delete));
        return card;
    }

    private void confirmDelete(Long iterId, String projectSlug, String versionSlug) {
        ConfirmDialog dlg = new ConfirmDialog(
                getTranslation("versionSettings.deleteConfirmTitle", projectSlug, versionSlug),
                getTranslation("versionSettings.deleteConfirmBody"),
                getTranslation("common.delete"),
                ev -> {
                    softDelete(iterId);
                    Notification.show(
                            getTranslation("versionSettings.deleteResult", versionSlug),
                            3000, Notification.Position.BOTTOM_END);
                    UI.getCurrent().getPage().reload();
                },
                getTranslation("common.cancel"), ev -> {});
        dlg.setConfirmButtonTheme("primary error");
        dlg.open();
    }

    @Transactional
    public void saveVersion(Long iterId, ProjectType type, EntityStatus status,
                            boolean override, Set<HLocale> picked) {
        iterationRepository.findForSettingsById(iterId).ifPresent(it -> {
            it.setProjectType(type);
            it.setStatus(status == null ? EntityStatus.ACTIVE : status);
            it.setOverrideLocales(override);
            it.getCustomizedLocales().clear();
            if (override && picked != null && !picked.isEmpty()) {
                it.getCustomizedLocales().addAll(picked);
            }
            iterationRepository.save(it);
        });
        Notification.show(getTranslation("common.saved"), 2000, Notification.Position.BOTTOM_END);
    }

    @Transactional
    public void softDelete(Long iterId) {
        iterationRepository.findById(iterId).ifPresent(it -> {
            it.setStatus(EntityStatus.OBSOLETE);
            iterationRepository.save(it);
        });
    }

    private String localeLabel(HLocale l) {
        if (l == null) return "";
        String code = l.getLocaleId() == null ? "?" : l.getLocaleId().getId();
        String display = l.getDisplayName();
        return display == null || display.isBlank() ? code : display + " (" + code + ")";
    }
}
