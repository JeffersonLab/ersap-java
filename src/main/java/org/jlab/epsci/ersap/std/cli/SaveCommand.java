/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.util.FileUtils;
import org.jline.reader.Completer;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.FileNameCompleter;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.reader.impl.completer.StringsCompleter;

class SaveCommand extends AbstractCommand {

    SaveCommand(Context context) {
        super(context, "save", "Export configuration to file");
    }

    @Override
    public int execute(String[] args) {
        if (args.length < 1) {
            writer.println("Error: missing filename argument");
            return EXIT_ERROR;
        }
        Path path = FileUtils.expandHome(args[0]);
        if (Files.exists(path)) {
            boolean overwrite = scanAnswer();
            if (!overwrite) {
                writer.println("The config was not saved");
                return EXIT_SUCCESS;
            }
        }
        return writeFile(path);
    }

    private boolean scanAnswer() {
        @SuppressWarnings("resource")
        Scanner scan = new Scanner(System.in);
        while (true) {
            String answer;
            System.out.print("The file already exists. Do you want to overwrite it? (y/N): ");
            answer = scan.nextLine();
            switch (answer) {
                case "y":
                case "Y":
                case "yes":
                case "Yes":
                    return true;
                case "n":
                case "N":
                case "no":
                case "No":
                case "":
                    return false;
                default:
                    System.out.println("Invalid answer.");
            }
        }
    }

    private int writeFile(Path path) {
        try (PrintWriter printer = FileUtils.openOutputTextFile(path, false)) {
            for (ConfigVariable variable : config.getVariables()) {
                if (variable.hasValue()) {
                    printer.printf("set %s %s%n", variable.getName(), variable.getValue());
                }
            }
        } catch (IOException e) {
            writer.printf("Error: could not write file: %s: %s%n", path, e.getMessage());
            return EXIT_ERROR;
        }
        writer.println("Config saved in " + path);
        return EXIT_SUCCESS;
    }

    @Override
    public Completer getCompleter() {
        Completer command = new StringsCompleter(getName());
        Completer fileCompleter = new FileNameCompleter();
        return new ArgumentCompleter(command, fileCompleter, NullCompleter.INSTANCE);
    }

    @Override
    public void printHelp(PrintWriter printer) {
        printer.printf("%n  %s <file_path>%n", name);
        printer.printf("%s.%n", ErsapUtil.splitIntoLines(description, "    ", 72));
    }
}
