/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys.ccc;

import org.jlab.epsci.ersap.base.ErsapUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

public class SimpleCompiler {

    private final String serviceName;
    private List<String> prev;
    private List<String> next;

    public SimpleCompiler(String serviceName) {
        this.serviceName = serviceName;
    }

    public void compile(String composition) {
        prev = new ArrayList<>();
        next = new ArrayList<>();
        List<String> subComposition = prev;
        boolean serviceFound = false;
        StringTokenizer st = new StringTokenizer(composition, "+");
        while (st.hasMoreTokens()) {
            String service = st.nextToken();
            if (service.equals(serviceName)) {
                subComposition = next;
                serviceFound = true;
                continue;
            }
            if (!ErsapUtil.isCanonicalName(service)) {
                throw new IllegalArgumentException("Invalid composition: " + composition);
            }
            subComposition.add(service);
        }
        if (!serviceFound) {
            throw new IllegalArgumentException(serviceName + " not in: " + composition);
        }
    }

    public Set<String> getOutputs() {
        Set<String> outputs = new HashSet<>();
        if (!next.isEmpty()) {
            outputs.add(next.get(0));
        }
        return outputs;
    }
}
