package org.zanata.log4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LoggerUtil {

    private LoggerUtil() {}

    /** Returns a logger named after the caller's package. */
    public static Logger getLogger(Class<?> caller) {
        String name = caller.getName();
        int lastDot = name.lastIndexOf('.');
        String pkg = lastDot >= 0 ? name.substring(0, lastDot) : "";
        return LoggerFactory.getLogger(pkg);
    }
}
