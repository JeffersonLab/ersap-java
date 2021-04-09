/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.sys.ccc.ServiceState;

/**
 *  Service system configuration.
 */
class ServiceSysConfig {

    private final ServiceState state;

    private boolean isDataRequest;
    private boolean isDoneRequest;
    private boolean isRingRequest;

    private int doneReportThreshold;
    private int dataReportThreshold;

    private int dataRequestCount;
    private int doneRequestCount;

    ServiceSysConfig(String name, String initialState) {
        state = new ServiceState(name, initialState);
    }

    public void addRequest() {
        dataRequestCount++;
        doneRequestCount++;
    }

    public void resetDoneRequestCount() {
        doneRequestCount = 0;
    }

    public void resetDataRequestCount() {
        dataRequestCount = 0;
    }

    public boolean isDataRequest() {
        return isDataRequest && dataRequestCount >= dataReportThreshold;
    }

    public void setDataRequest(boolean isDataRequest) {
        this.isDataRequest = isDataRequest;
    }

    public boolean isDoneRequest() {
        return isDoneRequest && doneRequestCount >= doneReportThreshold;
    }

    public void setDoneRequest(boolean isDoneRequest) {
        this.isDoneRequest = isDoneRequest;
    }

    public boolean isRingRequest() {
        return isRingRequest;
    }

    public void setRingRequest(boolean isRingRequest) {
        this.isRingRequest = isRingRequest;
    }

    public int getDoneReportThreshold() {
        return doneReportThreshold;
    }

    public void setDoneReportThreshold(int doneReportThreshold) {
        this.doneReportThreshold = doneReportThreshold;
    }

    public int getDataReportThreshold() {
        return dataReportThreshold;
    }

    public void setDataReportThreshold(int dataReportThreshold) {
        this.dataReportThreshold = dataReportThreshold;
    }

    public int getDataRequestCount() {
        return dataRequestCount;
    }

    public int getDoneRequestCount() {
        return doneRequestCount;
    }

    public void updateState(String newState) {
        state.setState(newState);
    }
}
