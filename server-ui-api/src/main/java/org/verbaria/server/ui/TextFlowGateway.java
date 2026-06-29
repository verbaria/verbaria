package org.verbaria.server.ui;

import java.util.List;
import java.util.Optional;

import org.zanata.rest.dto.extensions.gettext.TextFlowExtension;

public interface TextFlowGateway {

    List<Long> documentTextFlowIds(long documentId);

    TextFlowSnapshot snapshot(long textFlowId);

    String sourceText(long textFlowId);

    <T extends TextFlowExtension> Optional<T> extension(long textFlowId,
            Class<T> type);

    void putExtension(long textFlowId, TextFlowExtension extension);

    /**
     * Update the source text and an extension of a text flow in one
     * transaction, so both changes commit together (or not at all).
     */
    void update(long textFlowId, String sourceText, TextFlowExtension extension);
}
