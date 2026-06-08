package org.verbaria.server.headless.extension.consulo;

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

@Component
@Order(50)
public class ConsuloLegacyBackfill implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(ConsuloLegacyBackfill.class);

    private final TextFlowExtensionStore store;

    @PersistenceContext
    private EntityManager em;

    public ConsuloLegacyBackfill(TextFlowExtensionStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Number present = (Number) em.createNativeQuery(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE LOWER(table_name) = 'htext_flow' "
                        + "AND LOWER(column_name) = 'consulo_file_ext'")
                .getSingleResult();
        if (present.intValue() == 0) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT id, consulo_file_ext FROM htext_flow "
                        + "WHERE consulo_file_ext IS NOT NULL").getResultList();
        if (rows.isEmpty()) {
            return;
        }

        int migrated = 0;
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            String ext = (String) row[1];
            String json = store.toJson(new ConsuloSubFile(ext));
            em.createNativeQuery(
                    "INSERT INTO htext_flow_extension (text_flow_id, ext_type, json) "
                            + "VALUES (:tf, :t, :j) "
                            + "ON CONFLICT (text_flow_id, ext_type) DO NOTHING")
                    .setParameter("tf", id)
                    .setParameter("t", ConsuloSubFile.ID)
                    .setParameter("j", json)
                    .executeUpdate();
            migrated++;
        }
        em.createNativeQuery(
                "UPDATE htext_flow SET consulo_file_ext = NULL "
                        + "WHERE consulo_file_ext IS NOT NULL").executeUpdate();
        log.info("Migrated {} consulo extension(s) from the legacy "
                + "consulo_file_ext column", migrated);
    }
}
