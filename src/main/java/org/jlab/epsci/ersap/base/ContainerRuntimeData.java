/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.util.report.JsonUtils;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The runtime data of a running container.
 */
public class ContainerRuntimeData implements ErsapReportData<ContainerName> {

    private final ContainerName name;
    private final LocalDateTime snapshotTime;
    private final long numRequests;

    private final Set<ServiceRuntimeData> services;

    ContainerRuntimeData(JSONObject json) {
        this.name = new ContainerName(json.getString("name"));
        this.snapshotTime = JsonUtils.getDate(json, "snapshot_time");
        this.numRequests = json.optLong("n_requests");

        this.services = JsonUtils.serviceStream(json)
                                 .map(ServiceRuntimeData::new)
                                 .collect(Collectors.toSet());
    }

    @Override
    public ContainerName name() {
        return name;
    }

    /**
     * Gets the local time when the runtime data was collected.
     *
     * @return the snapshot time of the report
     */
    public LocalDateTime snapshotTime() {
        return snapshotTime;
    }

    /**
     * Gets the sum of all requests received by all the services running in the
     * container.
     *
     * @return the total number of requests received by the services of the container
     */
    public long numRequests() {
        return numRequests;
    }

    /**
     * Gets the runtime report of all the services running on the container.
     *
     * @return the runtime data of the services
     */
    public Set<ServiceRuntimeData> services() {
        return Collections.unmodifiableSet(services);
    }
}
