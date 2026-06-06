package org.verbaria.server.headless.service;

import java.util.Calendar;
import java.util.Date;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;
import org.zanata.common.ActivityType;
import org.zanata.model.Activity;
import org.zanata.model.HAccount;
import org.zanata.model.HPerson;
import org.zanata.model.IsEntityWithType;
import org.verbaria.server.headless.event.ContentChangedEvent;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.ActivityRepository;

@Component
public class ActivityLogger {

    private final ActivityRepository activityRepository;
    private final AccountRepository accountRepository;

    public ActivityLogger(ActivityRepository activityRepository,
            AccountRepository accountRepository) {
        this.activityRepository = activityRepository;
        this.accountRepository = accountRepository;
    }

    // AFTER_COMMIT in its own transaction so a logging failure can never roll
    // back the action that produced it; fallbackExecution keeps it working if
    // ever published outside a transaction.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onActivity(ContentChangedEvent event) {
        IsEntityWithType context = event.context();
        IsEntityWithType target = event.target();
        ActivityType type = event.activityType();
        if (event.actorUsername() == null || context == null || target == null
                || type == null) {
            return;
        }
        HPerson actor = accountRepository.findByUsername(event.actorUsername())
                .map(HAccount::getPerson).orElse(null);
        if (actor == null) {
            return;
        }
        Date hour = DateUtils.truncate(new Date(), Calendar.HOUR);
        Activity existing = activityRepository.findInHour(actor.getId(), type,
                context.getEntityType(), context.getId(), hour).orElse(null);
        if (existing != null) {
            existing.updateActivity(new Date(), target, event.wordCount());
            activityRepository.save(existing);
        } else {
            activityRepository.save(new Activity(actor, context, target, type,
                    event.wordCount()));
        }
    }
}
