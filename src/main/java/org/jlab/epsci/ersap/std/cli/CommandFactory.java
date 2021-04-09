/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

/**
 * A factory for new shell builtin commands.
 */
@FunctionalInterface
public interface CommandFactory {

    /**
     * Creates a new builtin command.
     *
     * @param context the shell session
     * @return the builtin command
     */
    Command create(Context context);
}
