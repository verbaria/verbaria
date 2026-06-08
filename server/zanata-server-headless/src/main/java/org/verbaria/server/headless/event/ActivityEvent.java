package org.verbaria.server.headless.event;

import org.zanata.common.ActivityType;
import org.zanata.model.HPerson;
import org.zanata.model.IsEntityWithType;

public record ActivityEvent(HPerson actor, IsEntityWithType context,
        IsEntityWithType target, ActivityType activityType, int wordCount) {
}
