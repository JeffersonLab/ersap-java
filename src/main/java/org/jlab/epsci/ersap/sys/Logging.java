/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class Logging {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");


    private Logging() { }

    static String getCurrentTime() {
        return FORMATTER.format(LocalDateTime.now());
    }

    static void info(String format, Object... args) {
        String currentTime = getCurrentTime();
        synchronized (LOCK) {
            System.out.printf("%s: ", currentTime);
            System.out.printf(format, args);
            System.out.println();
        }
    }

    static void error(String format, Object... args) {
        String currentTime = getCurrentTime();
        synchronized (LOCK) {
            System.err.printf("%s: ", currentTime);
            System.err.printf(format, args);
            System.err.println();
        }
    }
}
