/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.engine;

import org.jlab.coda.xmsg.data.xMsgD.xMsgData;
import org.jlab.coda.xmsg.data.xMsgD.xMsgPayload;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.array;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.sameInstance;

public class EngineDataTypeTest {

    private static final Random RANDOM = new Random();

    @Test
    public void testIntegerSerializer() throws Exception {
        EngineDataType dt = EngineDataType.SINT32;
        ErsapSerializer s = dt.serializer();

        ByteBuffer b = s.write(18);
        Integer d = (Integer) s.read(b);

        assertThat(d, is(18));
    }

    @Test
    public void testFloatingPointSerializer() throws Exception {
        EngineDataType dt = EngineDataType.DOUBLE;
        ErsapSerializer s = dt.serializer();

        ByteBuffer b = s.write(78.98);
        Double d = (Double) s.read(b);

        assertThat(d, is(closeTo(78.99, 0.01)));
    }

    @Test
    public void testStringSerializer() throws Exception {
        EngineDataType dt = EngineDataType.STRING;
        ErsapSerializer s = dt.serializer();

        ByteBuffer b = s.write("master of puppets");
        String d = (String) s.read(b);

        assertThat(d, is("master of puppets"));
    }

    @Test
    public void testIntegerArraySerializer() throws Exception {
        EngineDataType dt = EngineDataType.ARRAY_SINT32;
        ErsapSerializer s = dt.serializer();

        Integer[] v = new Integer[] {4, 5, 6};
        ByteBuffer b = s.write(v);
        Integer[] d = (Integer[]) s.read(b);

        assertThat(d, is(v));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFloatingPointArraySerializer() throws Exception {
        EngineDataType dt = EngineDataType.ARRAY_DOUBLE;
        ErsapSerializer s = dt.serializer();

        Double[] v = new Double[] {4.1, 5.6};
        ByteBuffer b = s.write(v);
        Double[] d = (Double[]) s.read(b);

        assertThat(d, is(array(closeTo(4.1, 0.01), closeTo(5.6, 0.1))));
    }

    @Test
    public void testStringArraySerializer() throws Exception {
        EngineDataType dt = EngineDataType.ARRAY_STRING;
        ErsapSerializer s = dt.serializer();

        String[] v = new String[] {"Ride the Lightning",
                                   "Master of Puppets", "...And Justice for All"};
        ByteBuffer b = s.write(v);
        String[] d = (String[]) s.read(b);

        assertThat(d, is(v));
    }

    @Test
    public void testNativeDataSerializer() throws Exception {
        xMsgData.Builder builder = xMsgData.newBuilder();

        builder.setFLSINT32(56);
        builder.setDOUBLE(5.6);
        builder.addSTRINGA("Ride the Lightning");
        builder.addSTRINGA("Master of Puppets");
        builder.addSTRINGA("...And Justice for All");

        EngineDataType dt = EngineDataType.NATIVE_DATA;
        ErsapSerializer s = dt.serializer();

        ByteBuffer b = s.write(builder.build());
        xMsgData d = (xMsgData) s.read(b);

        assertThat(d, is(builder.build()));
    }


    @Test
    public void testNativePayloadSerializer() throws Exception {
        xMsgPayload.Builder payload = xMsgPayload.newBuilder();

        xMsgData data1 = xMsgData.newBuilder()
                                 .addDOUBLEA(1)
                                 .addDOUBLEA(4.5)
                                 .addDOUBLEA(5.8)
                                 .build();
        xMsgPayload.Item.Builder item1 = xMsgPayload.Item.newBuilder();
        item1.setData(data1);
        item1.setName("doubles");

        xMsgData data2 = xMsgData.newBuilder()
                                 .addFLOATA(4.3f)
                                 .addFLOATA(4.5f)
                                 .addFLOATA(5.8f)
                                 .build();
        xMsgPayload.Item.Builder item2 = xMsgPayload.Item.newBuilder();
        item2.setData(data2);
        item2.setName("floats");


        EngineDataType dt = EngineDataType.NATIVE_PAYLOAD;
        ErsapSerializer s = dt.serializer();

        ByteBuffer b = s.write(payload.build());
        xMsgPayload p = (xMsgPayload) s.read(b);

        assertThat(p, is(payload.build()));
    }


    @Test
    public void testRawBytesSerializer() throws Exception {

        byte[] r = new byte[100];
        RANDOM.nextBytes(r);

        ByteBuffer bb = ByteBuffer.wrap(r);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        EngineDataType dt = EngineDataType.BYTES;
        ErsapSerializer s = dt.serializer();

        ByteBuffer b = s.write(bb);
        ByteBuffer d = (ByteBuffer) s.read(b);

        assertThat(d, is(sameInstance(bb)));
        assertThat(d.order(), is(ByteOrder.LITTLE_ENDIAN));
    }
}
