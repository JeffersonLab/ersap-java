/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;


/**
 * A ERSAP composition of services.
 * The orchestrator should send a request to the first service of the
 * composition, and the output of each service will be sent to the next service
 * in the composition, until all are executed.
 * <p>
 * The result of each service can be compared against given states to provide
 * custom routing logic.
 */
public class Composition {

    private List<String> allServices = new ArrayList<>();
    private String text;

    /**
     * Parses a composition from the given string.
     *
     * @param composition a string defining a valid composition
     */
    public Composition(String composition) {
        text = composition;

        // TODO: doesn't handle conditionals
        StringTokenizer st = new StringTokenizer(composition, "+;&,");
        while (st.hasMoreTokens()) {
            allServices.add(st.nextToken().trim());
        }
    }

    /**
     * Gets the first service of this composition.
     * This is the service that starts the composition.
     *
     * @return the canonical name of the first service
     */
    public String firstService() {
        return allServices.get(0);
    }

    @Override
    public String toString() {
        return text;
    }
}
