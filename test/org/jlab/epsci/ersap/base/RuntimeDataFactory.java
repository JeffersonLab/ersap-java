/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.util.report.JsonUtils;
import org.json.JSONObject;

// checkstyle.off: Javadoc
public final class RuntimeDataFactory {

    private RuntimeDataFactory() { }

    public static DpeRuntimeData parseRuntime(String resource) {
        JSONObject json = JsonUtils.readJson(resource).getJSONObject(ErsapConstants.RUNTIME_KEY);
        return new DpeRuntimeData(json);
    }
}
