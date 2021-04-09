/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jlab.epsci.ersap.base.core.DataUtil;
import org.jlab.epsci.ersap.base.core.ErsapComponent;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.sys.ccc.CompositionCompiler;
import org.jlab.epsci.ersap.sys.ccc.ServiceState;
import org.jlab.epsci.ersap.util.report.ServiceReport;
import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.coda.xmsg.core.xMsgConstants;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMeta;

import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * A Service engine.
 * Every engine process a request in its own thread.
 *
 *
 *
 */

@SuppressFBWarnings(value = "DM_EXIT",
    justification = " I need to exit the JRE.")
class ServiceEngine {

    private final Engine engine;
    private final ServiceActor base;

    private final ServiceSysConfig sysConfig;
    private final ServiceReport sysReport;

    private final Semaphore semaphore = new Semaphore(1);

    private final CompositionCompiler compiler;

    private final ErsapComponent monitorFe;

    // Already recorded (previous) composition
    private String prevComposition = ErsapConstants.UNDEFINED;

    // The last execution time
    private long executionTime;


    ServiceEngine(Engine userEngine,
                  ServiceActor base,
                  ServiceSysConfig config,
                  ServiceReport report) {
        this.base = base;
        this.engine = userEngine;
        this.sysConfig = config;
        this.sysReport = report;
        this.compiler = new CompositionCompiler(base.getName());

        DpeName monFeDpe = FrontEnd.getMonitorFrontEnd();
        this.monitorFe = monFeDpe != null ? ErsapComponent.dpe(monFeDpe.canonicalName()) : null;
    }

    void start() throws ErsapException {
        // nothing
    }

    void stop() {
        // nothing
    }

    public void configure(xMsgMessage message) throws ErsapException {

        EngineData inputData;
        EngineData outData = null;
        try {
            inputData = getEngineData(message);
            outData = configureEngine(inputData);
        } catch (Exception e) {
            Logging.error("UNHANDLED EXCEPTION ON SERVICE CONFIGURATION: %s", base.getName());
            e.printStackTrace();
            outData = DataUtil.buildErrorData("unhandled exception", 4, e);
        } catch (Throwable e) {
            Logging.error("UNHANDLED CRITICAL ERROR ON SERVICE CONFIGURATION: %s", base.getName());
            e.printStackTrace();
            outData = DataUtil.buildErrorData("unhandled critical error", 4, e);
        } finally {
            updateMetadata(message.getMetaData(), DataUtil.getMetadata(outData));
            resetClock();
        }

        String replyTo = getReplyTo(message);
        if (replyTo != null) {
            sendResponse(outData, replyTo);
        } else {
            reportProblem(outData);
        }
    }


    private EngineData configureEngine(EngineData inputData) {
        long startTime = startClock();

        EngineData outData = engine.configure(inputData);

        stopClock(startTime);

        if (outData == null) {
            outData = new EngineData();
        }
        if (outData.getData() == null) {
            outData.setData(EngineDataType.STRING.mimeType(), "done");
        }

        return outData;
    }


    public void execute(xMsgMessage message) throws ErsapException {
        sysConfig.addRequest();
        sysReport.incrementRequestCount();

        EngineData inData = null;
        EngineData outData = null;

        try {
            inData = getEngineData(message);
            parseComposition(inData);
            outData = executeEngine(inData);

            if (outData.getStatusSeverity() == 13) {
                Logging.error("SevereError in the engine = %s: %s",
                    inData.getEngineName(), inData.getDescription());
                System.exit(13);
            }

            sysReport.addExecutionTime(executionTime);
        } catch (Exception e) {
            Logging.error("UNHANDLED EXCEPTION ON SERVICE EXECUTION: %s", base.getName());
            e.printStackTrace();
            outData = DataUtil.buildErrorData("unhandled exception", 4, e);
        } catch (Throwable e) {
            Logging.error("UNHANDLED CRITICAL ERROR ON SERVICE EXECUTION: %s", base.getName());
            e.printStackTrace();
            outData = DataUtil.buildErrorData("unhandled critical error", 4, e);
        } finally {
            updateMetadata(message.getMetaData(), DataUtil.getMetadata(outData));
            resetClock();
        }

        String replyTo = getReplyTo(message);
        if (replyTo != null) {
            sendResponse(outData, replyTo);
            return;
        }

        reportProblem(outData);
        if (outData.getStatus() == EngineStatus.ERROR) {
            sysReport.incrementFailureCount();
            return;
        }

        reportResult(outData);

        if (sysConfig.isRingRequest()) {
            String executionState = outData.getExecutionState();
            if (!executionState.isEmpty()) {
                sendResult(inData, getLinks(inData, outData));
                sendMonitorData(executionState, outData);
            } else {
                sendResult(outData, getLinks(inData, outData));
            }
        } else {
            sendResult(outData, getLinks(inData, outData));
        }
    }

    private void parseComposition(EngineData inData) throws ErsapException {
        String currentComposition = inData.getComposition();
        if (currentComposition == null) {
            return;
        }

        if (!currentComposition.equals(prevComposition)) {
            compiler.compile(currentComposition);
            prevComposition = currentComposition;
        }
    }

    private Set<String> getLinks(EngineData inData, EngineData outData) {
        ServiceState ownerSS = new ServiceState(outData.getEngineName(),
            outData.getExecutionState());
        ServiceState inputSS = new ServiceState(inData.getEngineName(),
            inData.getExecutionState());

        return compiler.getLinks(ownerSS, inputSS);
    }

