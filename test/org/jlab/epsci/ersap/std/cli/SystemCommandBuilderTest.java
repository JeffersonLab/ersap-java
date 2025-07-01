/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SystemCommandBuilderTest {

    private SystemCommandBuilder b;

    @BeforeEach
    public void setUp() {
        b = new SystemCommandBuilder("${ERSAP_HOME}/bin/ersap-orchestrator");
        b.addOption("-t", 10);
        b.addOption("-i", "$CLAS12DIR/exp/input");
        b.addArgument("custom services.yml");
        b.addArgument("data/files.txt");
    }

    @Test
    public void outputArrayDoesNotNeedQuotes() throws Exception {
        // checkstyle.off: Indentation
        assertThat(b.toArray(), is(new String[]{
                "${ERSAP_HOME}/bin/ersap-orchestrator",
                "-t", "10",
                "-i", "$CLAS12DIR/exp/input",
                "custom services.yml",
                "data/files.txt"
                }));
        // checkstyle.on: Indentation
    }

    @Test
    public void outputStringNeedsQuotes() throws Exception {
        assertThat(b.toString(), is(
                "\"${ERSAP_HOME}/bin/ersap-orchestrator\""
                + " -t 10"
                + " -i \"$CLAS12DIR/exp/input\""
                + " \"custom services.yml\""
                + " data/files.txt"));
    }

    @Test
    public void outputStringCanQuoteEverything() throws Exception {
        b.quoteAll(true);

        assertThat(b.toString(), is(
                "\"${ERSAP_HOME}/bin/ersap-orchestrator\""
                + " \"-t\" \"10\""
                + " \"-i\" \"$CLAS12DIR/exp/input\""
                + " \"custom services.yml\""
                + " \"data/files.txt\""));
    }

    @Test
    public void outputStringMayBeMultiline() throws Exception {
        b.multiLine(true);

        assertThat(b.toString(), is(
                "\"${ERSAP_HOME}/bin/ersap-orchestrator\" \\"
                + "\n        -t 10 \\"
                + "\n        -i \"$CLAS12DIR/exp/input\" \\"
                + "\n        \"custom services.yml\" \\"
                + "\n        data/files.txt"));
    }
}
