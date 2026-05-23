/*
 * Copyright 2017, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 */
package org.zanata.dao;

import org.hibernate.Session;
import org.zanata.model.ReviewCriteria;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class ReviewCriteriaDAO extends AbstractDAOImpl<ReviewCriteria, Long> {

    public ReviewCriteriaDAO() {
        super(ReviewCriteria.class);
    }

    public ReviewCriteriaDAO(Session session) {
        super(ReviewCriteria.class, session);
    }

    public void remove(ReviewCriteria reviewCriteria) {
        getSession().delete(reviewCriteria);
    }
}
