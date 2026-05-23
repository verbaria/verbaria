package org.zanata.dao;

import org.hibernate.Session;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import org.zanata.model.HApplicationConfiguration;

@Named("applicationConfigurationDAO")

@RequestScoped
public class ApplicationConfigurationDAO extends
        AbstractDAOImpl<HApplicationConfiguration, Long> {

    private static final long serialVersionUID = 6357076088125640442L;

    public ApplicationConfigurationDAO() {
        super(HApplicationConfiguration.class);
    }

    public ApplicationConfigurationDAO(Session session) {
        super(HApplicationConfiguration.class, session);
    }

    public HApplicationConfiguration findByKey(String key) {
        return (HApplicationConfiguration) getSession()
                .byNaturalId(HApplicationConfiguration.class).using("key", key)
                .load();
    }

}
