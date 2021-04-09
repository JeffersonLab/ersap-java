/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util;

import java.util.Objects;

public final class ArgUtils {

    private ArgUtils() { }

    public static String requireNonEmpty(String arg, String desc) {
        if (arg == null) {
            throw new NullPointerException("null " + desc);
        }
        if (arg.isEmpty()) {
            throw new IllegalArgumentException("empty " + desc);
        }
        return arg;
    }

    public static <T> T requireNonNull(T obj, String desc) {
        return Objects.requireNonNull(obj, "null " + desc);
    }
}
