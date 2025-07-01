/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

class EditCommand extends BaseCommand {

    EditCommand(Context context) {
        super(context, "edit", "Edit data processing conditions");

        addArgument("services", "Edit services composition.",
            c -> Paths.get(c.getString(Config.SERVICES_FILE)));
        addArgument("files", "Edit input file list.",
            c -> Paths.get(c.getString(Config.FILES_LIST)));
    }

    void addArgument(String name, String description, Function<Config, Path> fileArg) {
        addSubCommand(newArgument(name, description, fileArg));
    }

    static CommandFactory newArgument(String name,
                                      String description,
                                      Function<Config, Path> fileArg) {
        return session -> new AbstractCommand(session, name, description) {
            @Override
            public int execute(String[] args) {
                return CommandUtils.editFile(fileArg.apply(session.config()));
            }
        };
    }
}
