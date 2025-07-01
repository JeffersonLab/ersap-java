/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys.ccc;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Correlates ERSAP condition with the set of routing statements.
 *
 *
 *
 *    2 15
 */
class Instruction {

    // The name of the service that this instruction is relevant to.
    private String serviceName;

    // Conditions of a composition
    private Condition ifCondition;
    private Set<Statement> ifCondStatements = new HashSet<>();

    private Condition elseifCondition;
    private Set<Statement> elseifCondStatements = new HashSet<>();

    private Set<Statement> elseCondStatements = new HashSet<>();

    private Set<Statement> unCondStatements = new HashSet<>();

    Instruction(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public Condition getIfCondition() {
        return ifCondition;
    }

    public void setIfCondition(Condition ifCondition) {
        this.ifCondition = ifCondition;
    }

    public Set<Statement> getIfCondStatements() {
        return ifCondStatements;
    }

    public void addIfCondStatement(Statement ifCondstatement) {
        this.ifCondStatements.add(ifCondstatement);
    }

    public Condition getElseifCondition() {
        return elseifCondition;
    }

    public void setElseifCondition(Condition elseifCondition) {
        this.elseifCondition = elseifCondition;
    }

    public Set<Statement> getElseifCondStatements() {
        return elseifCondStatements;
    }

    public void addElseifCondStatement(Statement elseifCondstatement) {
        this.elseifCondStatements.add(elseifCondstatement);
    }

    public Set<Statement> getElseCondStatements() {
        return elseCondStatements;
    }

    public void addElseCondStatement(Statement elseCondstatement) {
        this.elseCondStatements.add(elseCondstatement);
    }

    public Set<Statement> getUnCondStatements() {
        return unCondStatements;
    }

    public void setUnCondStatements(Set<Statement> unCondStatements) {
        this.unCondStatements = unCondStatements;
    }

    public void addUnCondStatement(Statement unCondstatement) {
        this.unCondStatements.add(unCondstatement);
    }

    @Override
    public String toString() {
        return "Instruction{"
                + "ifCondition=" + ifCondition
                + ", ifCondStatements=" + ifCondStatements
                + ", elseifCondition=" + elseifCondition
                + ", elseifCondStatements=" + elseifCondStatements
                + ", elseCondStatements=" + elseCondStatements
                + ", unCondStatements=" + unCondStatements
                + ", serviceName='" + serviceName + '\''
                + '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Instruction)) {
            return false;
        }

        Instruction other = (Instruction) obj;

        if (!serviceName.equals(other.serviceName)) {
            return false;
        }
        if (!Objects.equals(ifCondition, other.ifCondition)) {
            return false;
        }
        if (!ifCondStatements.equals(other.ifCondStatements)) {
            return false;
        }
        if (!Objects.equals(elseifCondition, other.elseifCondition)) {
            return false;
        }
        if (!elseifCondStatements.equals(other.elseifCondStatements)) {
            return false;
        }
        if (!elseCondStatements.equals(other.elseCondStatements)) {
            return false;
        }
        if (!unCondStatements.equals(other.unCondStatements)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = serviceName.hashCode();
        result = 31 * result + (ifCondition != null ? ifCondition.hashCode() : 0);
        result = 31 * result + ifCondStatements.hashCode();
        result = 31 * result + (elseifCondition != null ? elseifCondition.hashCode() : 0);
        result = 31 * result + elseifCondStatements.hashCode();
        result = 31 * result + elseCondStatements.hashCode();
        result = 31 * result + unCondStatements.hashCode();
        return result;
    }
}
