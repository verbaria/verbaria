package org.verbaria.server.ui;

public interface DocumentAction {

    String labelKey();

    String progressKey();

    String resultKey();

    boolean appliesTo(String projectType);

    int run(TextFlowGateway gateway, long documentId);
}
