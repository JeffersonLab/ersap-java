/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util;

import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionSpec;

public final class OptUtils {

    private OptUtils() { }

    public static <V> String optionHelp(String name, String... help) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < help.length; i++) {
            sb.append(String.format("  %-25s  %s%n", i == 0 ? name : "", help[i]));
        }
        return sb.toString();
    }

    public static <V> String optionHelp(OptionSpec<V> spec, String arg, String... help) {
        String lhs = optionName(spec, arg);
        return optionHelp(lhs, help);
    }

    public static <V> String optionName(OptionSpec<V> spec, String arg) {
        StringBuilder sb = new StringBuilder();
        String name = spec.options().get(0);
        sb.append("-");
        if (name.length() > 1) {
            sb.append("-");
        }
        sb.append(name);
        if (arg != null) {
            sb.append(" <").append(arg).append(">");
        }
        return sb.toString();
    }

    public static <V> String getDefault(OptionSpec<V> stageDir) {
        ArgumentAcceptingOptionSpec<V> spec = (ArgumentAcceptingOptionSpec<V>) stageDir;
        return "(default: " + spec.defaultValues().get(0) + ")";
    }
}
