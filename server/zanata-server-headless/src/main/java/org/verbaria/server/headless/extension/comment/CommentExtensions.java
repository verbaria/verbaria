package org.verbaria.server.headless.extension.comment;

import org.springframework.stereotype.Component;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
import org.zanata.model.HTextFlow;
import org.zanata.rest.dto.extensions.comment.SimpleComment;

@Component
public class CommentExtensions {

    private final TextFlowExtensionStore store;

    public CommentExtensions(TextFlowExtensionStore store) {
        this.store = store;
    }

    public String sourceComment(HTextFlow tf) {
        return store.get(tf, SimpleComment.class)
                .map(SimpleComment::getValue).orElse(null);
    }
}
