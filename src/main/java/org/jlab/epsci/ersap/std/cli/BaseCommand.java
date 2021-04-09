/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;

/**
 * A command that groups a list of subcommands that can be executed separately.
 */
public abstract class BaseCommand extends AbstractCommand {

    /**
     * The supported subcommands.
     */
    protected Map<String, Command> subCommands;

    /**
     * Creates a new base command to group subcommands.
     *
     * @param context the context of the shell session
     * @param name the name of the base command
     * @param description the description of the base command
     */
    protected BaseCommand(Context context, String name, String description) {
        super(context, name, description);
        this.subCommands = new LinkedHashMap<>();
    }

    @Override
    public int execute(String[] args) {
        if (args.length < 1) {
            writer.println("Error: missing argument(s).");
            return EXIT_ERROR;
        }
        String subCommandName = args[0];
        Command subCommand = subCommands.get(subCommandName);
        if (subCommand == null) {
            writer.println("Error: unknown argument " + subCommandName);
            return EXIT_ERROR;
        }
        try {
            String[] cmdArgs = Arrays.copyOfRange(args, 1, args.length);
            return subCommand.execute(cmdArgs);
        } catch (IllegalArgumentException e) {
            writer.println("Error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return EXIT_ERROR;
    }

    /**
     * Creates and adds a new subcommand to the list of subcommands supported by
     * this command.
     *
     * @param name the name of the subcommand
     * @param action the action to run when the the subcommand is executed
     * @param description the description of the subcommand
     * @throws IllegalArgumentException if a subcommand of the given name already exists
     */
    protected void addSubCommand(String name,
                                 Function<String[], Integer> action,
                                 String description) {
        addSubCommand(session -> new AbstractCommand(session, name, description) {
            @Override
            public int execute(String[] args) {
                return action.apply(args);
            }
        });
    }

    /**
     * Adds the given subcommand to the list of subcommands supported by this
     * command.
     *
     * @param factory the factory for a new subcommand of this command
     * @throws IllegalArgumentException if a subcommand of the given name already exists
     */
    protected void addSubCommand(CommandFactory factory) {
        Command subCmd = factory.create(new Context(terminal, config));
        String subName = subCmd.getName();
        Command prev = subCommands.putIfAbsent(subName, subCmd);
        if (prev != null) {
            String msg = String.format("a subcommand '%s %s' already exists", name, subName);
            throw new IllegalArgumentException(msg);
        }
    }

    @Override
    public Completer getCompleter() {
        List<Completer> completers = subCommands.values()
                .stream()
                .map(this::getCompleter)
                .collect(Collectors.toList());
        return new AggregateCompleter(completers);
    }

    private Completer getCompleter(Command arg) {
        List<Completer> allCompleters = new ArrayList<>();
        allCompleters.add(new StringsCompleter(name));

        Completer subCompleter = arg.getCompleter();
        if (subCompleter instanceof ArgumentCompleter) {
            ArgumentCompleter argCompleter = (ArgumentCompleter) subCompleter;
            allCompleters.addAll(argCompleter.getCompleters());
        } else {
            allCompleters.add(subCompleter);
            allCompleters.add(NullCompleter.INSTANCE);
        }

        return new ArgumentCompleter(allCompleters);
    }

    @Override
    public void printHelp(PrintWriter printer) {
        for (Command cmd: subCommands.values()) {
            printer.printf("%n  %s %s%n", name, cmd.getName());
            printer.printf("%s%n", ErsapUtil.splitIntoLines(cmd.getDescription(), "    ", 72));
        }
    }

    @Override
    public void close() throws Exception {
        for (Command subCommand : subCommands.values()) {
            try {
                subCommand.close();
            } catch (Exception e) {
                writer.println(e.getMessage());
            }
        }
    }
}
