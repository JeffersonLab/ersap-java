/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.util.report.JsonUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Tag("integration")
public class DpeRegistrationDataTest {

    private final JSONObject json;
    private final DpeRegistrationData data;

    public DpeRegistrationDataTest() {
        json = JsonUtils.readJson("/registration-data.json")
                        .getJSONObject(ErsapConstants.REGISTRATION_KEY);
        data = new DpeRegistrationData(json);
    }

    @Test
    public void name() throws Exception {
        assertThat(data.name().canonicalName(), is("10.1.1.10_java"));
    }

    @Test
    public void startTime() throws Exception {
        assertThat(data.startTime(), notNullValue());
    }

    @Test
    public void ersapHome() throws Exception {
        assertThat(data.ersapHome(), is("/home/user/ersap"));
    }

    @Test
    public void session() throws Exception {
        assertThat(data.session(), is("los_santos"));
    }

    @Test
    public void numCores() throws Exception {
        assertThat(data.numCores(), is(8));
    }

    @Test
    public void memorySize() throws Exception {
        assertThat(data.memorySize(), greaterThan(0L));
    }

    @Test
    public void withContainers() throws Exception {
        Set<String> names = data.containers()
                                .stream()
                                .map(ContainerRegistrationData::name)
                                .map(ContainerName::toString)
                                .collect(Collectors.toSet());

        assertThat(names, containsInAnyOrder("10.1.1.10_java:trevor",
                                             "10.1.1.10_java:franklin",
                                             "10.1.1.10_java:michael"));
    }

    @Test
    public void emptyContainers() throws Exception {
        json.put("containers", new JSONArray());
        DpeRegistrationData dpe = new DpeRegistrationData(json);

        assertThat(dpe.containers(), empty());
    }
}
