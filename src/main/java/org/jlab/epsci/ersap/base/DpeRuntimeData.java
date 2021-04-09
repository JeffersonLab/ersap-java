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
 * The runtime data of a running DPE.
 */
public class DpeRuntimeData implements ErsapReportData<DpeName> {

    private final DpeName name;
    private final LocalDateTime snapshotTime;
    private final double cpuUsage;
    private final long memUsage;
    private final double sysLoad;

    private final Set<ContainerRuntimeData> containers;

    DpeRuntimeData(JSONObject json) {
        this.name = new DpeName(json.getString("name"));
        this.snapshotTime = JsonUtils.getDate(json, "snapshot_time");
        this.cpuUsage = json.optDouble("cpu_usage", Double.NaN);
        this.memUsage = json.optLong("memory_usage");
        this.sysLoad = json.optDouble("load", -1);

        this.containers = JsonUtils.containerStream(json)
                                   .map(ContainerRuntimeData::new)
                                   .collect(Collectors.toSet());
    }

    @Override
    public DpeName name() {
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
     * Gets the reported CPU usage of the DPE.
     * <p>
     * A value of 1.0 is equivalent to 100% CPU usage.
     *
     * @return the CPU usage or NaN if it could not be obtained
     */
    public double cpuUsage() {
        return cpuUsage;
    }

    /**
     * Gets the amount of memory in use by the DPE.
     *
     * @return the amount of memory that the DPE is using, measured in bytes
     */
    public long memoryUsage() {
        return memUsage;
    }

    /**
     * Gets the system load average of the node where the DPE is running.
     *
     * @return the system load average, or a negative value if not available
     */
    public double systemLoad() {
        return sysLoad;
    }

    /**
     * Gets the runtime report of all the containers running on the DPE.
     *
     * @return the runtime data of the containers
     */
    public Set<ContainerRuntimeData> containers() {
        return Collections.unmodifiableSet(containers);
    }
}
