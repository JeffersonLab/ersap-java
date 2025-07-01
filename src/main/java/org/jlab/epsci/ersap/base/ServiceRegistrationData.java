/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.util.report.JsonUtils;
import org.json.JSONObject;

import java.time.LocalDateTime;

/**
 * The registration data of a running service.
 */
public class ServiceRegistrationData implements ErsapReportData<ServiceName> {

    private final ServiceName name;
    private final String className;
    private final LocalDateTime startTime;
    private final int poolSize;
    private final String author;
    private final String version;
    private final String description;

    ServiceRegistrationData(JSONObject json) {
        this.name = new ServiceName(json.getString("name"));
        this.className = json.getString("class_name");
        this.startTime = JsonUtils.getDate(json, "start_time");
        this.poolSize = json.optInt("pool_size");
        this.author = json.optString("author");
        this.version = json.optString("version");
        this.description = json.optString("description");
    }

    @Override
    public ServiceName name() {
        return name;
    }

    /**
     * Gets the Java class, C++ library or Python module that contains the service.
     *
     * @return the location of the service binary
     */
    public String className() {
        return className;
    }

    /**
     * Gets the local time when the service was started.
     *
     * @return the start time of the service
     */
    public LocalDateTime startTime() {
        return startTime;
    }

    /**
     * Gets an identification of who deployed the service.
     *
     * @return the author that deployed the service
     */
    public String startedBy() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Gets the maximum number of parallel threads assigned to the service.
     *
     * @return the maximum number of parallel requests that the service can process
     */
    public int poolSize() {
        return poolSize;
    }

    /**
     * Gets information about the developer(s) of the service class.
     * <p>
     * It may contain the name, email, etc of the person or group that
     * developed and released the service.
     *
     * @return the author(s) of the service
     */
    public String author() {
        return author;
    }

    /**
     * Gets the version string of the deployed service class.
     *
     * @return the version of the service
     */
    public String version() {
        return version;
    }

    /**
     * Gets the full description of the service class.
     * <p>
     * It may contain information of what the service does, what input data
     * types are supported, what output data types are returned, what errors are
     * returned, if it needs extra configuration, etc.
     *
     * @return the description of the service.
     */
    public String description() {
        return description;
    }
}
