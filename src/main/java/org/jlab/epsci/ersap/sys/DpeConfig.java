/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

class DpeConfig {

    private final int maxCores;
    private final int poolSize;
    private final long reportPeriod;

    DpeConfig(int maxCores, int poolSize, long reportPeriod) {
        this.maxCores = maxCores;
        this.poolSize = poolSize;
        this.reportPeriod = reportPeriod;
    }

    int maxCores() {
        return maxCores;
    }

    int poolSize() {
        return poolSize;
    }

    long reportPeriod() {
        return reportPeriod;
    }


    static int calculatePoolSize(int cores) {
        int halfCores = cores / 2;
        if (halfCores <= 2) {
            return 2;
        }
        int poolSize = (halfCores % 2 == 0) ? halfCores : halfCores + 1;
        if (poolSize > 16) {
            return 16;
        }
        return poolSize;
    }
}
