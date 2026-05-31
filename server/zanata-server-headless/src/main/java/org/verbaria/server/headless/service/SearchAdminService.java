package org.verbaria.server.headless.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.search.mapper.orm.Search;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchAdminService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void reindexAll() throws InterruptedException {
        Search.session(em).massIndexer().startAndWait();
    }
}
