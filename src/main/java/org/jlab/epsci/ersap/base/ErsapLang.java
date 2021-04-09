/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

/**
 * The supported languages for ERSAP components.
 */
public enum ErsapLang {

    /**
     * Identifies components written in the Java language.
     */
    JAVA("java"),

    /**
     * Identifies components written in the Python language.
     */
    PYTHON("python"),

    /**
     * Identifies components written in the C++ language.
     */
    CPP("cpp");

    private final String name;

    ErsapLang(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Get the enum value for the given language string.
     *
     * @param lang a supported language name (java, cpp, python)
     * @return the enum value for the language
     */
    public static ErsapLang fromString(String lang) {
        return ErsapLang.valueOf(lang.toUpperCase());
    }
}
