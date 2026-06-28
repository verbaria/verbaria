package org.verbaria.server.headless.service;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final TransactionTemplate txTemplate;

    private final ReentrantLock lock = new ReentrantLock();

    public ActivityLogger(ActivityRepository activityRepository,
            AccountRepository accountRepository,
            PlatformTransactionManager txManager) {
        this.activityRepository = activityRepository;
        this.accountRepository = accountRepository;
        this.txTemplate = new TransactionTemplate(txManager);
        this.txTemplate.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onActivity(ContentChangedEvent event) {
        if (event.actorUsername() == null || event.context() == null
                || event.target() == null || event.activityType() == null) {
            return;
        }
        lock.lock();
        try {
            txTemplate.executeWithoutResult(tx -> upsert(event));
        } finally {
            lock.unlock();
        }
    }

    private void upsert(ContentChangedEvent event) {
        IsEntityWithType context = event.context();
        IsEntityWithType target = event.target();
        ActivityType type = event.activityType();
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
