package org.verbaria.server.headless.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LocaleMemberRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.security.Roles;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;

@Service
public class ReviewPermissionService {

    private final AccountRepository accountRepository;
    private final LocaleRepository localeRepository;
    private final LocaleMemberRepository localeMemberRepository;

    public ReviewPermissionService(AccountRepository accountRepository,
            LocaleRepository localeRepository,
            LocaleMemberRepository localeMemberRepository) {
        this.accountRepository = accountRepository;
        this.localeRepository = localeRepository;
        this.localeMemberRepository = localeMemberRepository;
    }

    @Transactional(readOnly = true)
    public boolean canReview(String username, LocaleId localeId) {
        if (username == null) {
            return false;
        }
        HAccount account = accountRepository.findByUsername(username).orElse(null);
        if (account == null) {
            return false;
        }
        boolean admin = account.getRoles() != null
                && account.getRoles().stream()
                        .anyMatch(r -> Roles.ADMIN.equals(r.getName()));
        if (admin) {
            return true;
        }
        HPerson person = account.getPerson();
        HLocale locale = localeRepository.findByLocaleId(localeId).orElse(null);
        if (person == null || locale == null) {
            return false;
        }
        return localeMemberRepository.findByLocaleAndPerson(locale, person)
                .map(m -> m.isReviewer() || m.isCoordinator())
                .orElse(false);
    }
}
