package org.zanata.service;

import org.zanata.common.ContentState;

public interface TranslationCounter {
    /**
     * Count one or more copied messages
     * @param state state of copied message
     * @param score similarity score of the match (in source language)
     * @param chars number of characters in the source language (Unicode code points)
     * @param words number of words in the source language
     * @param messages number of messages copied. Must be {@code >= 1}.
     */
    void count(ContentState state, int score, long chars, long words, long messages);

    default void count(ContentState state, int score, long chars, long words) {
        count(state, score, chars, words, 1);
    }
}
