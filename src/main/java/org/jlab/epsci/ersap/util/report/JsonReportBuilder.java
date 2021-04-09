/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.report;

import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The json report builder class.
 */
public class JsonReportBuilder implements ExternalReport {

    @Override
    public String generateReport(DpeReport dpeData) {
        String snapshotTime = ErsapUtil.getCurrentTime();

        JSONObject dpeRuntime = new JSONObject();
        dpeRuntime.put("name", dpeData.getHost());
        dpeRuntime.put("snapshot_time", snapshotTime);
        dpeRuntime.put("cpu_usage", dpeData.getCpuUsage());
        dpeRuntime.put("memory_usage", dpeData.getMemoryUsage());
        dpeRuntime.put("load", dpeData.getLoad());

        JSONArray containersRuntimeArray = new JSONArray();
        for (ContainerReport cr : dpeData.getContainers()) {
            JSONObject containerRuntime = new JSONObject();
            containerRuntime.put("name", cr.getName());
            containerRuntime.put("snapshot_time", snapshotTime);

            long containerRequests = 0;

            JSONArray servicesRuntimeArray = new JSONArray();
            for (ServiceReport sr : cr.getServices()) {
                JSONObject serviceRuntime = new JSONObject();

                long serviceRequests = sr.getRequestCount();
                containerRequests += serviceRequests;

                serviceRuntime.put("name", sr.getName());
                serviceRuntime.put("snapshot_time", snapshotTime);
                serviceRuntime.put("n_requests", serviceRequests);
                serviceRuntime.put("n_failures", sr.getFailureCount());
                serviceRuntime.put("shm_reads", sr.getShrmReads());
                serviceRuntime.put("shm_writes", sr.getShrmWrites());
                serviceRuntime.put("bytes_recv", sr.getBytesReceived());
                serviceRuntime.put("bytes_sent", sr.getBytesSent());
                serviceRuntime.put("exec_time", sr.getExecutionTime());

                servicesRuntimeArray.put(serviceRuntime);
            }

            containerRuntime.put("n_requests", containerRequests);
            containerRuntime.put("services", servicesRuntimeArray);
            containersRuntimeArray.put(containerRuntime);
        }

        dpeRuntime.put("containers", containersRuntimeArray);

        JSONObject dpeRegistration = new JSONObject();
        dpeRegistration.put("name", dpeData.getHost());
        dpeRegistration.put("session", dpeData.getSession());
        dpeRegistration.put("description", dpeData.getDescription());
        dpeRegistration.put("language", dpeData.getLang());
        dpeRegistration.put("ersap_home", dpeData.getErsapHome());
        dpeRegistration.put("n_cores", dpeData.getCoreCount());
        dpeRegistration.put("memory_size", dpeData.getMemorySize());
        dpeRegistration.put("start_time", dpeData.getStartTime());

        JSONArray containersRegistrationArray = new JSONArray();
        for (ContainerReport cr : dpeData.getContainers()) {
            JSONObject containerRegistration = new JSONObject();
            containerRegistration.put("name", cr.getName());
            containerRegistration.put("language", cr.getLang());
            containerRegistration.put("author", cr.getAuthor());
            containerRegistration.put("start_time", cr.getStartTime());

            JSONArray servicesRegistrationArray = new JSONArray();
            for (ServiceReport sr : cr.getServices()) {
                JSONObject serviceRegistration = new JSONObject();
                serviceRegistration.put("name", sr.getName());
                serviceRegistration.put("class_name", sr.getClassName());
                serviceRegistration.put("author", sr.getAuthor());
                serviceRegistration.put("version", sr.getVersion());
                serviceRegistration.put("description", sr.getDescription());
                serviceRegistration.put("language", sr.getLang());
                serviceRegistration.put("pool_size", sr.getPoolSize());
                serviceRegistration.put("start_time", sr.getStartTime());

                servicesRegistrationArray.put(serviceRegistration);
            }

            containerRegistration.put("services", servicesRegistrationArray);
            containersRegistrationArray.put(containerRegistration);

        }

        dpeRegistration.put("containers", containersRegistrationArray);

        JSONObject dpeJsonData = new JSONObject();
        dpeJsonData.put(ErsapConstants.RUNTIME_KEY, dpeRuntime);
        dpeJsonData.put(ErsapConstants.REGISTRATION_KEY, dpeRegistration);

        return dpeJsonData.toString();
    }
}
