/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.engine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.jlab.epsci.ersap.engine.EngineSpecification.ParseException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("integration")
public class EngineSpecificationTest {

    @Test
    public void constructorThrowsIfSpecificationFileNotFound() {
        Exception ex = assertThrows(ParseException.class, () ->
                new EngineSpecification("std.services.convertors.EvioToNothing"));
        assertThat(ex.getMessage(), containsString("Service specification file not found"));
    }


    @Test
    public void constructorThrowsIfYamlIsMalformed() {
        Exception ex = assertThrows(ParseException.class, () ->
                new EngineSpecification("resources/service-spec-bad-1"));
        assertThat(ex.getMessage(), is("Unexpected YAML content"));
    }


    @Test
    public void parseServiceSpecification() {
        EngineSpecification sd = new EngineSpecification("resources/service-spec-simple");
        assertEquals(sd.name(), "SomeService");
        assertEquals(sd.engine(), "std.services.SomeService");
        assertEquals(sd.type(), "java");
    }


    @Test
    public void parseAuthorSpecification() {
        EngineSpecification sd = new EngineSpecification("resources/service-spec-simple");
        assertEquals(sd.author(), "Sebastian Mancilla");
        assertEquals(sd.email(), "smancill@jlab.org");
    }


    @Test
    public void parseVersionWhenYamlReturnsDouble() {
        EngineSpecification sd = new EngineSpecification("resources/service-spec-1");
        assertEquals(sd.version(), "0.8");
    }


    @Test
    public void parseVersionWhenYamlReturnsInteger() {
        EngineSpecification sd = new EngineSpecification("resources/service-spec-simple");
        assertEquals(sd.version(), "2");
    }


    @Test
    public void parseStringThrowsIfMissingKey() {
        Exception ex = assertThrows(ParseException.class, () ->
                new EngineSpecification("resources/service-spec-bad-2"));
        assertThat(ex.getMessage(), containsString("Missing key:"));
    }


    @Test
    public void parseStringThrowsIfBadKeyType() {
        Exception ex = assertThrows(ParseException.class, () ->
                new EngineSpecification("resources/service-spec-bad-3"));
        assertThat(ex.getMessage(), containsString("Bad type for:"));
    }
}
