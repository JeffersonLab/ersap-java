package org.jlab.epsci.ersap.examples.engines.generic;

import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.std.services.AbstractEventReaderService;
import org.jlab.epsci.ersap.std.services.EventReaderException;
import org.jlab.epsci.ersap.util.IASource;
import org.json.JSONObject;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

public class SourceOfDoubles extends AbstractEventReaderService<DoubleGenerator> {
    @Override
    protected DoubleGenerator createReader(Path file, JSONObject opts) throws EventReaderException {
        return new DoubleGenerator();
    }

    @Override
    protected void closeReader() {
     reader.close();
    }

    @Override
    protected int readEventCount() throws EventReaderException {
        return reader.getEventCount();
    }

    @Override
    protected ByteOrder readByteOrder() throws EventReaderException {
        return reader.getByteOrder();
    }

    @Override
    protected Object readEvent(int eventNumber) throws EventReaderException {
        return reader.nextEvent();
    }

    @Override
    protected EngineDataType getDataType() {
        return EngineDataType.DOUBLE;
    }
}
class DoubleGenerator implements IASource {

    @Override
    public Object nextEvent() {
        return ThreadLocalRandom.current().nextDouble(1.0, 100.0);
    }

    @Override
    public int getEventCount() {
        return Integer.MAX_VALUE;
    }

    @Override
    public ByteOrder getByteOrder() {
        return ByteOrder.BIG_ENDIAN;
    }

    @Override
    public void close() {

    }
}