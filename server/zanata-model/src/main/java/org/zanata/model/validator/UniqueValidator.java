/* Stub. Original implementation used Hibernate 5 DetachedCriteria/Restrictions.
 * Re-port to JPA Criteria to restore real validation. */
package org.zanata.model.validator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
public class UniqueValidator implements ConstraintValidator<Unique, Object> {
    @Override public void initialize(Unique a) {}
    @Override public boolean isValid(Object v, ConstraintValidatorContext ctx) { return true; }
}
