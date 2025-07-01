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
 * The runtime data of a running service.
 */
public class ServiceRuntimeData implements ErsapReportData<ServiceName> {

    private final ServiceName name;
    private final LocalDateTime snapshotTime;
    private final long numRequest;
    private final long numFailures;
    private final long shmReads;
    private final long shmWrites;
    private final long bytesRecv;
    private final long bytesSent;
    private final long execTime;

    ServiceRuntimeData(JSONObject json) {
        this.name = new ServiceName(json.getString("name"));
        this.snapshotTime = JsonUtils.getDate(json, "snapshot_time");
        this.numRequest = json.optLong("n_requests");
        this.numFailures = json.optLong("n_failures");
        this.shmReads = json.optLong("shm_reads");
        this.shmWrites = json.optLong("shm_writes");
        this.bytesRecv = json.optLong("bytes_recv");
        this.bytesSent = json.optLong("bytes_sent");
        this.execTime = json.optLong("exec_time");
    }

    @Override
    public ServiceName name() {
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
     * Gets the total number of requests received by the service.
     * This is the number of all requests received since the service was
     * deployed.
     *
     * @return the accumulated number of received requests
     */
    public long numRequests() {
        return numRequest;
    }


    /**
     * Gets the total number of failed requests processed by the service.
     * This is the number of all requests that returned an error since the
     * service was deployed.
     *
     * @return the accumulated number of failed processed requests
     */
    public long numFailures() {
        return numFailures;
    }

    /**
     * Gets the total number of requests received by the service through the
     * shared memory of the DPE.
     * This is the number of all requests received since the service was
     * deployed.
     * <p>
     * A service uses the shared memory to receive requests from services
     * running in the same DPE. For requests received from services in other
     * DPEs and orchestrators see {@link #bytesRecv}.
     *
     * @return the accumulated number of requests received through the shared memory
     */
    public long sharedMemoryReads() {
        return shmReads;
    }

    /**
     * Gets the total number of requests sent by the service through the shared
     * memory of the DPE.
     * This is the number of all requests sent since the service was deployed.
     * <p>
     * A service uses the shared memory to send requests to services running in
     * the same DPE. For requests sent to services in other DPEs and
     * orchestrators see {@link #bytesSent}.
     *
     * @return the accumulated number of requests sent through the shared memory
     */
    public long sharedMemoryWrites() {
        return shmWrites;
    }

    /**
     * Gets the total amount of bytes received by the service through the
     * network.
     * This is the sum of the size of all messages received by the service since
     * it was deployed.
     * <p>
     * A service receives network messages from services
     * running in other DPEs, and orchestrators. For messages received from
     * services in the same DPE see {@link #sharedMemoryReads}.
     *
     * @return the accumulated amount of bytes received through the network
     */
    public long bytesReceived() {
        return bytesRecv;
    }

    /**
     * Gets the total amount of bytes sent by the service through the network.
     * This is the sum of the size of all messages published by the service
     * since it was deployed.
     * <p>
     * A service publishes network messages to services running in other DPEs,
     * and orchestrators. For messages sent to services in the same DPE see
     * {@link #sharedMemoryWrites}.
     *
     * @return the accumulated amount of bytes sent through the network
     */
    public long bytesSent() {
        return bytesSent;
    }

    /**
     * Gets the total execution time of the service.
     * This is the sum of the execution time of all requests processed by the
     * service since it was deployed.
     *
     * @return the accumulated execution time, in microseconds
     */
    public long executionTime() {
        return execTime;
    }
}
