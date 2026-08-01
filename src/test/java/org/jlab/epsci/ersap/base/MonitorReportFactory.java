/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.json.JSONObject;

// checkstyle.off: Javadoc
/**
 * Builds {@link DpeRegistrationData} and {@link DpeRuntimeData} from a raw
 * {@code dpeReport} document, the same way {@code ErsapSubscriptions} does when
 * a real Monitor FE message arrives.
 *
 * <p>Lives in {@code org.jlab.epsci.ersap.base} because both report constructors
 * are package private. This mirrors the existing {@code RuntimeDataFactory}
 * helper and lets the Prometheus exporter tests use the real wire format instead
 * of inventing one.
 */
public final class MonitorReportFactory {

    private MonitorReportFactory() { }

    public static DpeRegistrationData registration(String document) {
        return new DpeRegistrationData(
                new JSONObject(document).getJSONObject(ErsapConstants.REGISTRATION_KEY));
    }

    public static DpeRuntimeData runtime(String document) {
        return new DpeRuntimeData(
                new JSONObject(document).getJSONObject(ErsapConstants.RUNTIME_KEY));
    }
}
