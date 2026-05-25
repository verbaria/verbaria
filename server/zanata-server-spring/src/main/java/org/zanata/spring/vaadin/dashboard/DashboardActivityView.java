package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.zanata.model.Activity;
import org.zanata.model.HDocument;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.type.EntityType;
import org.zanata.spring.repository.ActivityRepository;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.vaadin.MainLayout;

/**
 * Dashboard activity feed. Each row gets resolved into clickable links to the
 * project, version, and document touched by the activity, so users can jump
 * straight back into context from the timeline.
 */
@Route(value = "dashboard/activity", layout = MainLayout.class)
@PageTitle("Activity | Zanata")
@AnonymousAllowed
public class DashboardActivityView extends VerticalLayout {

    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;

    public DashboardActivityView(ActivityRepository activityRepository,
                                 ProjectIterationRepository iterationRepository,
                                 DocumentRepository documentRepository) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        setSizeFull();
        setPadding(true);

        H2 heading = new H2("Activity");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken)
                && auth.getName() != null;
        List<Activity> activities = signedIn
                ? activityRepository.findByActor(auth.getName(), PageRequest.of(0, 50))
                : Collections.emptyList();

        if (!signedIn) {
            Paragraph signIn = new Paragraph("Sign in to see your activity.");
            signIn.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            add(heading, signIn);
            return;
        }
        if (activities.isEmpty()) {
            Paragraph empty = new Paragraph(
                    "No activity yet — translate or review something to see it here.");
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            add(heading, empty);
            return;
        }

        Grid<Activity> grid = new Grid<>(Activity.class, false);
        grid.addColumn(a -> a.getActivityType() == null ? "" : friendly(a.getActivityType().name()))
                .setHeader("Activity").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::contextLinks)
                .setHeader("Where").setFlexGrow(1);
        grid.addColumn(a -> a.getWordCount() <= 0 ? "" : a.getWordCount() + " w")
                .setHeader("Words").setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(a -> a.getApproxTime() == null ? "" : a.getApproxTime().toString())
                .setHeader("When").setAutoWidth(true).setFlexGrow(0);
        grid.setItems(activities);
        grid.setAllRowsVisible(true);

        add(heading, grid);
    }

    private static String friendly(String name) {
        // UPDATE_TRANSLATION → "Update translation"
        if (name == null || name.isBlank()) return "";
        String spaced = name.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    /**
     * Render the context + target as one or two breadcrumb-style links, e.g.
     * "{project-link} › {version-link} › {doc-link}". Falls back to plain
     * text when an id can't be resolved.
     */
    private Div contextLinks(Activity a) {
        Div d = new Div();
        d.getStyle().set("display", "inline-flex");
        d.getStyle().set("gap", "0.4rem");
        d.getStyle().set("align-items", "center");
        d.getStyle().set("flex-wrap", "wrap");

        // Context first — only HProjectIteration is enumerated in this model
        // (no HProject value in EntityType). For other ctx types fall through
        // to the target resolution below.
        EntityType ct = a.getContextType();
        if (ct == EntityType.HProjectIteration) {
            Optional<HProjectIteration> opt = iterationRepository.findById(a.getContextId());
            opt.ifPresent(i -> {
                if (i.getProject() != null) {
                    d.add(projectLink(i.getProject()));
                    d.add(sep());
                }
                d.add(versionLink(i));
            });
        }

        // Target (often a doc) — append after the context if it adds info.
        EntityType tt = a.getLastTargetType();
        if (tt == EntityType.HDocument) {
            documentRepository.findById(a.getLastTargetId()).ifPresent(doc -> {
                if (d.getComponentCount() > 0) d.add(sep());
                d.add(docLink(doc));
            });
        }

        if (d.getComponentCount() == 0) {
            Span fallback = new Span(ct == null ? "" : ct.name());
            fallback.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            d.add(fallback);
        }
        return d;
    }

    private static Anchor projectLink(HProject p) {
        Anchor a = new Anchor("/project/view/" + p.getSlug(),
                p.getName() == null ? p.getSlug() : p.getName());
        a.getStyle().set("color", "var(--aura-blue-text, var(--lumo-primary-text-color))");
        a.getStyle().set("font-weight", "600");
        return a;
    }

    private static Anchor versionLink(HProjectIteration i) {
        String slug = i.getProject() == null ? "" : i.getProject().getSlug();
        Anchor a = new Anchor("/project/" + slug + "/version/" + i.getSlug(),
                i.getSlug());
        a.getStyle().set("color", "var(--aura-blue-text, var(--lumo-primary-text-color))");
        return a;
    }

    private static Anchor docLink(HDocument doc) {
        if (doc.getProjectIteration() == null
                || doc.getProjectIteration().getProject() == null) {
            return new Anchor("#", doc.getDocId());
        }
        String slug = doc.getProjectIteration().getProject().getSlug();
        String version = doc.getProjectIteration().getSlug();
        Anchor a = new Anchor("/project/" + slug + "/version/" + version,
                doc.getDocId());
        a.getStyle().set("color", "var(--vaadin-text-color)");
        return a;
    }

    private static Span sep() {
        Span s = new Span("›");
        s.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        return s;
    }
}
