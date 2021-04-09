/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.logging;

import java.io.PrintStream;

// checkstyle:off
/**
 * This class encapsulates the user's choice of output target.
 *
 *
 *   ceki
 *
 */
class OutputChoice {

    enum OutputChoiceType {
        SYS_OUT, SYS_ERR, FILE;
    }

    final OutputChoiceType outputChoiceType;
    final PrintStream targetPrintStream;


    OutputChoice(OutputChoiceType outputChoiceType) {
        if (outputChoiceType == OutputChoiceType.FILE) {
            throw new IllegalArgumentException();
        }
        this.outputChoiceType = outputChoiceType;
        this.targetPrintStream = null;
    }


    OutputChoice(PrintStream printStream) {
        this.outputChoiceType = OutputChoiceType.FILE;
        this.targetPrintStream = printStream;
    }


    PrintStream getTargetPrintStream() {
        switch (outputChoiceType) {
            case SYS_OUT:
                return System.out;
            case SYS_ERR:
                return System.err;
            case FILE:
                return targetPrintStream;
            default:
                throw new IllegalArgumentException();
        }

    }

}
