/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.coda.xmsg.core.xMsgConstants;

/**
 * Unique identifier of a ERSAP container.
 * <p>
 * The canonical name for a container has the following structure:
 * <pre>
 * {@literal <dpe_canonical_name>:<container_name>}
 * </pre>
 * Example:
 * <pre>
 * {@literal 10.1.1.1_java:master}
 * </pre>
 *
 * @see DpeName
 */
public class ContainerName implements ErsapName {

    private final DpeName dpe;
    private final String canonicalName;
    private final String name;

    /**
     * Identify a container with host and language of its DPE and name.
     *
     * @param host the host address of the DPE
     * @param lang the language of the DPE
     * @param name the name of the container
     */
    public ContainerName(String host, ErsapLang lang, String name) {
        this(new DpeName(host, lang), name);
    }

    /**
     * Identify a container with its DPE and name.
     *
     * @param dpe the DPE of the container
     * @param name the name of the container
     */
    public ContainerName(DpeName dpe, String name) {
        this.dpe = dpe;
        this.canonicalName = dpe.canonicalName() + xMsgConstants.TOPIC_SEP + name;
        this.name = name;
    }

    /**
     * Identify a container with its canonical name.
     *
     * @param canonicalName the canonical name of the container
     */
    public ContainerName(String canonicalName) {
        if (!ErsapUtil.isContainerName(canonicalName)) {
            throw new IllegalArgumentException("Invalid container name: " + canonicalName);
        }
        this.dpe = new DpeName(ErsapUtil.getDpeName(canonicalName));
        this.name = ErsapUtil.getContainerName(canonicalName);
        this.canonicalName = canonicalName;
    }

    @Override
    public String canonicalName() {
        return canonicalName;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ErsapAddress address() {
        return dpe.address();
    }

    @Override
    public ErsapLang language() {
        return dpe.language();
    }

    /**
     * Gets the canonical name of the DPE for this container.
     *
     * @return the DPE name
     */
    public DpeName dpe() {
        return dpe;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + canonicalName.hashCode();
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
        if (getClass() != obj.getClass()) {
            return false;
        }
        ContainerName other = (ContainerName) obj;
        return canonicalName.equals(other.canonicalName);
    }

    @Override
    public String toString() {
        return canonicalName;
    }
}
