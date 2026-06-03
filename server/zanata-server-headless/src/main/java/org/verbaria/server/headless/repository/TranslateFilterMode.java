package org.verbaria.server.headless.repository;

public enum TranslateFilterMode {
    ALL(0),
    INCOMPLETE(1),
    COMPLETE(2),
    NEEDS_REVIEW(3),
    NEED_APPROVE(4);

    private final int code;

    TranslateFilterMode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}
