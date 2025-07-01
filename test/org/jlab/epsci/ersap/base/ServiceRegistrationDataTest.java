/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.util.report.JsonUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Tag("integration")
public class ServiceRegistrationDataTest {

    private final JSONObject json;
    private final ServiceRegistrationData data;

    public ServiceRegistrationDataTest() {
        json = JsonUtils.readJson("/registration-data.json")
                        .getJSONObject(ErsapConstants.REGISTRATION_KEY);
        data = new ServiceRegistrationData(JsonUtils.getService(json, 1, 0));
    }

    @Test
    public void name() throws Exception {
        assertThat(data.name().canonicalName(), is("10.1.1.10_java:franklin:Engine2"));
    }

    @Test
    public void className() throws Exception {
        assertThat(data.className(), is("org.jlab.ersap.examples.Engine2"));
    }

    @Test
    public void startTime() throws Exception {
        assertThat(data.startTime(), notNullValue());
    }

    @Test
    public void poolSize() throws Exception {
        assertThat(data.poolSize(), is(2));
    }

    @Test
    public void author() throws Exception {
        assertThat(data.author(), is("Trevor"));
    }

    @Test
    public void version() throws Exception {
        assertThat(data.version(), is("1.0"));
    }

    @Test
    public void description() throws Exception {
        assertThat(data.description(), containsString("Some description"));
    }
}
