/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys.ccc;

import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.EngineData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * This class presents routing schema for a service, result of the ERSAP
 * composition compiler, parsing routing statements of a composition.
 * <p>
 * Contains a map that has keys of input service names, data from which are
 * required logically to be ANDed, i.e. data from all services in the AND must
 * be present in order for the receiving service to execute its service engine.
 * <p>
 * Also contains a set of names of all services that are linked to the service
 * of interest, i.e. names of all services that this services will send it's
 * output data.
 *
 *
 *
 *    2 15
 */
class Statement {

    // The name of the service that this statement is relevant to.
    private final String serviceName;

    // statement string
    private final String statementString;

    // The map that has keys of input service names, data from which are required
    // logically to be ANDed. I.e. data from all services in the AND must be present
    // in order for the receiving service to execute its service engine.
    private final Map<String, EngineData> logAndInputs = new HashMap<>();

    // Names of all services that are linked to the service of interest, i.e. names
    // of all services that send data to this service
    private final Set<String> inputLinks = new LinkedHashSet<>();

    // Names of all services that are linked to the service of interest, i.e. names
    // of all services that this services will send it's output data.
    private final Set<String> outputLinks = new LinkedHashSet<>();


    Statement(String statementString, String serviceName) throws ErsapException {
        if (statementString.contains(serviceName)) {
            this.statementString = statementString;
            this.serviceName = serviceName;
            process(statementString);
        } else {
            throw new ErsapException("irrelevant statement");
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getStatementString() {
        return statementString;
    }

    public Set<String> getInputLinks() {
        return inputLinks;
    }

    public Set<String> getOutputLinks() {
        return outputLinks;
    }

    public Map<String, EngineData> getLogAndInputs() {
        return logAndInputs;
    }

    /**
     * Analyses the composition string.
     *
     * @param statement string
     * @throws ErsapException
     */
    private void process(String statement) throws ErsapException {
        // This is new routing statement
        // clear local containers
        inputLinks.clear();
        outputLinks.clear();
        logAndInputs.clear();

        // parse the new statement to find input and output
        // linked service names
        if (statement.contains(serviceName)) {
            parseLinked(serviceName, statement);
            if (isLogAnd(serviceName, statement)) {
                for (String sn : inputLinks) {
                    logAndInputs.put(sn, null);
                }
            }
        }
    }

    /**
     * Parses composition field of the transient data and returns the list of
     * services output linked to this service, i.e. that are getting output
     * data of this service.
     * <p>
     * Attention: service name CAN NOT appear twice in the composition.
     *
     * @param serviceName the name of the service
     *                    for which we find input/output links
     * @param statement the string of the composition
     */
    private void parseLinked(String serviceName, String statement) throws ErsapException {

        // List that contains composition elements
        List<String> elementSet = new ArrayList<>();

        StringTokenizer st = new StringTokenizer(statement, "+");
        while (st.hasMoreTokens()) {
            String el = st.nextToken();
            el = CompositionParser.removeFirst(el, "&");
            el = CompositionParser.removeFirst(el, "{");
            elementSet.add(el);
        }

        // See if the string contains this service name, and record the index,
        // and analyze index+1 element.
        // Note: multiple services can send to a single service, like: s1,s2+s3.
        // (this is the reason we use in:contains)
        int index = -1;
        for (String s : elementSet) {
            index++;
            if (s.contains(serviceName)) {
                break;
            }
        }
        if (index == -1) {
            throw new ErsapException("Routing statement parsing exception. "
                    + "Service name can not be found in the statement.");
        } else {
            int pIndex = index - 1;
            if (pIndex >= 0) {
                String element = CompositionParser.getJSetElementAt(elementSet, pIndex);
                // the case to fan out the output of this service
                elementTokenizer(element, inputLinks);
            }

            // define output links
            int nIndex = index + 1;
            if (elementSet.size() > nIndex) {
                String element = CompositionParser.getJSetElementAt(elementSet, nIndex);
                // the case to fan out the output of this service
                elementTokenizer(element, outputLinks);
            }
        }
    }

    private void elementTokenizer(String element, Set<String> container) {
        if (element.contains(",")) {
            StringTokenizer st1 = new StringTokenizer(element, ",");
            while (st1.hasMoreTokens()) {
                container.add(st1.nextToken());
            }
        } else {
            container.add(element);
        }
    }

    /**
     * Check to see in the composition this service is required to logically
     * AND inputs before executing its service.
     *
     * @param serviceNname in the composition
     * @param composition the string of the composition
     * @return true if component name is programmed as {@code &<service_name>"}
     */
    private boolean isLogAnd(String serviceNname, String composition) {
        String ac = "&" + serviceNname;

        // List that contains composition elements
        Set<String> elementSet = new LinkedHashSet<>();

        StringTokenizer st = new StringTokenizer(composition, "+");
        while (st.hasMoreTokens()) {
            elementSet.add(st.nextToken());
        }

        for (String s : elementSet) {
            if (s.equals(ac)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Statement{"
                + "serviceName='" + serviceName + "'"
                + ", statementString='" + statementString + "'"
                + ", logAndInputs=" + logAndInputs
                + ", inputLinks=" + inputLinks
                + ", outputLinks=" + outputLinks
                + "}";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Statement)) {
            return false;
        }

        Statement other = (Statement) obj;

        if (!serviceName.equals(other.serviceName)) {
            return false;
        }
        if (!statementString.equals(other.statementString)) {
            return false;
        }
        if (!logAndInputs.equals(other.logAndInputs)) {
            return false;
        }
        if (!inputLinks.equals(other.inputLinks)) {
            return false;
        }
        if (!outputLinks.equals(other.outputLinks)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = serviceName.hashCode();
        result = 31 * result + statementString.hashCode();
        result = 31 * result + logAndInputs.hashCode();
        result = 31 * result + inputLinks.hashCode();
        result = 31 * result + outputLinks.hashCode();
        return result;
    }
}
