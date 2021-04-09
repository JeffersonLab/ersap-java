/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import java.util.Arrays;
import java.util.Map;

import org.jlab.epsci.ersap.util.EnvUtils;
import org.jline.reader.EndOfFileException;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.Terminal.Signal;
import org.jline.terminal.Terminal.SignalHandler;

class CommandRunner {

    private final Terminal terminal;
    private final Parser parser;
    private final Map<String, Command> commands;

    CommandRunner(Terminal terminal, Map<String, Command> commands) {
        this.terminal = terminal;
        this.parser = new DefaultParser();
        this.commands = commands;
    }

    public int execute(String line) {
        String[] shellArgs = parseLine(line);
        if (shellArgs == null) {
            return Command.EXIT_ERROR;
        }
        if (shellArgs.length == 0) {
            return Command.EXIT_SUCCESS;
        }
        String commandName = shellArgs[0];
        Command command = commands.get(commandName);
        if (command == null) {
            if ("exit".equals(commandName)) {
                throw new EndOfFileException();
            }
            terminal.writer().println("Invalid command");
            return Command.EXIT_ERROR;
        }
        Thread execThread = Thread.currentThread();
        SignalHandler prevIntHandler = terminal.handle(Signal.INT, s -> {
            execThread.interrupt();
        });
        try {
            String[] cmdArgs = Arrays.copyOfRange(shellArgs, 1, shellArgs.length);
            return command.execute(cmdArgs);
        } finally {
            terminal.handle(Signal.INT, prevIntHandler);
            terminal.writer().flush();
        }
    }

    private String[] parseLine(String line) {
        try {
            String cmd = EnvUtils.expandEnvironment(line, System.getenv()).trim();
            return parser.parse(cmd, cmd.length() + 1)
                         .words()
                         .toArray(new String[0]);
        } catch (IllegalArgumentException e) {
            terminal.writer().println(e.getMessage());
            return null;
        }
    }

    Parser getParser() {
        return parser;
    }
}
