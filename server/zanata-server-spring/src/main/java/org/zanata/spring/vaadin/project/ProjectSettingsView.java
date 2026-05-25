package org.zanata.spring.vaadin.project;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.zanata.common.EntityStatus;
import org.zanata.common.ProjectType;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.vaadin.BreadcrumbsService;
import org.zanata.spring.vaadin.HasBreadcrumbs;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "project/view/:slug/settings", layout = MainLayout.class)
@PermitAll
public class ProjectSettingsView extends VerticalLayout implements BeforeEnterObserver, TitleKey, HasBreadcrumbs {

    @Override public String pageTitleKey() { return "page.projectSettings"; }


    private final ProjectRepository projectRepository;
    private final LocaleRepository localeRepository;
    private final BreadcrumbsService breadcrumbsService;

    public ProjectSettingsView(ProjectRepository projectRepository,
                               LocaleRepository localeRepository,
                               BreadcrumbsService breadcrumbsService) {
        this.projectRepository = projectRepository;
        this.localeRepository = localeRepository;
        this.breadcrumbsService = breadcrumbsService;
        setSizeFull();
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
        TextField sourceView = new TextField(getTranslation("projectSettings.sourceViewUrl"));
        sourceView.setValue(project.getSourceViewURL() == null ? "" : project.getSourceViewURL());
        TextField sourceCheckout = new TextField(getTranslation("projectSettings.sourceCheckoutUrl"));
        sourceCheckout.setValue(project.getSourceCheckoutURL() == null ? "" : project.getSourceCheckoutURL());

        ComboBox<HLocale> sourceLocale = new ComboBox<>(getTranslation("projectCreate.sourceLocale"));
        List<HLocale> allLocales = localeRepository.findAll();
        sourceLocale.setItems(allLocales);
        sourceLocale.setItemLabelGenerator(this::localeLabel);
        if (project.getDefaultSourceLocale() != null) {
            sourceLocale.setValue(project.getDefaultSourceLocale());
        }

        FormLayout form = new FormLayout(name, description, defaultType, status,
                sourceLocale, sourceView, sourceCheckout);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        add(form);

        add(new H3(getTranslation("projectSettings.targetLanguages")));
        Paragraph hint = new Paragraph(getTranslation("projectSettings.targetLanguagesHint"));
        hint.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        hint.getStyle().set("margin", "0 0 0.5rem 0");
        add(hint);

        MultiSelectComboBox<HLocale> targetLocales =
                new MultiSelectComboBox<>(getTranslation("projectSettings.targetLocales"));
        targetLocales.setItems(allLocales);
        targetLocales.setItemLabelGenerator(this::localeLabel);
        targetLocales.setWidthFull();
        targetLocales.setAllowCustomValue(false);
        if (project.getCustomizedLocales() != null) {
            targetLocales.setValue(project.getCustomizedLocales());
        }
        add(targetLocales);

        // --- Validations card ---
        add(new H3(getTranslation("projectSettings.validations")));
        Paragraph valHint = new Paragraph(getTranslation("projectSettings.validationsHint"));
        valHint.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        valHint.getStyle().set("margin", "0 0 0.5rem 0");
        add(valHint);
        FormLayout valForm = new FormLayout();
        valForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        Map<String, String> validationDefs = validationCatalog();
        Map<String, ComboBox<String>> validationPickers = new LinkedHashMap<>();
        Map<String, String> stored = project.getCustomizedValidations() == null
                ? Map.of() : project.getCustomizedValidations();
        for (Map.Entry<String, String> entry : validationDefs.entrySet()) {
            ComboBox<String> picker = new ComboBox<>(entry.getValue());
            picker.setItems("OFF", "WARN", "ERROR");
            picker.setClearButtonVisible(true);
            picker.setHelperText(entry.getKey());
            String current = stored.get(entry.getKey());
            if (current != null && !current.isBlank()) picker.setValue(current);
            valForm.add(picker);
            validationPickers.put(entry.getKey(), picker);
        }
        add(valForm);

        Button save = new Button(getTranslation("common.save"), e -> {
            project.setName(name.getValue());
            project.setDescription(description.getValue());
            project.setDefaultProjectType(defaultType.getValue());
            project.setStatus(status.getValue());
            project.setSourceViewURL(sourceView.getValue());
            project.setSourceCheckoutURL(sourceCheckout.getValue());
            project.setDefaultSourceLocale(sourceLocale.getValue());
            Set<HLocale> picked = new LinkedHashSet<>(targetLocales.getValue());
            project.setCustomizedLocales(picked);
            project.setOverrideLocales(!picked.isEmpty());
            // Persist per-validation state — strip empties so we inherit defaults.
            Map<String, String> validations = project.getCustomizedValidations() == null
                    ? new LinkedHashMap<>() : project.getCustomizedValidations();
            validations.clear();
            validationPickers.forEach((key, picker) -> {
                String v = picker.getValue();
                if (v != null && !v.isBlank()) validations.put(key, v);
            });
            project.setCustomizedValidations(validations);
            projectRepository.save(project);
            Notification.show(getTranslation("common.saved"), 2000, Notification.Position.BOTTOM_START);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        Button back = new Button(getTranslation("projectSettings.back"),
                e -> getUI().ifPresent(ui -> ui.navigate("project/view/" + slug)));
        add(new HorizontalLayout(save, back));
    }

    private String localeLabel(HLocale l) {
        if (l == null) return "";
        String code = l.getLocaleId() == null ? "?" : l.getLocaleId().getId();
        String display = l.getDisplayName();
        return display == null || display.isBlank() ? code : display + " (" + code + ")";
    }

    /**
     * Catalog of validations the UI exposes — keys match the storage keys used
     * by {@code HProject.customizedValidations}, values are human-readable
     * labels from the legacy validation framework / docs.
     */
    private Map<String, String> validationCatalog() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("HTML_XML", getTranslation("projectSettings.validation.htmlXml"));
        m.put("JAVA_VARIABLES", getTranslation("projectSettings.validation.javaVariables"));
        m.put("NEW_LINE", getTranslation("projectSettings.validation.newLine"));
        m.put("PRINTF_VARIABLES", getTranslation("projectSettings.validation.printfVariables"));
        m.put("PRINTF_XSI_EXTENSION", getTranslation("projectSettings.validation.printfXsi"));
        m.put("TAB", getTranslation("projectSettings.validation.tab"));
        m.put("XML_ENTITY", getTranslation("projectSettings.validation.xmlEntity"));
        return m;
    }
}
