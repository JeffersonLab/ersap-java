/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.examples.engines;

import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;

import java.util.Set;

/**
 * Factorial calculating service engine
 * Created by   on  2 16.
 */
public class Factorial implements Engine {

    private double fact(int n) {
        double result;
        if (n == 1) {
            return 1;
        }
        result = fact(n - 1) * n;
        return result;
    }

    @Override
    public EngineData configure(EngineData input) {
        return null;
    }

    @Override
    public EngineData execute(EngineData input) {
        fact(7777);
        return input;
    }

    @Override
    public EngineData executeGroup(Set<EngineData> inputs) {
        return null;
    }

    @Override
    public Set<EngineDataType> getInputDataTypes() {
        return null;
    }

    @Override
    public Set<EngineDataType> getOutputDataTypes() {
        return null;
    }

    @Override
    public Set<String> getStates() {
        return null;
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getVersion() {
        return null;
    }

    @Override
    public String getAuthor() {
        return null;
    }

    @Override
    public void reset() {

    }

    @Override
    public void destroy() {

    }
}
