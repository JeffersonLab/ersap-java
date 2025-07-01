/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import org.jlab.epsci.ersap.base.ServiceName;
import org.jlab.epsci.ersap.base.ServiceRuntimeData;

class ServiceBenchmark {

    private final ServiceName name;
    private final long numRequest;
    private final long numFailures;
    private final long shmReads;
    private final long shmWrites;
    private final long bytesRecv;
    private final long bytesSent;
    private final long execTime;

    ServiceBenchmark(ServiceRuntimeData first, ServiceRuntimeData last) {
        this.name = first.name();
        this.numRequest = last.numRequests() - first.numRequests();
        this.numFailures = last.numFailures() - first.numFailures();
        this.shmReads = last.sharedMemoryReads() - first.sharedMemoryReads();
        this.shmWrites = last.sharedMemoryWrites() - first.sharedMemoryWrites();
        this.bytesRecv = last.bytesReceived() - first.bytesReceived();
        this.bytesSent = last.bytesSent() - first.bytesSent();
        this.execTime = last.executionTime() - first.executionTime();
    }

    public ServiceName name() {
        return name;
    }

    public long numRequests() {
        return numRequest;
    }

    public long numFailures() {
        return numFailures;
    }

    public long sharedMemoryReads() {
        return shmReads;
    }

    public long sharedMemoryWrites() {
        return shmWrites;
    }

    public long bytesReceived() {
        return bytesRecv;
    }

    public long bytesSent() {
        return bytesSent;
    }

    public long executionTime() {
        return execTime;
    }
}
