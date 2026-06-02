package org.verbaria.server.headless.extension.comment;

import org.springframework.stereotype.Component;
import org.verbaria.server.headless.extension.TextFlowExtensionFactory;
import org.zanata.rest.dto.extensions.comment.SimpleComment;

@Component
public class CommentExtensionFactory
        implements TextFlowExtensionFactory<SimpleComment> {

    @Override
    public String type() {
        return SimpleComment.ID;
    }

    @Override
    public Class<SimpleComment> pojoType() {
        return SimpleComment.class;
    }
}
