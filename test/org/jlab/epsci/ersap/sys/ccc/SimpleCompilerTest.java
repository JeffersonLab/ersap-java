/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys.ccc;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class SimpleCompilerTest {

    private final String composition = "10.10.10.1_java:C:S1+"
                                     + "10.10.10.1_java:C:S2+"
                                     + "10.10.10.1_java:C:S3+"
                                     + "10.10.10.1_java:C:S4";

    @Test()
    public void testInvalidServiceName() throws Exception {
        String composition = "10.10.10.1_java:C:S1+"
                           + "10.10.10.1:C:S2+"
                           + "10.10.10.1:C:S4";
        SimpleCompiler cc = new SimpleCompiler("10.10.10.1_java:C:S1");

        assertThrows(IllegalArgumentException.class, () -> cc.compile(composition));
    }

    @Test
    public void testMissingCurrentService() throws Exception {
        SimpleCompiler cc = new SimpleCompiler("10.10.10.1_java:C:S6");

        assertThrows(IllegalArgumentException.class, () -> cc.compile(composition));
    }

    @Test
    public void testServiceAtTheBeginning() throws Exception {
        SimpleCompiler cc = new SimpleCompiler("10.10.10.1_java:C:S1");
        cc.compile(composition);

        Set<String> expected = new HashSet<>(Arrays.asList("10.10.10.1_java:C:S2"));
        assertThat(cc.getOutputs(), is(expected));
    }

    @Test
    public void testServiceAtTheMiddle() throws Exception {
        SimpleCompiler cc = new SimpleCompiler("10.10.10.1_java:C:S2");
        cc.compile(composition);

        Set<String> expected = new HashSet<>(Arrays.asList("10.10.10.1_java:C:S3"));
        assertThat(cc.getOutputs(), is(expected));
    }

    @Test
    public void testServiceAtTheEnd() throws Exception {
        SimpleCompiler cc = new SimpleCompiler("10.10.10.1_java:C:S4");
        cc.compile(composition);

        Set<String> expected = new HashSet<>();
        assertThat(cc.getOutputs(), is(expected));
    }

    @Test
    public void testMultipleCalls() throws Exception {
        SimpleCompiler cc = new SimpleCompiler("10.10.10.1_java:C:S3");
        cc.compile(composition);

        String composition2 = "10.10.10.1_java:C:S1+"
                            + "10.10.10.1_java:C:S3+"
                            + "10.10.10.1_java:C:S5";
        cc.compile(composition2);

        Set<String> expected = new HashSet<>(Arrays.asList("10.10.10.1_java:C:S5"));
        assertThat(cc.getOutputs(), is(expected));
    }
}
