package org.verbaria.server.headless.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountActivationKey;

/**
 * Activation keys emailed during sign-up. The key string is the entity id
 * ({@code keyHash}), so {@code findById}/{@code deleteById} look up by the value
 * carried in the activation link.
 */
@Repository
public interface ActivationKeyRepository
        extends JpaRepository<HAccountActivationKey, String> {

    /** Remove any existing keys for an account (e.g. on re-registration). */
    void deleteByAccount(HAccount account);
}
