/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.core.ErsapConstants;

/**
 * <p>
 *     This class holds service status information,
 *     including static information, such as the
 *     name and the description, as well as dynamic
 *     information about the number of requests to
 *     this service and service engine execution time.
 *     Note: request number will be updated by the
 *     service container.
 * </p>
 *
 *
 */
class ServiceStatus {
    private String name = ErsapConstants.UNDEFINED;

    private String description = ErsapConstants.UNDEFINED;

    private volatile int requestNumber;

    private volatile long averageExecutionTime;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getRequestNumber() {
        return requestNumber;
    }

    public void setRequestNumber(int requestNumber) {
        this.requestNumber = requestNumber;
    }

    public long getAverageExecutionTime() {
        return averageExecutionTime;
    }

    public void setAverageExecutionTime(long averageExecutionTime) {
        this.averageExecutionTime = averageExecutionTime;
    }
}
