/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.DpeName;

/**
 * Stores properties of a DPE.
 * <p>
 * Currently, these properties are:
 * <ul>
 * <li>name (IP address)
 * <li>number of cores
 * <li>value of {@code $ERSAP_HOME}
 * </ul>
 */
class DpeInfo {

    final DpeName name;
    final int cores;
    final String ersapHome;

    DpeInfo(DpeName name, int cores, String ersapHome) {
        if (name == null) {
            throw new IllegalArgumentException("Null DPE name");
        }
        if (cores < 0) {
            throw new IllegalArgumentException("Invalid number of cores");
        }
        this.name = name;
        this.cores = cores;
        this.ersapHome = ersapHome;
    }


    DpeInfo(String name, int cores, String ersapHome) {
        this(new DpeName(name), cores, ersapHome);
    }


    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + name.hashCode();
        return result;
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof DpeInfo)) {
            return false;
        }
        DpeInfo other = (DpeInfo) obj;
        if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "[" + name + "," + cores + "]";
    }
}
