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
public class DpeRuntimeDataTest {

    private final JSONObject json;
    private final DpeRuntimeData data;

    public DpeRuntimeDataTest() {
        json = JsonUtils.readJson("/runtime-data.json")
                        .getJSONObject(ErsapConstants.RUNTIME_KEY);
        data = new DpeRuntimeData(json);
    }

    @Test
    public void name() throws Exception {
        assertThat(data.name().canonicalName(), is("10.1.1.10_java"));
    }

    @Test
    public void snapshotTime() throws Exception {
        assertThat(data.snapshotTime(), notNullValue());
    }

    @Test
    public void cpuUsage() throws Exception {
        assertThat(data.cpuUsage(), is(greaterThan(0.0)));
    }

    @Test
    public void testMemoryUsage() throws Exception {
        assertThat(data.memoryUsage(), is(greaterThan(0L)));
    }

    @Test
    public void testLoad() throws Exception {
        assertThat(data.systemLoad(), is(greaterThan(0.0)));
    }

    @Test
    public void withContainers() throws Exception {
        Set<String> names = data.containers()
                                .stream()
                                .map(ContainerRuntimeData::name)
                                .map(ContainerName::toString)
                                .collect(Collectors.toSet());

        assertThat(names, containsInAnyOrder("10.1.1.10_java:trevor",
                                             "10.1.1.10_java:franklin",
                                             "10.1.1.10_java:michael"));
    }

    @Test
    public void emptyContainers() throws Exception {
        json.put("containers", new JSONArray());
        DpeRuntimeData dpe = new DpeRuntimeData(json);

        assertThat(dpe.containers(), empty());
    }
}