    private EngineData executeEngine(EngineData inData)
            throws ErsapException {
        long startTime = startClock();

        EngineData outData = engine.execute(inData);

        stopClock(startTime);

        if (outData == null) {
            throw new ErsapException("null engine result");
        }
        if (outData.getData() == null) {
            if (outData.getStatus() == EngineStatus.ERROR) {
                outData.setData(EngineDataType.STRING.mimeType(),
                    ErsapConstants.UNDEFINED);
            } else {
                throw new ErsapException("empty engine result");
            }
        }

        return outData;
    }

    private void updateMetadata(xMsgMeta.Builder inMeta, xMsgMeta.Builder outMeta) {
        outMeta.setAuthor(base.getName());
        outMeta.setVersion(engine.getVersion());

        if (!outMeta.hasCommunicationId()) {
            outMeta.setCommunicationId(inMeta.getCommunicationId());
        }
        outMeta.setComposition(inMeta.getComposition());
        outMeta.setExecutionTime(executionTime);
        outMeta.setAction(inMeta.getAction());

        if (outMeta.hasSenderState()) {
            sysConfig.updateState(outMeta.getSenderState());
        }
    }

    private void reportResult(EngineData outData) throws ErsapException {
        if (sysConfig.isDataRequest()) {
            reportData(outData);
            sysConfig.resetDataRequestCount();
        }
        if (sysConfig.isDoneRequest()) {
            reportDone(outData);
            sysConfig.resetDoneRequestCount();
        }
    }

    private void sendResponse(EngineData outData, String replyTo) throws ErsapException {
        base.send(putEngineData(outData, replyTo));
    }

    private void sendResult(EngineData outData, Set<String> outLinks) throws ErsapException {
        for (String ss : outLinks) {
            ErsapComponent comp = ErsapComponent.dpe(ss);
            xMsgMessage msg = putEngineData(outData, ss);
            base.send(comp.getProxyAddress(), msg);
        }
    }

    private void reportDone(EngineData data) throws ErsapException {
        String mt = data.getMimeType();
        Object ob = data.getData();
        data.setData(EngineDataType.STRING.mimeType(), ErsapConstants.DONE);

        sendReport(ErsapConstants.DONE, data);

        data.setData(mt, ob);
    }

    private void reportData(EngineData data) throws ErsapException {
        sendReport(ErsapConstants.DATA, data);
    }

    private void reportProblem(EngineData data) throws ErsapException {
        EngineStatus status = data.getStatus();
        if (status.equals(EngineStatus.ERROR)) {
            sendReport(ErsapConstants.ERROR, data);
        } else if (status.equals(EngineStatus.WARNING)) {
            sendReport(ErsapConstants.WARNING, data);
        }
    }


    private void sendReport(String topicPrefix, EngineData data) throws ErsapException {
        xMsgTopic topic = xMsgTopic.wrap(topicPrefix + xMsgConstants.TOPIC_SEP + base.getName());
        xMsgMessage transit = DataUtil.serialize(topic, data, engine.getOutputDataTypes());
        base.send(base.getFrontEnd(), transit);
    }

    private void sendMonitorData(String state, EngineData data) throws ErsapException {
        if (monitorFe != null) {
            xMsgTopic topic = xMsgTopic.wrap(ErsapConstants.MONITOR_REPORT
                + xMsgConstants.TOPIC_SEP + state
                + xMsgConstants.TOPIC_SEP + sysReport.getSession()
                + xMsgConstants.TOPIC_SEP + base.getEngine());
            xMsgMessage transit = DataUtil.serialize(topic, data, engine.getOutputDataTypes());
            base.sendUncheck(monitorFe.getProxyAddress(), transit);
        }
    }


    private EngineData getEngineData(xMsgMessage message) throws ErsapException {
        xMsgMeta.Builder metadata = message.getMetaData();
        String mimeType = metadata.getDataType();
        if (mimeType.equals(ErsapConstants.SHARED_MEMORY_KEY)) {
            sysReport.incrementShrmReads();
            String sender = metadata.getSender();
            int id = metadata.getCommunicationId();
            return SharedMemory.getEngineData(base.getName(), sender, id);
        } else {
            sysReport.addBytesReceived(message.getDataSize());
            return DataUtil.deserialize(message, engine.getInputDataTypes());
        }
    }

    private xMsgMessage putEngineData(EngineData data, String receiver)
            throws ErsapException {
        xMsgTopic topic = xMsgTopic.wrap(receiver);
        if (SharedMemory.containsReceiver(receiver)) {
            int id = data.getCommunicationId();
            SharedMemory.putEngineData(receiver, base.getName(), id, data);
            sysReport.incrementShrmWrites();

            xMsgMeta.Builder metadata = xMsgMeta.newBuilder();
            metadata.setAuthor(base.getName());
            metadata.setComposition(data.getComposition());
            metadata.setCommunicationId(id);
            metadata.setAction(xMsgMeta.ControlAction.EXECUTE);
            metadata.setDataType(ErsapConstants.SHARED_MEMORY_KEY);

            return new xMsgMessage(topic, metadata, ErsapConstants.SHARED_MEMORY_KEY.getBytes());
        } else {
            xMsgMessage output = DataUtil.serialize(topic, data, engine.getOutputDataTypes());
            sysReport.addBytesSent(output.getDataSize());
            return output;
        }
    }


    private String getReplyTo(xMsgMessage message) {
        xMsgMeta.Builder meta = message.getMetaData();
        if (meta.hasReplyTo()) {
            return meta.getReplyTo();
        }
        return null;
    }


    private void resetClock() {
        executionTime = 0;
    }

    private long startClock() {
        return System.nanoTime();
    }

    private void stopClock(long watch) {
        executionTime = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - watch);
    }


    public boolean tryAcquire() {
        return semaphore.tryAcquire();
    }

    public void release() {
        semaphore.release();
    }
}
