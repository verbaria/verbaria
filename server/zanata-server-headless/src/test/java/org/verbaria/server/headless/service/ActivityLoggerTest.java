package org.verbaria.server.headless.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.Optional;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.zanata.common.ActivityType;
import org.zanata.model.Activity;
import org.zanata.model.HAccount;
import org.zanata.model.HPerson;
import org.zanata.model.IsEntityWithType;
import org.zanata.model.type.EntityType;
import org.verbaria.server.headless.event.ContentChangedEvent;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.ActivityRepository;

class ActivityLoggerTest {

    private ActivityRepository repository;
    private AccountRepository accountRepository;
    private ActivityLogger logger;
    private HPerson actor;
    private IsEntityWithType context;
    private IsEntityWithType target;

    @BeforeEach
    void setUp() {
        repository = mock(ActivityRepository.class);
        accountRepository = mock(AccountRepository.class);
        logger = new ActivityLogger(repository, accountRepository);

        actor = mock(HPerson.class);
        when(actor.getId()).thenReturn(7L);
        HAccount account = mock(HAccount.class);
        when(account.getPerson()).thenReturn(actor);
        when(accountRepository.findByUsername("alice"))
                .thenReturn(Optional.of(account));

        context = mock(IsEntityWithType.class);
        when(context.getEntityType()).thenReturn(EntityType.HProjectIteration);
        when(context.getId()).thenReturn(42L);
        target = mock(IsEntityWithType.class);
        when(target.getEntityType()).thenReturn(EntityType.HTexFlowTarget);
        when(target.getId()).thenReturn(99L);
    }

    @Test
    void firstActivityInsertsNewRow() {
        when(repository.findInHour(any(), any(), any(), anyLong(), any()))
                .thenReturn(Optional.empty());

        logger.onActivity(new ContentChangedEvent("proj", "alice", context, target,
                ActivityType.UPDATE_TRANSLATION, 5));

        ArgumentCaptor<Activity> saved = ArgumentCaptor.forClass(Activity.class);
        verify(repository).save(saved.capture());
        Activity a = saved.getValue();
        assertThat(a.getActor()).isSameAs(actor);
        assertThat(a.getActivityType()).isEqualTo(ActivityType.UPDATE_TRANSLATION);
        assertThat(a.getContextType()).isEqualTo(EntityType.HProjectIteration);
        assertThat(a.getContextId()).isEqualTo(42L);
        assertThat(a.getLastTargetType()).isEqualTo(EntityType.HTexFlowTarget);
        assertThat(a.getLastTargetId()).isEqualTo(99L);
        assertThat(a.getWordCount()).isEqualTo(5);
        assertThat(a.getEventCount()).isEqualTo(1);
    }

    @Test
    void sameHourAggregatesIntoExistingRow() throws Exception {
        Activity existing = new Activity(actor, context, target,
                ActivityType.UPDATE_TRANSLATION, 5);
        // approxTime is normally stamped by the @PrePersist listener; set it so
        // updateActivity can compute its offset against a persisted row.
        FieldUtils.writeField(existing, "approxTime", new Date(), true);
        when(repository.findInHour(any(), any(), any(), anyLong(), any()))
                .thenReturn(Optional.of(existing));

        IsEntityWithType newTarget = mock(IsEntityWithType.class);
        when(newTarget.getEntityType()).thenReturn(EntityType.HTexFlowTarget);
        when(newTarget.getId()).thenReturn(123L);

        logger.onActivity(new ContentChangedEvent("proj", "alice", context, newTarget,
                ActivityType.UPDATE_TRANSLATION, 3));

        verify(repository).save(existing);
        assertThat(existing.getEventCount()).isEqualTo(2);
        assertThat(existing.getWordCount()).isEqualTo(8);
        assertThat(existing.getLastTargetId()).isEqualTo(123L);
    }

    @Test
    void nullActorUsernameIsANoOp() {
        logger.onActivity(new ContentChangedEvent("proj", null, context, target,
                ActivityType.UPDATE_TRANSLATION, 5));
        verifyNoInteractions(repository);
        verifyNoInteractions(accountRepository);
    }

    @Test
    void unknownActorDoesNotPersist() {
        when(accountRepository.findByUsername("ghost"))
                .thenReturn(Optional.empty());
        logger.onActivity(new ContentChangedEvent("proj", "ghost", context, target,
                ActivityType.UPDATE_TRANSLATION, 5));
        verify(repository, never()).save(any());
    }

    @Test
    void nullTargetDoesNotPersist() {
        logger.onActivity(new ContentChangedEvent("proj", "alice", context, null,
                ActivityType.UPLOAD_SOURCE_DOCUMENT, 0));
        verify(repository, never()).save(any());
    }
}
