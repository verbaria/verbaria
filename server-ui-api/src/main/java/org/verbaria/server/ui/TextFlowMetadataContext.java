package org.verbaria.server.ui;

public record TextFlowMetadataContext(TextFlowGateway gateway, long textFlowId,
        TextFlowSnapshot snapshot, boolean canEdit, boolean canReview) {
}
