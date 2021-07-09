/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.services;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.util.Set;

/**
 * This abstract class assumes that the TwoStreamSource is a
 * back-end server that listens JLAB VTP streams.
 *
 * @param <TwoStreamSource> Two stream aggregator and decoder object
 */
public abstract class AbstractStreamSourceService<TwoStreamSource> extends AbstractService {
    private static final String CONF_ACTION = "action";

    private static final String CONF_ACTION_CONNECT = "connect";
    private static final String CONF_ACTION_DISCONNECT = "disconnect";

    private static final String CONF_PORT_1 = "port1";
    private static final String CONF_PORT_2 = "port2";

    private static final String REQUEST_NEXT = "next";
    private static final String REQUEST_NEXT_REC = "next-rec";


    /**
     * The TwoStreamSource object.
     */
    protected TwoStreamSource streamSource;
    private final Object streamSourceLock = new Object();

    @Override
    public EngineData configure(EngineData input) {
        final long startTime = System.currentTimeMillis();
        if (input.getMimeType().equalsIgnoreCase(EngineDataType.JSON.mimeType())) {
            String source = (String) input.getData();
            JSONObject data = new JSONObject(source);
            if (data.has(CONF_ACTION) && data.has(CONF_PORT_1) && data.has(CONF_PORT_2)) {
                int p1 = data.getInt(CONF_PORT_1);
                int p2 = data.getInt(CONF_PORT_1);
                String action = data.getString(CONF_ACTION);
                if (action.equals(CONF_ACTION_CONNECT)) {
                    try {
                        streamSource = createStreamSource(p1, p2, data);
                        streamStart();
                    } catch (StreamSourceException e) {
                        e.printStackTrace();
                    }
                } else if (action.equals(CONF_ACTION_DISCONNECT)) {
                    closeStreamSource();
                } else {
                    logger.error("config: wrong '{}' parameter value = {}",
                        CONF_ACTION, action);
                }
            } else {
                logger.error("config: missing '{}' or '{}' or '{}' parameters",
                    CONF_ACTION, CONF_PORT_1, CONF_PORT_2);
            }
        } else {
            logger.error("config: wrong mime-type {}", input.getMimeType());
        }
        logger.info("config time: {} [ms]", System.currentTimeMillis() - startTime);
        return null;

    }

    protected abstract TwoStreamSource createStreamSource(int port1,
                                                          int por2,
                                                          JSONObject opts)
            throws StreamSourceException;

    /**
     * Closes the stream source.
     */
    protected abstract void closeStreamSource();

    /**
     * Gets the byte order of the events in the stream.
     *
     * @return the byte order of the events in the stream.
     * @throws StreamSourceException exception
     */
    protected abstract ByteOrder readByteOrder() throws StreamSourceException;

    /**
     * Gets the ERSAP engine data-type for the type of the stream.
     * The data-type will be used to serialize the events when the engine data
     * result needs to be sent to services over the network.
     *
     * @return the data-type of the stream.
     */
    protected abstract EngineDataType getDataType();

    protected abstract void streamStart();

    protected abstract Object getStreamUnit() throws StreamSourceException;


    @Override
    public EngineData execute(EngineData input) {
        EngineData output = new EngineData();

        String dt = input.getMimeType();
        if (dt.equalsIgnoreCase(EngineDataType.STRING.mimeType())) {
            String request = (String) input.getData();
            if (request.equals(REQUEST_NEXT) || request.equals(REQUEST_NEXT_REC)) {
                getNextEvent(input, output);
            } else {
                ServiceUtils.setError(output, String.format("Wrong input data = '%s'", request));
            }
        } else {
            String errorMsg = String.format("Wrong input type '%s'", dt);
            ServiceUtils.setError(output, errorMsg);
        }

        return output;
    }

    private void getNextEvent(EngineData input, EngineData output) {
//        synchronized (streamSourceLock) {
        if (streamSource == null) {
            ServiceUtils.setError(output,
                "Error: Stream source instance is NULL.",
                1);
        } else {
            try {
                Object event = getStreamUnit();
                output.setData(getDataType().toString(), event);
                output.setDescription("data");
                ServiceUtils.setError(output,
                    "Error: Exception getting the decoded stream unit.",
                    1);
            } catch (StreamSourceException e) {
                e.printStackTrace();
            }
        }
//        }
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return null;
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(
            getDataType(),
            EngineDataType.STRING,
            EngineDataType.SFIXED32);
    }

}
