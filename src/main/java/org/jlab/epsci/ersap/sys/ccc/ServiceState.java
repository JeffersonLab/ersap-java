/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys.ccc;

/**
 * Defines service-name and state pair.
 *
 *
 *
 *    2 15
 */
public class ServiceState {

    private final String name;
    private String state;

    public ServiceState(String name, String state) {
        this.name = name;
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        ServiceState other = (ServiceState) obj;

        if (!name.equals(other.name)) {
            return false;
        }
        if (!state.equals(other.state)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + state.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ServiceState{" + "name='" + name + "', state='" + state + "'}";
    }
}
