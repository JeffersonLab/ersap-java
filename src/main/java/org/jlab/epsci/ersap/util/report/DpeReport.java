/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.report;

import org.jlab.epsci.ersap.base.core.ErsapBase;
import org.jlab.epsci.ersap.util.EnvUtils;
import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dpe report class.
 */
public class DpeReport extends BaseReport {

    private final String host;
    private final String ersapHome;
    private final String session;

    private final int coreCount;
    private int poolSize;
    private final long memorySize;

    private final String aliveData;

    private final Map<String, ContainerReport> containers = new ConcurrentHashMap<>();

    public DpeReport(ErsapBase base, String session) {
        super(base.getName(), EnvUtils.userName(), base.getDescription());

        this.host = name;
        this.ersapHome = base.getErsapHome();
        this.session = session;

        this.coreCount = Runtime.getRuntime().availableProcessors();
        this.memorySize = Runtime.getRuntime().maxMemory();

        this.aliveData = aliveData();
    }

    private String aliveData() {
        JSONObject data =  new JSONObject();
        data.put("name", name);
        data.put("n_cores", coreCount);
        data.put("ersap_home", ersapHome);
        return data.toString();
    }

    public String getHost() {
        return host;
    }

    public String getErsapHome() {
        return ersapHome;
    }

    public String getSession() {
        return session;
    }

    public int getCoreCount() {
        return coreCount;
    }

    public long getMemorySize() {
        return memorySize;
    }

    public double getCpuUsage() {
        return SystemStats.getCpuUsage();
    }

    public long getMemoryUsage() {
        return SystemStats.getMemoryUsage();
    }

    public double getLoad() {
        return SystemStats.getSystemLoad();
    }

    public Collection<ContainerReport> getContainers() {
        return containers.values();
    }

    public ContainerReport addContainer(ContainerReport cr) {
        return containers.putIfAbsent(cr.getName(), cr);
    }

    public ContainerReport removeContainer(ContainerReport cr) {
        return containers.remove(cr.getName());
    }

    public void removeAllContainers() {
        containers.clear();
    }

    public String getAliveData() {
        return aliveData;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
}
