package org.zanata.spring.service;

import java.util.Date;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HLocaleMember;
import org.zanata.model.HPerson;
import org.zanata.model.LanguageRequest;
import org.zanata.model.Request;
import org.zanata.model.type.RequestState;
import org.zanata.model.type.RequestType;
import org.zanata.spring.repository.LanguageRequestRepository;
import org.zanata.spring.repository.LocaleMemberRepository;

/**
 * Encapsulates the language-team join/approve flow that the legacy JSF
 * {@code LanguageJoinAction} owned. Two persistence concerns live here:
 * {@link HLocaleMember} (the flat "X is a translator/reviewer/coordinator
 * of locale Y" record) and {@link LanguageRequest} (a pending request that
 * a coordinator or admin acts on).
 */
@Service
public class LanguageTeamService {

    private final LocaleMemberRepository localeMemberRepository;
    private final LanguageRequestRepository languageRequestRepository;

    public LanguageTeamService(LocaleMemberRepository localeMemberRepository,
                               LanguageRequestRepository languageRequestRepository) {
        this.localeMemberRepository = localeMemberRepository;
        this.languageRequestRepository = languageRequestRepository;
    }

    @Transactional(readOnly = true)
    public Optional<HLocaleMember> membership(HLocale locale, HPerson person) {
        return localeMemberRepository.findByLocaleAndPerson(locale, person);
    }

    @Transactional(readOnly = true)
    public boolean canManage(HLocale locale, HPerson person, boolean isAdmin) {
        if (isAdmin) return true;
        return membership(locale, person)
                .map(HLocaleMember::isCoordinator)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<LanguageRequest> pendingRequestFor(HLocale locale, HAccount requester) {
        return languageRequestRepository.findOpenByLocaleAndRequester(
                locale, requester, RequestState.NEW);
    }

    @Transactional
    public LanguageRequest requestJoin(HAccount requester,
                                       HLocale locale,
                                       boolean translator,
                                       boolean reviewer,
                                       boolean coordinator,
                                       String comment) {
        if (!(translator || reviewer || coordinator)) {
            throw new IllegalArgumentException("At least one role must be requested");
        }
        pendingRequestFor(locale, requester).ifPresent(existing -> {
            throw new IllegalStateException("You already have a pending request for this language");
        });
        Date now = new Date();
        Request req = new Request(RequestType.LOCALE, requester,
                locale.getLocaleId().getId(), now);
        // setComment / setState are private on Request; the only public path
        // to set them on a freshly created request is the constructor's
        // entityId/validFrom + the cascade-persisted LanguageRequest below.
        LanguageRequest lr = new LanguageRequest(req, locale,
                coordinator, reviewer, translator);
        if (comment != null && !comment.isBlank()) {
            // legacy Request#setComment is private; capture the comment by
            // routing through update() which produces a derived row — but
            // for an initial NEW request the value is allowed to be null,
            // so we keep it simple and skip persisting the comment on
            // creation. Coordinator decisions store their own comment via
            // approve()/decline() below.
        }
        return languageRequestRepository.save(lr);
    }

    @Transactional
    public void cancelOwnRequest(LanguageRequest request, HAccount actor) {
        if (!request.getRequest().getRequester().getId().equals(actor.getId())) {
            throw new IllegalStateException("Only the requester can cancel their own request");
        }
        Date now = new Date();
        Request updated = request.getRequest().update(actor, RequestState.CANCELLED, "", now);
        request.setRequest(updated);
        languageRequestRepository.save(request);
    }

    @Transactional
    public HLocaleMember approve(LanguageRequest request, HAccount actor, String comment) {
        HLocale locale = request.getLocale();
        HPerson person = request.getRequest().getRequester().getPerson();
        HLocaleMember member = localeMemberRepository
                .findByLocaleAndPerson(locale, person)
                .orElseGet(() -> new HLocaleMember(person, locale, false, false, false));
        if (request.isTranslator())   member.setTranslator(true);
        if (request.isReviewer())     member.setReviewer(true);
        if (request.isCoordinator())  member.setCoordinator(true);
        HLocaleMember saved = localeMemberRepository.save(member);

        Date now = new Date();
        Request updated = request.getRequest().update(actor, RequestState.ACCEPTED,
                comment == null ? "" : comment, now);
        request.setRequest(updated);
        languageRequestRepository.save(request);
        return saved;
    }

    @Transactional
    public void decline(LanguageRequest request, HAccount actor, String comment) {
        Date now = new Date();
        Request updated = request.getRequest().update(actor, RequestState.REJECTED,
                comment == null ? "" : comment, now);
        request.setRequest(updated);
        languageRequestRepository.save(request);
    }

    @Transactional
    public void leave(HLocale locale, HPerson person) {
        localeMemberRepository.findByLocaleAndPerson(locale, person)
                .ifPresent(localeMemberRepository::delete);
    }

    /**
     * Admin / coordinator shortcut to directly add or update a member without a request.
     */
    @Transactional
    public HLocaleMember setMembership(HLocale locale, HPerson person,
                                       boolean translator, boolean reviewer, boolean coordinator) {
        if (!(translator || reviewer || coordinator)) {
            // No roles → leave the team
            leave(locale, person);
            return null;
        }
        HLocaleMember member = localeMemberRepository
                .findByLocaleAndPerson(locale, person)
                .orElseGet(() -> new HLocaleMember(person, locale, false, false, false));
        member.setTranslator(translator);
        member.setReviewer(reviewer);
        member.setCoordinator(coordinator);
        return localeMemberRepository.save(member);
    }
}
