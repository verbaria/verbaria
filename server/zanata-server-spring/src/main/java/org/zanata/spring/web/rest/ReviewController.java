package org.zanata.spring.web.rest;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.IssuePriority;
import org.zanata.model.ReviewCriteria;
import org.zanata.spring.repository.ReviewCriteriaRepository;

/**
 * Backs the React /admin/review screen.  Persists review criteria to the
 * ReviewCriteria JPA entity — replaces the legacy ReviewService whose
 * paths and DTO this controller mirrors so the React client keeps working.
 */
@RestController
@RequestMapping("/rest/review")
public class ReviewController {

    private final ReviewCriteriaRepository repo;

    public ReviewController(ReviewCriteriaRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TransReviewCriteria> list() {
        return repo.findAll().stream().map(TransReviewCriteria::fromModel).toList();
    }

    @PostMapping("/criteria")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<TransReviewCriteria> create(@RequestBody TransReviewCriteria incoming) {
        if (isBlank(incoming.description())
                || incoming.description().length() > ReviewCriteria.DESCRIPTION_MAX_LENGTH) {
            return ResponseEntity.badRequest().build();
        }
        ReviewCriteria saved = repo.save(new ReviewCriteria(
                incoming.priority(),
                Boolean.TRUE.equals(incoming.commentRequired()),
                incoming.description()));
        return ResponseEntity.ok(TransReviewCriteria.fromModel(saved));
    }

    @PutMapping("/criteria/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<TransReviewCriteria> update(@PathVariable("id") Long id,
                                                      @RequestBody TransReviewCriteria incoming) {
        if (isBlank(incoming.description())
                || incoming.description().length() > ReviewCriteria.DESCRIPTION_MAX_LENGTH) {
            return ResponseEntity.badRequest().build();
        }
        return repo.findById(id).map(row -> {
            row.setDescription(incoming.description());
            row.setPriority(incoming.priority());
            row.setCommentRequired(Boolean.TRUE.equals(incoming.commentRequired()));
            return ResponseEntity.ok(TransReviewCriteria.fromModel(repo.save(row)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/criteria/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<TransReviewCriteria> delete(@PathVariable("id") Long id) {
        return repo.findById(id).map(row -> {
            TransReviewCriteria dto = TransReviewCriteria.fromModel(row);
            repo.delete(row);
            return ResponseEntity.ok(dto);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record TransReviewCriteria(Long id, IssuePriority priority,
                                      String description, Boolean commentRequired) {
        static TransReviewCriteria fromModel(ReviewCriteria c) {
            return new TransReviewCriteria(c.getId(), c.getPriority(),
                    c.getDescription(), c.isCommentRequired());
        }
    }
}
