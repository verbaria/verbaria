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
import com.vaadin.flow.component.grid.Grid;
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
import org.verbaria.server.headless.layout.DocumentLayoutRegistry;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.service.ProjectHierarchyService;
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
    private final ProjectHierarchyService hierarchyService;
    private final DocumentLayoutRegistry layoutRegistry;
    /** The general-settings Save/Back row, placed at the very bottom of the page. */
    private HorizontalLayout settingsActionsRow;
    /**
     * In-memory version drafts — the grid edits these; nothing is persisted until
     * the page's Save button. New versions have a null id; removed existing
     * versions are tracked in {@link #removedVersionIds}.
     */
    private List<HProjectIteration> versionDrafts;
    private Set<Long> removedVersionIds;
    private Grid<HProjectIteration> versionsGrid;
    /** Wrapper for the versions grid; rebuilt live when the parent changes. */
    private VerticalLayout versionsContainer;

    public ProjectSettingsView(ProjectRepository projectRepository,
                               ProjectIterationRepository iterationRepository,
                               LocaleRepository localeRepository,
                               BreadcrumbsService breadcrumbsService,
                               ProjectHierarchyService hierarchyService,
                               DocumentLayoutRegistry layoutRegistry) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.localeRepository = localeRepository;
        this.breadcrumbsService = breadcrumbsService;
        this.hierarchyService = hierarchyService;
        this.layoutRegistry = layoutRegistry;
        // The MainLayout content card is overflow:hidden, so the view must size
        // to the area and scroll its own (long) content.
        setSizeFull();
        addClassNames(AuraUtility.Overflow.AUTO);
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
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.projects"), "/projects"),
                BreadcrumbsService.Crumb.of(slug, "/project/view/" + slug),
                BreadcrumbsService.Crumb.here(getTranslation("page.settings")));
        add(new H2(project.getName() == null ? slug : project.getName()));

        List<HLocale> allLocales = localeRepository.findAll();
        // One page (no tabs): general settings, the versions grid, then a single
        // Save/Back row at the very bottom.
        add(buildDetailsTab(project, slug, allLocales));
        H3 versionsHeading = new H3(getTranslation("project.tab.versions"));
        versionsHeading.addClassNames(AuraUtility.Margin.Top.MEDIUM);
        add(versionsHeading);
        // When this project inherits from a parent, its versions (and their
        // settings) come from the parent — the grid is locked and "Add version"
        // is hidden. Rebuilt live by the parent picker (see buildDetailsTab).
        versionsContainer = new VerticalLayout();
        versionsContainer.setPadding(false);
        versionsContainer.setSpacing(true);
        versionsContainer.setWidthFull();
        add(versionsContainer);
        add(settingsActionsRow);
        refreshVersions(slug, allLocales, project.getParentProject() != null);
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
        ComboBox<String> defaultType = new ComboBox<>(getTranslation("projectCreate.type"));
        defaultType.setItems(layoutRegistry.knownTypes());
        defaultType.setValue(project.getDefaultProjectType() == null
                ? "gettext" : project.getDefaultProjectType());
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
        sourceView.setHelperText(getTranslation("projectSettings.sourceViewUrlHint"));

        ComboBox<HProject> parent = new ComboBox<>(getTranslation("projectSettings.parentProject"));
        parent.setItems(projectRepository.findAllByOrderBySlugAsc().stream()
                .filter(c -> !c.getSlug().equals(slug))
                .filter(c -> c.getParentProject() == null)
                .toList());
        parent.setItemLabelGenerator(this::projectLabel);
        parent.setClearButtonVisible(true);
        parent.setHelperText(getTranslation("projectSettings.parentProjectHint"));
        if (project.getParentProject() != null) {
            parent.setValue(project.getParentProject());
        }

        ComboBox<HLocale> sourceLocale = new ComboBox<>(getTranslation("projectCreate.sourceLocale"));
        sourceLocale.setItems(allLocales);
        sourceLocale.setItemLabelGenerator(this::localeLabel);
        if (project.getDefaultSourceLocale() != null) {
            sourceLocale.setValue(project.getDefaultSourceLocale());
        }

        FormLayout form = new FormLayout(name, description, defaultType, status,
                messageFormat, parent, sourceLocale, sourceView);
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

        // When a parent is chosen, languages / type / source locale / source URL
        // are inherited: preview the inherited values and lock the fields. When
        // the parent is cleared, the fields stay populated and become editable —
        // so removing a parent copies its settings down into this (now
        // standalone) project, ready to save.
        Paragraph inheritedNote = new Paragraph();
        inheritedNote.addClassNames(AuraUtility.TextColor.SECONDARY,
                AuraUtility.FontSize.SMALL);
        tab.add(inheritedNote);
        parent.addValueChangeListener(e -> {
            applyParentState(e.getValue(), slug, project.getName(), defaultType,
                    sourceLocale, targetLocales, sourceView, messageFormat, inheritedNote);
            // Lock/unlock the versions grid + hide/show "Add version" live.
            refreshVersions(slug, allLocales, e.getValue() != null);
        });
        applyParentState(parent.getValue(), slug, project.getName(), defaultType,
                sourceLocale, targetLocales, sourceView, messageFormat, inheritedNote);

        Button save = new Button(getTranslation("common.save"), e -> {
            HProject chosen = parent.getValue();
            ProjectHierarchyService.LinkError err =
                    hierarchyService.validate(project, chosen);
            if (err != null) {
                Notification.show(
                        getTranslation("projectSettings.parentInvalid." + err.name()),
                        4000, Notification.Position.MIDDLE);
                return;
            }
            project.setName(name.getValue());
            project.setDescription(description.getValue());
            project.setStatus(status.getValue());
            if (chosen != null) {
                // Inherit from the parent: keep no own copy of these settings.
                project.setDefaultProjectType(null);
                project.setDefaultSourceLocale(null);
                project.setSourceViewURL(null);
                project.setCustomizedLocales(new LinkedHashSet<>());
                project.setOverrideLocales(false);
                project.setMessageEvaluateType(null);
            } else {
                // Standalone (or just-detached): persist the shown values as own.
                project.setDefaultProjectType(defaultType.getValue());
                project.setDefaultSourceLocale(sourceLocale.getValue());
                project.setSourceViewURL(sourceView.getValue());
                Set<HLocale> picked = new LinkedHashSet<>(targetLocales.getValue());
                project.setCustomizedLocales(picked);
                project.setOverrideLocales(!picked.isEmpty());
                project.setMessageEvaluateType(messageFormat.getValue() == null
                        ? MessageEvaluateType.NONE : messageFormat.getValue());
            }
            // Sets/clears parent + mirrors the parent's versions onto this project.
            hierarchyService.linkParent(project, chosen);
            // Persist all version edits/adds/removes in the same Save action.
            persistVersions(slug);
            // Re-sync the grid from the DB (new ids assigned, removed gone) —
            // no full page reload.
            versionDrafts = new java.util.ArrayList<>(
                    iterationRepository.findForSettingsByProject(slug));
            removedVersionIds.clear();
            versionsGrid.setItems(versionDrafts);
            Notification.show(getTranslation("common.saved"), 2000, Notification.Position.BOTTOM_START);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        Button back = new Button(getTranslation("projectSettings.back"),
                e -> getUI().ifPresent(ui -> ui.navigate("project/view/" + slug)));
        // Placed at the very bottom of the page (after the versions grid) by
        // beforeEnter, not inside the general section.
        settingsActionsRow = new HorizontalLayout(save, back);
        return tab;
    }

    /**
     * Lock + preview the inherited fields when a parent is selected; leave the
     * (now copied) values editable when none is. Pulls the effective values
     * through a transactional service call to avoid touching detached entities.
     */
    private void applyParentState(HProject selectedParent, String slug,
            String projectName, ComboBox<String> defaultType,
            ComboBox<HLocale> sourceLocale,
            MultiSelectComboBox<HLocale> targetLocales, TextField sourceView,
            ComboBox<MessageEvaluateType> messageFormat,
            Paragraph inheritedNote) {
        boolean inherited = selectedParent != null;
        defaultType.setReadOnly(inherited);
        sourceLocale.setReadOnly(inherited);
        targetLocales.setReadOnly(inherited);
        sourceView.setReadOnly(inherited);
        messageFormat.setReadOnly(inherited);
        if (!inherited) {
            inheritedNote.setText("");
            return;
        }
        ProjectHierarchyService.EffectiveSettings es =
                hierarchyService.effectiveSettings(selectedParent.getSlug());
        if (es.type() != null) {
            defaultType.setValue(es.type());
        }
        sourceLocale.setValue(es.sourceLocale());
        targetLocales.setValue(new LinkedHashSet<>(es.locales()));
        String url = HProject.expandUrlMacros(es.sourceUrlTemplate(), slug, projectName);
        sourceView.setValue(url == null ? "" : url);
        messageFormat.setValue(es.messageFormat());
        inheritedNote.setText(getTranslation("projectSettings.inheritedFrom",
                projectLabel(selectedParent)));
    }

    /** Rebuild the versions grid for the current inherited state (live). */
    private void refreshVersions(String slug, List<HLocale> allLocales,
            boolean inherited) {
        versionsContainer.removeAll();
        versionsContainer.add(buildVersionsSection(slug, allLocales, inherited));
    }

    private VerticalLayout buildVersionsSection(String slug, List<HLocale> allLocales,
            boolean inherited) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(true);
        section.setWidthFull();

        versionDrafts = new java.util.ArrayList<>(
                iterationRepository.findForSettingsByProject(slug));
        removedVersionIds = new java.util.HashSet<>();

        // Sortable summary grid; click a row to expand its editor (details). All
        // edits are in-memory; nothing is persisted until the page Save button.
        versionsGrid = new Grid<>();
        versionsGrid.setItems(versionDrafts);
        versionsGrid.setWidthFull();
        versionsGrid.setAllRowsVisible(true);
        versionsGrid.setSelectionMode(Grid.SelectionMode.NONE);

        Grid.Column<HProjectIteration> slugCol = versionsGrid.addColumn(HProjectIteration::getSlug)
                .setHeader(getTranslation("projectSettings.versionSlug"))
                .setSortable(true).setAutoWidth(true).setFlexGrow(1);
        versionsGrid.addColumn(it -> it.getProjectType() == null
                        ? getTranslation("versionSettings.inherited")
                        : it.getProjectType())
                .setHeader(getTranslation("versionSettings.projectType"))
                .setSortable(true).setAutoWidth(true);
        versionsGrid.addColumn(it -> it.getStatus() == null
                        ? EntityStatus.ACTIVE.toString() : it.getStatus().toString())
                .setHeader(getTranslation("versionSettings.status"))
                .setSortable(true).setAutoWidth(true);

        versionsGrid.setItemDetailsRenderer(new com.vaadin.flow.data.renderer.ComponentRenderer<>(
                iter -> buildVersionDetail(iter, allLocales, inherited)));
        versionsGrid.setDetailsVisibleOnClick(true);

        if (inherited) {
            // Inherited from a parent: versions are read-only here, no adding.
            Paragraph note = new Paragraph(getTranslation("projectSettings.versionsInherited"));
            note.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);
            section.add(note);
        } else {
            // "Add version" lives as a clickable row inside the grid (footer); it
            // just appends an unsaved draft row — no page reload.
            Button addRow = new Button(getTranslation("projectSettings.addVersion"),
                    LineAwesomeIcon.PLUS_SOLID.create(), e -> openAddVersionDialog());
            addRow.addThemeVariants(ButtonVariant.TERTIARY);
            versionsGrid.appendFooterRow().getCell(slugCol).setComponent(addRow);
        }

        section.add(versionsGrid);
        return section;
    }

    /**
     * Expandable per-version editor. Field changes mutate the in-memory draft;
     * Remove marks the version for deletion. Persistence happens on the page Save.
     */
    private VerticalLayout buildVersionDetail(HProjectIteration iter,
            List<HLocale> allLocales, boolean inherited) {
        VerticalLayout detail = new VerticalLayout();
        detail.setPadding(true);
        detail.setSpacing(true);
        detail.setWidthFull();

        ComboBox<String> typeBox = new ComboBox<>(getTranslation("versionSettings.projectType"));
        typeBox.setItems(layoutRegistry.knownTypes());
        typeBox.setValue(iter.getProjectType());
        typeBox.setClearButtonVisible(true);
        typeBox.setHelperText(getTranslation("versionSettings.projectTypeHint"));
        typeBox.addValueChangeListener(e -> {
            iter.setProjectType(e.getValue());
            versionsGrid.getDataProvider().refreshItem(iter);
        });

        ComboBox<EntityStatus> statusBox = new ComboBox<>(getTranslation("versionSettings.status"));
        statusBox.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY, EntityStatus.OBSOLETE);
        statusBox.setValue(iter.getStatus() == null ? EntityStatus.ACTIVE : iter.getStatus());
        statusBox.addValueChangeListener(e -> {
            iter.setStatus(e.getValue() == null ? EntityStatus.ACTIVE : e.getValue());
            versionsGrid.getDataProvider().refreshItem(iter);
        });

        Checkbox override = new Checkbox(getTranslation("versionSettings.overrideLocales"));
        override.setValue(iter.isOverrideLocales());
        MultiSelectComboBox<HLocale> locales =
                new MultiSelectComboBox<>(getTranslation("versionSettings.customizedLocales"));
        locales.setItems(allLocales);
        locales.setItemLabelGenerator(this::localeLabel);
        locales.setWidthFull();
        locales.setValue(new LinkedHashSet<>(iter.getCustomizedLocales()));
        locales.setEnabled(override.getValue());
        override.addValueChangeListener(e -> {
            boolean on = Boolean.TRUE.equals(e.getValue());
            iter.setOverrideLocales(on);
            locales.setEnabled(on);
        });
        locales.addValueChangeListener(e ->
                iter.setCustomizedLocales(new LinkedHashSet<>(e.getValue())));

        FormLayout form = new FormLayout(typeBox, statusBox);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        detail.add(form, override, locales);

        if (inherited) {
            // Locked: settings come from the parent project.
            typeBox.setReadOnly(true);
            statusBox.setReadOnly(true);
            override.setReadOnly(true);
            locales.setReadOnly(true);
            return detail;
        }

        Button remove = new Button(getTranslation("versionSettings.delete"),
                LineAwesomeIcon.TRASH_SOLID.create(), e -> {
            if (iter.getId() != null) {
                removedVersionIds.add(iter.getId());
            }
            versionDrafts.remove(iter);
            versionsGrid.getDataProvider().refreshAll();
        });
        remove.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.TERTIARY);
        detail.add(new HorizontalLayout(remove));
        return detail;
    }

    /** Append an unsaved draft version row (persisted on the page Save). */
    private void openAddVersionDialog() {
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
            boolean exists = versionDrafts.stream()
                    .anyMatch(d -> v.equals(d.getSlug()));
            if (exists) {
                versionSlug.setInvalid(true);
                versionSlug.setErrorMessage(getTranslation("projectSettings.versionExists"));
                return;
            }
            HProjectIteration draft = new HProjectIteration();
            draft.setSlug(v);
            draft.setStatus(EntityStatus.ACTIVE);
            versionDrafts.add(draft);
            versionsGrid.getDataProvider().refreshAll();
            dialog.close();
        });
        create.addThemeVariants(ButtonVariant.PRIMARY);
        Button cancel = new Button(getTranslation("common.cancel"), e -> dialog.close());
        dialog.getFooter().add(cancel, create);
        dialog.open();
    }

    /**
     * Persist all in-memory version drafts in one transaction: create new ones
     * (and mirror them to children), update existing ones, and obsolete the
     * removed ones. Called by the page Save button.
     */
    @Transactional
    public void persistVersions(String slug) {
        HProject project = projectRepository.findBySlug(slug).orElseThrow();
        for (HProjectIteration draft : versionDrafts) {
            EntityStatus st = draft.getStatus() == null
                    ? EntityStatus.ACTIVE : draft.getStatus();
            if (draft.getId() == null) {
                HProjectIteration v = new HProjectIteration();
                v.setSlug(draft.getSlug());
                v.setStatus(st);
                v.setProjectType(draft.getProjectType());
                v.setProject(project);
                v.setOverrideLocales(draft.isOverrideLocales());
                if (draft.isOverrideLocales() && draft.getCustomizedLocales() != null) {
                    v.getCustomizedLocales().addAll(draft.getCustomizedLocales());
                }
                iterationRepository.save(v);
                hierarchyService.propagateVersionToChildren(project, draft.getSlug());
            } else {
                iterationRepository.findForSettingsById(draft.getId()).ifPresent(it -> {
                    it.setProjectType(draft.getProjectType());
                    it.setStatus(st);
                    it.setOverrideLocales(draft.isOverrideLocales());
                    it.getCustomizedLocales().clear();
                    if (draft.isOverrideLocales() && draft.getCustomizedLocales() != null) {
                        it.getCustomizedLocales().addAll(draft.getCustomizedLocales());
                    }
                    iterationRepository.save(it);
                });
            }
        }
        for (Long id : removedVersionIds) {
            iterationRepository.findById(id).ifPresent(it -> {
                it.setStatus(EntityStatus.OBSOLETE);
                iterationRepository.save(it);
            });
        }
    }

    private String localeLabel(HLocale l) {
        if (l == null) return "";
        String code = l.getLocaleId() == null ? "?" : l.getLocaleId().getId();
        String display = l.getDisplayName();
        return display == null || display.isBlank() ? code : display + " (" + code + ")";
    }

    private String projectLabel(HProject p) {
        if (p == null) return "";
        String name = p.getName();
        return name == null || name.isBlank() ? p.getSlug()
                : name + " (" + p.getSlug() + ")";
    }
}
