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

import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Tag("integration")
public class ContainerRuntimeDataTest {

    private final JSONObject json;
    private final ContainerRuntimeData data;

    public ContainerRuntimeDataTest() {
        json = JsonUtils.readJson("/runtime-data.json")
                        .getJSONObject(ErsapConstants.RUNTIME_KEY);
        data = new ContainerRuntimeData(JsonUtils.getContainer(json, 1));
    }

    @Test
    public void name() throws Exception {
        assertThat(data.name().canonicalName(), is("10.1.1.10_java:franklin"));
    }

    @Test
    public void snapshotTime() throws Exception {
        assertThat(data.snapshotTime(), notNullValue());
    }

    @Test
    public void numRequests() throws Exception {
        assertThat(data.numRequests(), is(3500L));
    }

    @Test
    public void withServices() throws Exception {
        Set<String> names = data.services()
                                .stream()
                                .map(ServiceRuntimeData::name)
                                .map(ServiceName::toString)
                                .collect(Collectors.toSet());

        assertThat(names, containsInAnyOrder("10.1.1.10_java:franklin:Engine2",
                                             "10.1.1.10_java:franklin:Engine3"));
    }

    @Test
    public void emptyServices() throws Exception {
        JSONObject containerJson = JsonUtils.getContainer(json, 2);
        ContainerRuntimeData data = new ContainerRuntimeData(containerJson);

        assertThat(data.services(), empty());
    }
}
