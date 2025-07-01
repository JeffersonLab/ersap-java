/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.util.FileUtils;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class OrchestratorPaths {

    static final String INPUT_DIR = FileUtils.ersapPath("data", "input").toString();
    static final String OUTPUT_DIR = FileUtils.ersapPath("data", "output").toString();
    static final String STAGE_DIR = File.separator + "scratch";
    static final String OUTPUT_FILE_PREFIX = "out_";

    final List<WorkerFile> allFiles;

    final Path inputDir;
    final Path outputDir;
    final Path stageDir;
    final String prefix;

    static class Builder {

        private List<WorkerFile> allFiles;
        private Stream<String> afStream = null;


        private Path inputDir = Paths.get(INPUT_DIR);
        private Path outputDir = Paths.get(OUTPUT_DIR);
        private Path stageDir = Paths.get(STAGE_DIR);
        private String prefix = OUTPUT_FILE_PREFIX;


        Builder(String inputFile, String outputFile) {
            Path inputPath = Paths.get(inputFile);
            Path outputPath = Paths.get(outputFile);

            String inputName = FileUtils.getFileName(inputPath).toString();
            String outputName = FileUtils.getFileName(outputPath).toString();

            this.allFiles = Arrays.asList(new WorkerFile(inputName, outputName));
            this.inputDir = FileUtils.getParent(inputPath).toAbsolutePath().normalize();
            this.outputDir = FileUtils.getParent(outputPath).toAbsolutePath().normalize();
        }

        Builder(List<String> inputFiles) {
            afStream = inputFiles.stream();
            this.allFiles = inputFiles.stream()
                    .peek(f -> checkValidFileName(f))
                    .map(f -> new WorkerFile(f, prefix + f))
                    .collect(Collectors.toList());
        }

        Builder withInputDir(String inputDir) {
            this.inputDir = Paths.get(inputDir).toAbsolutePath().normalize();
            return this;
        }

        Builder withOutputDir(String outputDir) {
            this.outputDir = Paths.get(outputDir).toAbsolutePath().normalize();
            return this;
        }

        Builder withOutputFilePrefix(String prefix) {
            this.prefix = prefix;
            this.allFiles = afStream
                    .peek(f -> checkValidFileName(f))
                    .map(f -> new WorkerFile(f, prefix + f))
                    .collect(Collectors.toList());
            return this;
        }

        Builder withStageDir(String stageDir) {
            this.stageDir = Paths.get(stageDir).toAbsolutePath().normalize();
            return this;
        }

        OrchestratorPaths build() {
            return new OrchestratorPaths(this);
        }

        private static void checkValidFileName(String file) {
            try {
                if (Paths.get(file).getParent() != null) {
                    throw new OrchestratorConfigException("Input file cannot be a path: " + file);
                }
            } catch (InvalidPathException e) {
                throw new OrchestratorConfigException(e);
            }
        }
    }


    protected OrchestratorPaths(Builder builder) {
        this.allFiles = builder.allFiles;
        this.inputDir = builder.inputDir;
        this.outputDir = builder.outputDir;
        this.stageDir = builder.stageDir;
        this.prefix = builder.prefix;
    }

    Path inputFilePath(WorkerFile recFile) {
        return inputDir.resolve(recFile.inputName);
    }

    Path outputFilePath(WorkerFile recFile) {
        return outputDir.resolve(recFile.outputName);
    }

    Path stageInputFilePath(WorkerFile recFile) {
        return stageDir.resolve(recFile.inputName);
    }

    Path stageOutputFilePath(WorkerFile recFile) {
        return stageDir.resolve(recFile.outputName);
    }

    int numFiles() {
        return allFiles.size();
    }
}
