/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import org.jline.reader.Completer;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;

import java.io.PrintWriter;

/**
 * Default class for new shell commands.
 * The name and a brief description of the command are required.
 * The command can access to the virtual terminal used by the shell, if needed.
 */
public abstract class AbstractCommand implements Command {

    /**
     *  The name of the command.
     */
    protected final String name;

    /**
     * The description of the command.
     */
    protected final String description;

    /**
     * The virtual terminal used by the shell.
     */
    protected final Terminal terminal;

    /**
     * The configuration of the shell session.
     */
    protected final Config config;

    /**
     * The text-output stream of the terminal.
     */
    protected final PrintWriter writer;


    static CommandFactory wrap(String name, String description, String... command) {
        return session -> new AbstractCommand(session, name, description) {
            @Override
            public int execute(String[] args) {
                return CommandUtils.runProcess(command);
            }
        };
    }


    /**
     * Creates a new command.
     *
     * @param context the context of the shell session
     * @param name the name of the command
     * @param description the description of the command
     */
    protected AbstractCommand(Context context, String name, String description) {
        this.name = name;
        this.description = description;
        this.terminal = context.terminal();
        this.config = context.config();
        this.writer = terminal.writer();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Completer getCompleter() {
        return new ArgumentCompleter(new StringsCompleter(name), NullCompleter.INSTANCE);
    }

    @Override
    public void printHelp(PrintWriter printer) {
        printer.println(getDescription());
    }

    @Override
    public void close() throws Exception {
        // nothing
    }
}
