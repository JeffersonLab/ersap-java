/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

enum OrchestratorConfigMode {

    FILE("file"),
    DATASET("dataset");

    private final String name;

    OrchestratorConfigMode(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    public static OrchestratorConfigMode fromString(String lang) {
        return OrchestratorConfigMode.valueOf(lang.toUpperCase());
    }
}
