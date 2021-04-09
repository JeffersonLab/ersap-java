/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.examples.engines;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;

import java.util.Set;

/**
 * Empty engine.
 *
 *
 *
 *   2/ 17
 */
public class Empty implements Engine {

    @Override
    public EngineData execute(EngineData x) {
        return x;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> x) {
        System.out.println("E1 engine group execute...");
        return x.iterator().next();
    }

    @Override
    public EngineData configure(EngineData x) {
        System.out.println("E1 engine configure...");
        return x;
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.STRING);
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return ErsapUtil.buildDataTypes(EngineDataType.STRING);
    }

    @Override
    public String getDescription() {
        return "Sample service E1";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getAuthor() {
        return "Vardan Gyurgyan";
    }

    @Override
    public void reset() {

    }

    @Override
    public void destroy() {

    }
}
