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
 * The registration data of a running container.
 */
public class ContainerRegistrationData implements ErsapReportData<ContainerName> {

    private final ContainerName name;
    private final LocalDateTime startTime;
    private final Set<ServiceRegistrationData> services;

    ContainerRegistrationData(JSONObject json) {
        this.name = new ContainerName(json.getString("name"));
        this.startTime = JsonUtils.getDate(json, "start_time");

        this.services = JsonUtils.serviceStream(json)
                                 .map(ServiceRegistrationData::new)
                                 .collect(Collectors.toSet());
    }

    @Override
    public ContainerName name() {
        return name;
    }

    /**
     * Gets the local time when the container was started.
     *
     * @return the start time of the container
     */
    public LocalDateTime startTime() {
        return startTime;
    }

    /**
     * Gets an identification of who started the container.
     *
     * @return the author that started the container
     */
    public String startedBy() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Gets all the services running on the container.
     *
     * @return the registration data of the services
     */
    public Set<ServiceRegistrationData> services() {
        return Collections.unmodifiableSet(services);
    }
}
