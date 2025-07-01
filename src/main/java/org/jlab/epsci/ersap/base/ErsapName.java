/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

/**
 * Identifier of a ERSAP component.
 */
public interface ErsapName {

    /**
     * Returns the canonical name of this ERSAP component.
     *
     * @return a string with the canonical name
     */
    String canonicalName();

    /**
     * Returns the specific name of this ERSAP component.
     * This is the last part of the canonical name.
     *
     * @return a string with the name part of the canonical name
     */
    String name();

    /**
     * Returns the language of this ERSAP component.
     *
     * @return the language
     */
    ErsapLang language();

    /**
     * Returns the address of the proxy used by this ERSAP component.
     *
     * @return the address where the component is listening requests
     */
    ErsapAddress address();
}
