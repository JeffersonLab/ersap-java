/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

class WorkerFile {

    final String inputName;
    final String outputName;

    WorkerFile(String inFile, String outFile) {
        inputName = inFile;
        outputName = outFile;
    }
}
