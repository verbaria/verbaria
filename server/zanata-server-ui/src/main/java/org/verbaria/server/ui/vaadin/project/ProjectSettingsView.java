package org.verbaria.server.ui.vaadin.project;

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
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.ui.vaadin.BreadcrumbsService;
import org.verbaria.server.ui.vaadin.HasBreadcrumbs;
import org.verbaria.server.ui.vaadin.MainLayout;

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
        setWidthFull();
        setHeightFull();
        setPadding(true);
        setSpacing(true);
        // The settings form can grow taller than the viewport — let it scroll
        // instead of clipping the lower fields.
        addClassNames(AuraUtility.Overflow.Y.AUTO);
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
        List<HLocale> allLocales = localeRepository.findAll();
        sourceLocale.setItems(allLocales);
        sourceLocale.setItemLabelGenerator(this::localeLabel);
        if (project.getDefaultSourceLocale() != null) {
            sourceLocale.setValue(project.getDefaultSourceLocale());
        }

        FormLayout form = new FormLayout(name, description, defaultType, status,
                messageFormat, sourceLocale, sourceView, sourceCheckout);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        add(form);

        add(new H3(getTranslation("projectSettings.targetLanguages")));
        Paragraph hint = new Paragraph(getTranslation("projectSettings.targetLanguagesHint"));
        hint.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.Bottom.SMALL);
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
        add(new HorizontalLayout(save, back));
    }

    private String localeLabel(HLocale l) {
        if (l == null) return "";
        String code = l.getLocaleId() == null ? "?" : l.getLocaleId().getId();
        String display = l.getDisplayName();
        return display == null || display.isBlank() ? code : display + " (" + code + ")";
    }
}
