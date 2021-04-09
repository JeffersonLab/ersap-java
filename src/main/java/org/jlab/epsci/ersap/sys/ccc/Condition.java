/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys.ccc;

import org.jlab.epsci.ersap.base.error.ErsapException;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Defines it's own as well as all input service states that if exists in
 * run-time will make this ERSAP composition condition a true condition.
 * <p>
 *
 *
 *
 *    2 15
 */
class Condition {

    // The name of the service that this condition is relevant to.
    private final String serviceName;

    // States of services that are required to be present in order this condition to be true
    private final Set<ServiceState> andStates = new LinkedHashSet<>();

    // NOT states of services that are required to be present in order this condition to be true
    private final Set<ServiceState> andNotStates = new LinkedHashSet<>();

    // Required states of services that will make this statement true
    private final Set<ServiceState> orStates = new LinkedHashSet<>();

    // Required states of services that will make this statement true
    private final Set<ServiceState> orNotStates = new LinkedHashSet<>();

    Condition(String conditionString, String serviceName) throws ErsapException {
        this.serviceName = serviceName;
        process(conditionString);
    }

    public String getServiceName() {
        return serviceName;
    }

    public Set<ServiceState> getAndStates() {
        return andStates;
    }

    private void addAndState(ServiceState andState) {
        this.andStates.add(andState);
    }

    public Set<ServiceState> getAndNotStates() {
        return andNotStates;
    }

    private void addAndNotState(ServiceState andNotState) {
        this.andNotStates.add(andNotState);
    }

    public Set<ServiceState> getOrStates() {
        return orStates;
    }

    private void addOrState(ServiceState orState) {
        this.orStates.add(orState);
    }

    public Set<ServiceState> getOrNotStates() {
        return orNotStates;
    }

    private void addOrNotState(ServiceState orState) {
        this.orNotStates.add(orState);
    }

    private void process(String cs) throws ErsapException {
        if (cs.contains("(")) {
            cs = cs.replaceAll("\\(", "");
        }
        if (cs.contains(")")) {
            cs = cs.replaceAll("\\)", "");
        }

        if (cs.contains("&&")) {
            parseCondition(cs, "&&");
        } else if (cs.contains("!!")) {
            parseCondition(cs, "!!");
        } else {
            parseCondition(cs, null);
        }
    }

    private void parseCondition(String cs, String logicOperator) throws ErsapException {
        StringTokenizer t0;
        StringTokenizer t1;
        if (logicOperator == null) {
            Pattern p = Pattern.compile(CompositionCompiler.SIMP_COND);
            Matcher m = p.matcher(cs);
            if (m.matches()) {
                if (cs.contains("!=")) {
                    t1 = new StringTokenizer(cs, "!=\"");
                    if (t1.countTokens() != 2) {
                        throw new ConditionException();
                    }
                    ServiceState sst = new ServiceState(t1.nextToken(), t1.nextToken());
                    addOrNotState(sst);

                } else if (cs.contains("==")) {
                    t1 = new StringTokenizer(cs, "==\"");
                    if (t1.countTokens() != 2) {
                        throw new ConditionException();
                    }
                    ServiceState sst = new ServiceState(t1.nextToken(), t1.nextToken());
                    addOrState(sst);

                } else {
                    throw new ConditionException();
                }
            } else {
                throw new ConditionException();
            }

        } else {

            if (cs.contains("&&") && !cs.contains("!!")) {
                t0 = new StringTokenizer(cs, logicOperator);
                while (t0.hasMoreTokens()) {
                    String ac = t0.nextToken();

                    Pattern p = Pattern.compile(CompositionCompiler.SIMP_COND);
                    Matcher m = p.matcher(ac);
                    if (m.matches()) {

                        if (ac.contains("!=")) {
                            t1 = new StringTokenizer(t0.nextToken(), "!=\"");
                            if (t1.countTokens() != 2) {
                                throw new ConditionException();
                            }
                            ServiceState sst = new ServiceState(t1.nextToken(), t1.nextToken());
                            addAndNotState(sst);

                        } else if (ac.contains("==")) {
                            t1 = new StringTokenizer(t0.nextToken(), "==\"");
                            if (t1.countTokens() != 2) {
                                throw new ConditionException();
                            }
                            ServiceState sst = new ServiceState(t1.nextToken(), t1.nextToken());
                            addAndState(sst);

                        } else {
                            throw new ConditionException();
                        }
                    } else {
                        throw new ConditionException();
                    }
                }
            } else if (cs.contains("!!") && !cs.contains("&&")) {
                t0 = new StringTokenizer(cs, logicOperator);
                while (t0.hasMoreTokens()) {
                    String ac = t0.nextToken();

                    Pattern p = Pattern.compile(CompositionCompiler.SIMP_COND);
                    Matcher m = p.matcher(ac);
                    if (m.matches()) {

                        if (ac.contains("!=")) {
                            t1 = new StringTokenizer(t0.nextToken(), "!=\"");
                            if (t1.countTokens() != 2) {
                                throw new ConditionException();
                            }
                            ServiceState sst = new ServiceState(t1.nextToken(), t1.nextToken());
                            addOrNotState(sst);

                        } else if (ac.contains("==")) {
                            t1 = new StringTokenizer(t0.nextToken(), "==\"");
                            if (t1.countTokens() != 2) {
                                throw new ConditionException();
                            }
                            ServiceState sst = new ServiceState(t1.nextToken(), t1.nextToken());
                            addOrState(sst);

                        } else {
                            throw new ConditionException();
                        }
                    } else {
                        throw new ConditionException();
                    }
                }
            } else {
                throw new ConditionException();
            }
        }
    }

    /**
     * Returns true if passed states make this condition true.
     *
     * @return true/false
     */
    public boolean isTrue(ServiceState ownerSS, ServiceState inputSS) {

        boolean checkAnd = getAndStates().isEmpty()
                || checkANDCondition(getAndStates(), ownerSS, inputSS);
        boolean checkAndNot = getAndNotStates().isEmpty()
                || !checkANDCondition(getAndNotStates(), ownerSS, inputSS);
        boolean checkOr = getOrStates().isEmpty()
                || checkORCondition(getOrStates(), ownerSS, inputSS);
        boolean checkOrNot = getOrNotStates().isEmpty()
                || !checkORCondition(getOrNotStates(), ownerSS, inputSS);

        return checkAnd && checkAndNot && checkOr && checkOrNot;
    }

    private boolean checkANDCondition(Set<ServiceState> sc, ServiceState s1, ServiceState s2) {
        return sc.contains(s1) && sc.contains(s2);
    }

    private boolean checkORCondition(Set<ServiceState> sc, ServiceState s1, ServiceState s2) {
        return sc.contains(s1) || sc.contains(s2);
    }

    @Override
    public String toString() {
        return "Condition{"
                + "serviceName='" + serviceName + '\''
                + ", andStates=" + andStates
                + ", andNotStates=" + andNotStates
                + ", orStates=" + orStates
                + ", orNotStates=" + orNotStates
                + '}';
    }


    public static class ConditionException extends ErsapException {

        ConditionException() {
            super("composition syntax error: malformed conditional statement");
        }
    }
}
