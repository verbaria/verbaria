/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 */
package org.zanata.log4j;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Stub. The original extended {@code it.openutils.log4j.AlternateSMTPAppender}
 * which in turn used {@code javax.mail.*} — incompatible with the
 * jakarta.mail-based runtime. Re-implement on top of log4j2's SMTPAppender
 * (jakarta.mail-aware) when log4j 2 is rolled in. For now this is a no-op
 * appender so log4j 1.x configuration still loads.
 */
public class ZanataSMTPAppender extends AppenderSkeleton {

    public ZanataSMTPAppender() {
    }

    @Override protected void append(LoggingEvent event) { /* no-op */ }
    @Override public boolean requiresLayout() { return false; }
    @Override public void close() { /* no-op */ }

    public String getFrom() { return null; }
    public void setFrom(String from) {}
    public String getTo() { return null; }
    public void setTo(String to) {}
    public String getSubject() { return null; }
    public void setSubject(String subject) {}
    public void setThreshold(Object threshold) {}
    public void setEvaluator(Object evaluator) {}
    public void setTimeout(int t) {}
    public int getTimeout() { return 0; }
    public void activateOptions() { /* no-op */ }
}
