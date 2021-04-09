/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class CompositionTest {

    @Test
    public void firstServiceReturnsTheFirstOnSimpleComposition() throws Exception {
        Composition c = new Composition("10.1.1.1:cont:S1+10.1.1.2:cont:S2");

        assertThat(c.firstService(), is("10.1.1.1:cont:S1"));
    }


    @Test
    public void returnTheSameStringOnSimpleComposition() throws Exception {
        Composition c = new Composition("10.1.1.1:cont:S1+10.1.1.2:cont:S2");

        assertThat(c.toString(), is("10.1.1.1:cont:S1+10.1.1.2:cont:S2"));
    }
}
