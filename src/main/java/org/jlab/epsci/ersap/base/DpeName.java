/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.epsci.ersap.base.core.ErsapConstants;

/**
 * The name of a ERSAP DPE.
 * <p>
 * The canonical name for a DPE has the following structure:
 * <pre>
 * {@literal <host_address>_<language>}
 * {@literal <host_address>%<port>_<language>}
 * </pre>
 * Example:
 * <pre>
 * {@literal 10.1.1.1_java}
 * </pre>
 */
public class DpeName implements ErsapName {

    private final ErsapAddress address;
    private final ErsapLang language;
    private final String name;

    /**
     * Identify a DPE with host and language.
     * The default port will be used.
     *
     * @param host the host address where the DPE is running
     * @param lang the language of the DPE
     */
    public DpeName(String host, ErsapLang lang) {
        address = new ErsapAddress(host, ErsapUtil.getDefaultPort(lang.toString()));
        language = lang;
        name = host + ErsapConstants.LANG_SEP + lang;
    }

    /**
     * Identify a DPE with host, port and language.
     *
     * @param host the host address where the DPE is running
     * @param port the port used by the DPE
     * @param lang the language of the DPE
     */
    public DpeName(String host, int port, ErsapLang lang) {
        address = new ErsapAddress(host, port);
        language = lang;
        name = host + ErsapConstants.PORT_SEP
             + port + ErsapConstants.LANG_SEP
             + lang;
    }

    /**
     * Identify a DPE with a canonical name.
     *
     * @param canonicalName the canonical name of the DPE
     */
    public DpeName(String canonicalName) {
        if (!ErsapUtil.isCanonicalName(canonicalName)) {
            throw new IllegalArgumentException("Invalid canonical name: " + canonicalName);
        }
        String host = ErsapUtil.getDpeHost(canonicalName);
        int port = ErsapUtil.getDpePort(canonicalName);
        this.address = new ErsapAddress(host, port);
        this.language = ErsapLang.fromString(ErsapUtil.getDpeLang(canonicalName));
        this.name = canonicalName;
    }

    @Override
    public String canonicalName() {
        return name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public ErsapLang language() {
        return language;
    }

    @Override
    public ErsapAddress address() {
        return address;
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
        if (getClass() != obj.getClass()) {
            return false;
        }
        DpeName other = (DpeName) obj;
        return name.equals(other.name);
    }

    @Override
    public String toString() {
        return name;
    }
}
