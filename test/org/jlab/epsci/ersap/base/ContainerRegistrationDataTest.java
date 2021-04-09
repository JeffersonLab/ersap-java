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
public class ContainerRegistrationDataTest {

    private final JSONObject json;
    private final ContainerRegistrationData data;

    public ContainerRegistrationDataTest() {
        json = JsonUtils.readJson("/registration-data.json")
                        .getJSONObject(ErsapConstants.REGISTRATION_KEY);
        data = new ContainerRegistrationData(JsonUtils.getContainer(json, 1));
    }

    @Test
    public void name() throws Exception {
        assertThat(data.name().canonicalName(), is("10.1.1.10_java:franklin"));
    }

    @Test
    public void startTime() throws Exception {
        assertThat(data.startTime(), notNullValue());
    }

    @Test
    public void withServices() throws Exception {
        Set<String> names = data.services()
                                .stream()
                                .map(ServiceRegistrationData::name)
                                .map(ServiceName::toString)
                                .collect(Collectors.toSet());

        assertThat(names, containsInAnyOrder("10.1.1.10_java:franklin:Engine2",
                                             "10.1.1.10_java:franklin:Engine3"));
    }

    @Test
    public void emptyServices() throws Exception {
        JSONObject containerJson = JsonUtils.getContainer(json, 2);
        ContainerRegistrationData data = new ContainerRegistrationData(containerJson);

        assertThat(data.services(), empty());
    }
}
