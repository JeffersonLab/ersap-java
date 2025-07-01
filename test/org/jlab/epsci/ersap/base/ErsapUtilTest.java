/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.hamcrest.Matchers;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class ErsapUtilTest {

    private static String[] goodDpeNames = new String[] {
        "192.168.1.102_java",
        "192.168.1.102_cpp",
        "192.168.1.102_python",
        "192.168.1.102%20000_java",
        "192.168.1.102%16000_cpp",
        "192.168.1.102%9999_python",
    };
    private static String[] goodContainerNames = new String[] {
        "10.2.58.17_java:master",
        "10.2.58.17_cpp:container1",
        "10.2.58.17_python:User",
        "10.2.58.17%20000_python:User",
        "10.2.58.17_java:best_container",
        "10.2.58.17_cpp:with-hyphen",
    };
    private static String[] goodServiceNames = new String[] {
        "129.57.28.27_java:master:SimpleEngine",
        "129.57.28.27_cpp:container1:IntegrationEngine",
        "129.57.28.27_python:User:StatEngine",
        "129.57.28.27%20000_python:User:StatEngine",
        "129.57.28.27%9000_cpp:user-session:LogEngine",
    };

    private static String[] badDpeNames = new String[] {
        "192.168.1.102",
        "192.168.1.102%",
        "192_168_1_102_java",
        "192.168.1.102_erlang",
        "192.168.1.103:python",
        "192.168.1.103%aaa_python",
        "192 168 1 102 java",
        " 192.168.1.102_java",
    };
    private static String[] badContainerNames = new String[] {
        "10.2.9.9_java:",
        "10.2.9.9_java:name.part",
        "10.2.9.9_cpp:container:",
        "10.2.9.9_python:long,user",
        "10.2.58.17_python: User",
    };
    private static String[] badServiceNames = new String[] {
        "129.57.28.27_java:master:Simple:Engine",
        "129.57.28.27_cpp:container1:Integration...",
        "129.57.28.27_python:User:Stat,Engine",
        " 129.57.28.27_java:master:SimpleEngine",
        "129.57.28.27_java:master: SimpleEngine",
    };


    @Test
    public void validDpeName() throws Exception {
        assertValidNames("isDpeName", goodDpeNames);
    }


    @Test
    public void invalidDpeName() throws Exception {
        assertInvalidNames("isDpeName", badDpeNames);
        assertInvalidNames("isDpeName", goodContainerNames);
        assertInvalidNames("isDpeName", badContainerNames);
        assertInvalidNames("isDpeName", goodServiceNames);
        assertInvalidNames("isDpeName", badServiceNames);
    }


    @Test
    public void validContainerName() throws Exception {
        assertValidNames("isContainerName", goodContainerNames);
    }


    @Test
    public void invalidContainerName() throws Exception {
        assertInvalidNames("isContainerName", goodDpeNames);
        assertInvalidNames("isContainerName", badDpeNames);
        assertInvalidNames("isContainerName", badContainerNames);
        assertInvalidNames("isContainerName", goodServiceNames);
        assertInvalidNames("isContainerName", badServiceNames);
    }


    @Test
    public void validServiceName() throws Exception {
        assertValidNames("isServiceName", goodServiceNames);
    }


    @Test
    public void invalidServiceName() throws Exception {
        assertInvalidNames("isServiceName", goodDpeNames);
        assertInvalidNames("isServiceName", badDpeNames);
        assertInvalidNames("isServiceName", goodContainerNames);
        assertInvalidNames("isServiceName", badContainerNames);
        assertInvalidNames("isServiceName", badServiceNames);
    }


    @Test
    public void validCanonicalName() throws Exception {
        assertValidNames("isCanonicalName", goodDpeNames);
        assertValidNames("isCanonicalName", goodContainerNames);
        assertValidNames("isCanonicalName", goodServiceNames);
    }


    @Test
    public void invalidCanonicalName() throws Exception {
        assertInvalidNames("isCanonicalName", badDpeNames);
        assertInvalidNames("isCanonicalName", badContainerNames);
        assertInvalidNames("isCanonicalName", badServiceNames);
    }


    private void assertValidNames(String method, String[] names) throws Exception {
        Method m = ErsapUtil.class.getMethod(method, String.class);
        for (String name : names) {
            assertThat(m.invoke(null, name), is(true));
        }
    }


    private void assertInvalidNames(String method, String[] names) throws Exception {
        Method m = ErsapUtil.class.getMethod(method, String.class);
        for (String name : names) {
            assertThat(m.invoke(null, name), is(false));
        }
    }


    @Test
    public void getDpeHostReturnsTheHost() throws Exception {
        assertThat(ErsapUtil.getDpeHost(goodDpeNames[0]),       is("192.168.1.102"));
        assertThat(ErsapUtil.getDpeHost(goodContainerNames[0]), is("10.2.58.17"));
        assertThat(ErsapUtil.getDpeHost(goodServiceNames[0]),   is("129.57.28.27"));

        assertThat(ErsapUtil.getDpeHost(goodDpeNames[3]),       is("192.168.1.102"));
        assertThat(ErsapUtil.getDpeHost(goodContainerNames[3]), is("10.2.58.17"));
        assertThat(ErsapUtil.getDpeHost(goodServiceNames[3]),   is("129.57.28.27"));
    }

    @Test
    public void getDpePortReturnsThePort() throws Exception {
        assertThat(ErsapUtil.getDpePort(goodDpeNames[0]),       Matchers.is(ErsapConstants.JAVA_PORT));
        assertThat(ErsapUtil.getDpePort(goodContainerNames[0]), is(ErsapConstants.JAVA_PORT));
        assertThat(ErsapUtil.getDpePort(goodServiceNames[0]),   is(ErsapConstants.JAVA_PORT));

        assertThat(ErsapUtil.getDpePort(goodDpeNames[1]),       is(ErsapConstants.CPP_PORT));
        assertThat(ErsapUtil.getDpePort(goodContainerNames[1]), is(ErsapConstants.CPP_PORT));
        assertThat(ErsapUtil.getDpePort(goodServiceNames[1]),   is(ErsapConstants.CPP_PORT));

        assertThat(ErsapUtil.getDpePort(goodDpeNames[2]),       is(ErsapConstants.PYTHON_PORT));
        assertThat(ErsapUtil.getDpePort(goodContainerNames[2]), is(ErsapConstants.PYTHON_PORT));
        assertThat(ErsapUtil.getDpePort(goodServiceNames[2]),   is(ErsapConstants.PYTHON_PORT));

        assertThat(ErsapUtil.getDpePort(goodDpeNames[3]),       is(20000));
        assertThat(ErsapUtil.getDpePort(goodContainerNames[3]), is(20000));
        assertThat(ErsapUtil.getDpePort(goodServiceNames[3]),   is(20000));
    }

    @Test
    public void getDpeLangReturnsTheLang() throws Exception {
        assertThat(ErsapUtil.getDpeLang(goodDpeNames[0]),       is("java"));
        assertThat(ErsapUtil.getDpeLang(goodContainerNames[0]), is("java"));
        assertThat(ErsapUtil.getDpeLang(goodServiceNames[0]),   is("java"));

        assertThat(ErsapUtil.getDpeLang(goodDpeNames[3]),       is("java"));
        assertThat(ErsapUtil.getDpeLang(goodContainerNames[3]), is("python"));
        assertThat(ErsapUtil.getDpeLang(goodServiceNames[3]),   is("python"));
    }

    @Test
    public void getDpeNameReturnsTheName() throws Exception {
        assertThat(ErsapUtil.getDpeName(goodDpeNames[0]),       is("192.168.1.102_java"));
        assertThat(ErsapUtil.getDpeName(goodContainerNames[0]), is("10.2.58.17_java"));
        assertThat(ErsapUtil.getDpeName(goodServiceNames[0]),   is("129.57.28.27_java"));

        assertThat(ErsapUtil.getDpeName(goodDpeNames[3]),       is("192.168.1.102%20000_java"));
        assertThat(ErsapUtil.getDpeName(goodContainerNames[3]), is("10.2.58.17%20000_python"));
        assertThat(ErsapUtil.getDpeName(goodServiceNames[3]),   is("129.57.28.27%20000_python"));
    }


    @Test
    public void getContainerCanonicalNameReturnsTheName() throws Exception {
        assertThat(ErsapUtil.getContainerCanonicalName(goodContainerNames[0]),
                   is("10.2.58.17_java:master"));

        assertThat(ErsapUtil.getContainerCanonicalName(goodServiceNames[0]),
                   is("129.57.28.27_java:master"));
    }


    @Test
    public void getContainerNameReturnsTheName() throws Exception {
        assertThat(ErsapUtil.getContainerName(goodContainerNames[0]), is("master"));
        assertThat(ErsapUtil.getContainerName(goodServiceNames[0]),   is("master"));
    }


    @Test
    public void getEngineNameReturnsTheName() throws Exception {
        assertThat(ErsapUtil.getEngineName(goodServiceNames[0]), is("SimpleEngine"));
    }


    @Test
    public void formDpeNameReturnsTheCanonicalName() throws Exception {
        assertThat(new DpeName("10.2.58.17", ErsapLang.JAVA).canonicalName(),
                   is("10.2.58.17_java"));
    }


    @Test
    public void formContainerNameReturnsTheCanonicalName() throws Exception {
        DpeName dpe = new DpeName("10.2.58.17", ErsapLang.JAVA);
        ContainerName container = new ContainerName(dpe, "master");
        assertThat(container.canonicalName(), is("10.2.58.17_java:master"));

        assertThat(new ContainerName("10.2.58.17", ErsapLang.JAVA, "master").canonicalName(),
                   is("10.2.58.17_java:master"));
    }


    @Test
    public void formServiceNameReturnsTheCanonicalName() throws Exception {
        ContainerName container = new ContainerName("10.2.58.17", ErsapLang.JAVA, "cont");
        assertThat(new ServiceName(container, "Engine").canonicalName(),
                   is("10.2.58.17_java:cont:Engine"));

        assertThat(new ServiceName("10.2.58.17", ErsapLang.JAVA, "cont", "Engine").canonicalName(),
                   is("10.2.58.17_java:cont:Engine"));
    }


    @Test
    public void splitIntoLinesSingleLine() throws Exception {
        String text = "Call me Ishmael.";
        int length = text.length();

        assertThat(ErsapUtil.splitIntoLines("Call me Ishmael.", "", length + 10),
                   is("Call me Ishmael."));

        assertThat(ErsapUtil.splitIntoLines("Call me Ishmael.", "", length),
                   is("Call me Ishmael."));

        assertThat(ErsapUtil.splitIntoLines("Call me Ishmael.", "    ", length + 10),
                   is("    Call me Ishmael."));

        assertThat(ErsapUtil.splitIntoLines("Call me Ishmael.", "    ", length),
                   is("    Call me Ishmael."));
    }


    @Test
    public void splitIntoLinesMultipleLines() throws Exception {
        String text = "Moby Dick seeks thee not. It is thou, thou, that madly seekest him!";

        assertThat(ErsapUtil.splitIntoLines(text, "", 25),
                   is("Moby Dick seeks thee not.\n"
                    + "It is thou, thou, that\n"
                    + "madly seekest him!"));

        assertThat(ErsapUtil.splitIntoLines(text, ">>>", 25),
                   is(">>>Moby Dick seeks thee not.\n"
                    + ">>>It is thou, thou, that\n"
                    + ">>>madly seekest him!"));
    }
}
