package org.verbaria.server.ui.vaadin.admin;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.model.HPerson;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.RoleRepository;

/**
 * Read-only query support for the admin User Manager grid.
 *
 * <p>The grid renders each account's roles in a later Vaadin UIDL flush, by
 * which point the {@link HAccount} entities have detached and the lazy
 * {@code roles} collection would throw {@code LazyInitializationException}. This
 * service loads a page (DB-side pagination, no collection fetch) and then
 * initialises {@code roles} for just that page within the same read-only
 * transaction, so the collection is ready once the entities detach.</p>
 */
@Service
public class AdminUserQueryService {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;

    public AdminUserQueryService(AccountRepository accountRepository,
            RoleRepository roleRepository) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<HAccount> findPage(String filter, int page, int size) {
        PageRequest pageRequest =
                PageRequest.of(page, size, Sort.by("username"));
        String text = filter == null ? "" : filter.trim();
        Page<HAccount> result = text.isEmpty()
                ? accountRepository.findAll(pageRequest)
                : accountRepository.findByUsernameContaining(text, pageRequest);
        List<HAccount> content = result.getContent();
        if (!content.isEmpty()) {
            // one extra query initialises roles on these managed entities
            accountRepository.fetchRolesFor(content);
        }
        return content;
    }

    @Transactional(readOnly = true)
    public long count(String filter) {
        String text = filter == null ? "" : filter.trim();
        return text.isEmpty()
                ? accountRepository.count()
                : accountRepository.countByUsernameContaining(text);
    }

    /** All defined role names, sorted, for the role editor. */
    @Transactional(readOnly = true)
    public List<String> allRoleNames() {
        return roleRepository.findAll().stream()
                .map(HAccountRole::getName)
                .sorted()
                .toList();
    }

    /**
     * Loads an account with its {@code roles} and {@code person} initialised
     * inside the transaction, so the detail view can read them after the entity
     * detaches.
     */
    @Transactional(readOnly = true)
    public Optional<HAccount> loadWithRoles(String username) {
        Optional<HAccount> found =
                accountRepository.findByUsernameWithRoles(username);
        found.ifPresent(account -> {
            // touch the lazy person association so it is loaded in-session
            HPerson person = account.getPerson();
            if (person != null) {
                person.getName();
                person.getEmail();
            }
        });
        return found;
    }

    /**
     * Replaces {@code username}'s roles with the given role names. Unknown role
     * names are ignored. Returns {@code false} if the account does not exist.
     */
    @Transactional
    public boolean updateRoles(String username, Collection<String> roleNames) {
        Optional<HAccount> found = accountRepository.findByUsername(username);
        if (found.isEmpty()) {
            return false;
        }
        HAccount account = found.get();
        Set<HAccountRole> roles = new LinkedHashSet<>();
        for (String name : roleNames) {
            roleRepository.findByName(name).ifPresent(roles::add);
        }
        account.setRoles(roles);
        accountRepository.save(account);
        return true;
    }
}
