/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys;

import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.Engine;
import org.jlab.epsci.ersap.engine.EngineDataType;

import java.util.Set;

/**
 * ERSAP dynamic class loader.
 *
 *
 */
class EngineLoader {

    private ClassLoader classLoader;

    EngineLoader(ClassLoader cl) {
        classLoader = cl;
    }

    public Engine load(String className) throws ErsapException {
        try {
            Class<?> aClass = classLoader.loadClass(className);
            Object aInstance = aClass.newInstance();
            if (aInstance instanceof Engine) {
                Engine engine = (Engine) aInstance;
                validateEngine(engine);
                return engine;
            } else {
                throw new ErsapException("not a ERSAP engine: " + className);
            }
        } catch (ClassNotFoundException e) {
            throw new ErsapException("class not found: " + className);
        } catch (IllegalAccessException | InstantiationException e) {
            throw new ErsapException("could not create instance: " + className, e);
        }
    }

    private void validateEngine(Engine engine) throws ErsapException {
        validateDataTypes(engine.getInputDataTypes(), "input data types");
        validateDataTypes(engine.getOutputDataTypes(), "output data types");
        validateString(engine.getDescription(), "description");
        validateString(engine.getVersion(), "version");
        validateString(engine.getAuthor(), "author");
    }

    private void validateString(String value, String field) throws ErsapException {
        if (value == null || value.isEmpty()) {
            throw new ErsapException("missing engine " + field);
        }
    }

    private void validateDataTypes(Set<EngineDataType> types, String field) throws ErsapException {
        if (types == null || types.isEmpty()) {
            throw new ErsapException("missing engine " + field);
        }
        for (EngineDataType dt : types) {
            if (dt == null) {
                throw new ErsapException("null data type on engine " + field);
            }
        }
    }
}
