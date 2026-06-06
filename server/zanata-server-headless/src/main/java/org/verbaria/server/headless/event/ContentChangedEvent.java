package org.verbaria.server.headless.event;

import org.zanata.common.ActivityType;
import org.zanata.model.IsEntityWithType;

public record ContentChangedEvent(String projectSlug, String actorUsername,
        IsEntityWithType context, IsEntityWithType target,
        ActivityType activityType, int wordCount) {

    public static ContentChangedEvent statsOnly(String projectSlug) {
        return new ContentChangedEvent(projectSlug, null, null, null, null, 0);
    }
}
