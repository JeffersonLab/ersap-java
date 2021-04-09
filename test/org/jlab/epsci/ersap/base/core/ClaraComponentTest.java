/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base.core;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


public class ErsapComponentTest {

    @Test
    public void testJavaDpeComponent() throws Exception {
        ErsapComponent c = ErsapComponent.dpe("10.2.9.1_java");

        assertThat(c.getCanonicalName(), is("10.2.9.1_java"));
        assertThat(c.getTopic().toString(), is("dpe:10.2.9.1_java"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_java"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.JAVA_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.JAVA_PORT));
    }

    @Test
    public void testJavaContainerComponent() throws Exception {
        ErsapComponent c = ErsapComponent.container("10.2.9.1_java:master");

        assertThat(c.getCanonicalName(), is("10.2.9.1_java:master"));
        assertThat(c.getTopic().toString(), is("container:10.2.9.1_java:master"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_java"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.JAVA_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.JAVA_PORT));

        assertThat(c.getContainerName(), is("master"));
    }

    @Test
    public void testJavaServiceComponent() throws Exception {
        ErsapComponent c = ErsapComponent.service("10.2.9.1_java:master:E1");

        assertThat(c.getCanonicalName(), is("10.2.9.1_java:master:E1"));
        assertThat(c.getTopic().toString(), is("10.2.9.1_java:master:E1"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_java"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.JAVA_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.JAVA_PORT));

        assertThat(c.getContainerName(), is("master"));
        assertThat(c.getEngineName(), is("E1"));
    }

    @Test
    public void testCppDpeComponent() throws Exception {
        ErsapComponent c = ErsapComponent.dpe("10.2.9.1_cpp");

        assertThat(c.getCanonicalName(), is("10.2.9.1_cpp"));
        assertThat(c.getTopic().toString(), is("dpe:10.2.9.1_cpp"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_cpp"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.CPP_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.CPP_PORT));
    }

    @Test
    public void testCppContainerComponent() throws Exception {
        ErsapComponent c = ErsapComponent.container("10.2.9.1_cpp:master");

        assertThat(c.getCanonicalName(), is("10.2.9.1_cpp:master"));
        assertThat(c.getTopic().toString(), is("container:10.2.9.1_cpp:master"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_cpp"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.CPP_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.CPP_PORT));

        assertThat(c.getContainerName(), is("master"));
    }

    @Test
    public void testCppServiceComponent() throws Exception {
        ErsapComponent c = ErsapComponent.service("10.2.9.1_cpp:master:E1");

        assertThat(c.getCanonicalName(), is("10.2.9.1_cpp:master:E1"));
        assertThat(c.getTopic().toString(), is("10.2.9.1_cpp:master:E1"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_cpp"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.CPP_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.CPP_PORT));

        assertThat(c.getContainerName(), is("master"));
        assertThat(c.getEngineName(), is("E1"));
    }

    @Test
    public void testPythonDpeComponent() throws Exception {
        ErsapComponent c = ErsapComponent.dpe("10.2.9.1_python");

        assertThat(c.getCanonicalName(), is("10.2.9.1_python"));
        assertThat(c.getTopic().toString(), is("dpe:10.2.9.1_python"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_python"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.PYTHON_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.PYTHON_PORT));
    }

    @Test
    public void testPythonContainerComponent() throws Exception {
        ErsapComponent c = ErsapComponent.container("10.2.9.1_python:master");

        assertThat(c.getCanonicalName(), is("10.2.9.1_python:master"));
        assertThat(c.getTopic().toString(), is("container:10.2.9.1_python:master"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_python"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.PYTHON_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.PYTHON_PORT));

        assertThat(c.getContainerName(), is("master"));
    }

    @Test
    public void testPythonServiceComponent() throws Exception {
        ErsapComponent c = ErsapComponent.service("10.2.9.1_python:master:E1");

        assertThat(c.getCanonicalName(), is("10.2.9.1_python:master:E1"));
        assertThat(c.getTopic().toString(), is("10.2.9.1_python:master:E1"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1_python"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is(ErsapConstants.PYTHON_LANG));
        assertThat(c.getDpePort(), is(ErsapConstants.PYTHON_PORT));

        assertThat(c.getContainerName(), is("master"));
        assertThat(c.getEngineName(), is("E1"));
    }

    @Test
    public void testComponentWithCustomPort() throws Exception {
        ErsapComponent c = ErsapComponent.dpe("10.2.9.1%9999_java");

        assertThat(c.getCanonicalName(), is("10.2.9.1%9999_java"));
        assertThat(c.getTopic().toString(), is("dpe:10.2.9.1%9999_java"));

        assertThat(c.getDpeCanonicalName(), is("10.2.9.1%9999_java"));
        assertThat(c.getDpeHost(), is("10.2.9.1"));
        assertThat(c.getDpeLang(), is("java"));
        assertThat(c.getDpePort(), is(9999));
    }
}
