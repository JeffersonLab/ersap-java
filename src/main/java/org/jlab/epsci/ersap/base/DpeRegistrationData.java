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
 * The registration data of a running DPE.
 */
public class DpeRegistrationData implements ErsapReportData<DpeName> {

    private final DpeName name;
    private final LocalDateTime startTime;
    private final String ersapHome;
    private final String session;

    private final int numCores;
    private final long memorySize;

    private final Set<ContainerRegistrationData> containers;

    DpeRegistrationData(JSONObject json) {
        name = new DpeName(json.getString("name"));
        this.startTime = JsonUtils.getDate(json, "start_time");
        this.ersapHome = json.optString("ersap_home");
        this.session = json.optString("session");
        this.numCores = json.optInt("n_cores");
        this.memorySize = json.optLong("memory_size");

        this.containers = JsonUtils.containerStream(json)
                                   .map(ContainerRegistrationData::new)
                                   .collect(Collectors.toSet());
    }

    @Override
    public DpeName name() {
        return name;
    }

    /**
     * Gets the local time when the DPE was started.
     *
     * @return the start time of the DPE
     */
    public LocalDateTime startTime() {
        return startTime;
    }

    /**
     * Gets an identification of who started the DPE.
     *
     * @return the author that started the DPE
     */
    public String startedBy() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Gets the local value of the <code>$ERSAP_HOME</code> environment variable
     * used by the DPE.
     *
     * @return the local path of the ERSAP installation for the DPE
     */
    public String ersapHome() {
        return ersapHome;
    }

    /**
     * Gets the session used by the DPE to publish reports.
     *
     * @return the session ID of the DPE
     */
    public String session() {
        return session;
    }

    /**
     * Gets the maximum number of cores assigned to the DPE.
     *
     * @return the number of cores the can be used by the DPE.
     */
    public int numCores() {
        return numCores;
    }

    /**
     * Gets the maximum amount of memory available to the DPE.
     *
     * @return the maximum amount of memory that the DPE will attempt to use,
     *         measured in bytes
     */
    public long memorySize() {
        return memorySize;
    }

    /**
     * Gets all the containers running on the DPE.
     *
     * @return the registration data of the containers
     */
    public Set<ContainerRegistrationData> containers() {
        return Collections.unmodifiableSet(containers);
    }
}
