/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.logging;

public class LoggerFactory {

    public LoggerFactory() {
        SimpleLogger.lazyInit();
    }

    public Logger getLogger(String name) {
        return new SimpleLogger(name);
    }
}
