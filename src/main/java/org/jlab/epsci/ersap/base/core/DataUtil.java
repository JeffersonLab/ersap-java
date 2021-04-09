/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base.core;

import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.data.xMsgM.xMsgMeta;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Set;

public final class DataUtil {

    private static final EngineDataAccessor DATA_ACCESSOR = EngineDataAccessor.getDefault();

    private DataUtil() { }

    public static EngineData buildErrorData(String msg, int severity, Throwable exception) {
        EngineData outData = new EngineData();
        outData.setData(EngineDataType.STRING.mimeType(), msg);
        outData.setDescription(ErsapUtil.reportException(exception));
        outData.setStatus(EngineStatus.ERROR, severity);
        return outData;
    }

    /**
     * Convoluted way to access the internal EngineData metadata,
     * which is hidden to users.
     *
     * @param data {@link EngineData} object
     * @return {@link org.jlab.coda.xmsg.data.xMsgM.xMsgMeta.Builder} object
     */
    public static xMsgMeta.Builder getMetadata(EngineData data) {
        return DATA_ACCESSOR.getMetadata(data);
    }

    /**
     * Builds a message by serializing passed data object using serialization
     * routine defined in one of the data types objects.
     *
     * @param topic     the topic where the data will be published
     * @param data      the data to be serialized
     * @param dataTypes the set of registered data types
     * @throws ErsapException if the data could not be serialized
     * @return xMsgMessage
     */
    public static xMsgMessage serialize(xMsgTopic topic,
                                        EngineData data,
                                        Set<EngineDataType> dataTypes)
            throws ErsapException {

        xMsgMeta.Builder metadata = DATA_ACCESSOR.getMetadata(data);
        String mimeType = metadata.getDataType();
        for (EngineDataType dt : dataTypes) {
            if (dt.mimeType().equals(mimeType)) {
                try {
                    ByteBuffer bb = dt.serializer().write(data.getData());
                    if (bb.order() == ByteOrder.BIG_ENDIAN) {
                        metadata.setByteOrder(xMsgMeta.Endian.Big);
                    } else {
                        metadata.setByteOrder(xMsgMeta.Endian.Little);
                    }
                    return new xMsgMessage(topic, metadata, bb.array());
                } catch (ErsapException e) {
                    throw new ErsapException("Could not serialize " + mimeType, e);
                }
            }
        }
        if (mimeType.equals(EngineDataType.STRING.mimeType())) {
            ByteBuffer bb = EngineDataType.STRING.serializer().write(data.getData());
            return new xMsgMessage(topic, metadata, bb.array());
        }
        throw new ErsapException("Unsupported mime-type = " + mimeType);
    }

    /**
     * De-serializes data of the message {@link org.jlab.coda.xmsg.core.xMsgMessage},
     * represented as a byte[] into an object of az type defined using the mimeType/dataType
     * of the meta-data (also as a part of the xMsgMessage). Second argument is used to
     * pass the serialization routine as a method of the
     * {@link EngineDataType} object.
     *
     * @param msg {@link org.jlab.coda.xmsg.core.xMsgMessage} object
     * @param dataTypes set of {@link EngineDataType} objects
     * @return {@link EngineData} object containing de-serialized data object
     *          and metadata
     * @throws ErsapException exception
     */
    public static EngineData deserialize(xMsgMessage msg, Set<EngineDataType> dataTypes)
            throws ErsapException {
        xMsgMeta.Builder metadata = msg.getMetaData();
        String mimeType = metadata.getDataType();
        for (EngineDataType dt : dataTypes) {
            if (dt.mimeType().equals(mimeType)) {
                try {
                    ByteBuffer bb = ByteBuffer.wrap(msg.getData());
                    if (metadata.getByteOrder() == xMsgMeta.Endian.Little) {
                        bb.order(ByteOrder.LITTLE_ENDIAN);
                    }
                    Object userData = dt.serializer().read(bb);
                    return DATA_ACCESSOR.build(userData, metadata);
                } catch (ErsapException e) {
                    throw new ErsapException("ERSAP-Error: Could not deserialize " + mimeType, e);
                }
            }
        }
        throw new ErsapException("ERSAP-Error: Unsupported mime-type = " + mimeType);
    }


    public abstract static class EngineDataAccessor {

        private static volatile EngineDataAccessor defaultAccessor;

        public static EngineDataAccessor getDefault() {
            new EngineData(); // Load the accessor
            EngineDataAccessor a = defaultAccessor;
            if (a == null) {
                throw new IllegalStateException("EngineDataAccessor should not be null");
            }
            return a;
        }

        public static void setDefault(EngineDataAccessor accessor) {
            if (defaultAccessor != null) {
                throw new IllegalStateException("EngineDataAccessor should be null");
            }
            defaultAccessor = accessor;
        }

        protected abstract xMsgMeta.Builder getMetadata(EngineData data);

        protected abstract EngineData build(Object data, xMsgMeta.Builder metadata);
    }
}
