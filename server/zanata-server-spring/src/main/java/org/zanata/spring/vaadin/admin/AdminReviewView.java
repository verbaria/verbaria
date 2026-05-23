package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.common.IssuePriority;
import org.zanata.model.ReviewCriteria;
import org.zanata.spring.repository.ReviewCriteriaRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/review", layout = MainLayout.class)
@PageTitle("Review Criteria | Zanata")
@RolesAllowed("ADMIN")
public class AdminReviewView extends VerticalLayout {

    public AdminReviewView(ReviewCriteriaRepository repo) {
        setSizeFull();
        setPadding(true);
        add(new H2("Review Criteria"));

        Grid<ReviewCriteria> grid = new Grid<>(ReviewCriteria.class, false);
        grid.addColumn(ReviewCriteria::getDescription).setHeader("Description");
        grid.addColumn(rc -> rc.getPriority() == null ? "" : rc.getPriority().name())
                .setHeader("Priority").setAutoWidth(true);
        grid.addColumn(ReviewCriteria::isCommentRequired)
                .setHeader("Comment required").setAutoWidth(true);
        grid.setItems(repo.findAll());

        add(grid);

        add(new H3("Add criterion"));
        TextField description = new TextField("Description");
        Select<IssuePriority> priority = new Select<>();
        priority.setLabel("Priority");
        priority.setItems(IssuePriority.values());
        priority.setValue(IssuePriority.Major);
        Checkbox commentRequired = new Checkbox("Comment required");

        Button add = new Button("Add", e -> {
            ReviewCriteria rc = new ReviewCriteria(
                    priority.getValue(),
                    commentRequired.getValue() != null && commentRequired.getValue(),
                    description.getValue());
            repo.save(rc);
            grid.setItems(repo.findAll());
            description.clear();
            commentRequired.clear();
        });

        add(new HorizontalLayout(description, priority, commentRequired, add));
    }
}
