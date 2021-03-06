/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

import org.jlab.epsci.ersap.engine.EngineData;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.jlab.epsci.ersap.engine.EngineStatus;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class DataManagerTest {

    private final String testFileName = "collider.txt";
    private final String testFilePath = getClass().getResource("/collider.txt").getPath();

    private DataManager dm;

    @BeforeEach
    public void setUp() {
        dm = new DataManager();
    }


    @Test
    public void setDefaultPaths() throws Exception {
        dm = new DataManager("/ersap/");

        assertPaths("/ersap/data/input", "/ersap/data/output", "/scratch");
    }


    @Test
    public void setAllPaths() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", "/mnt/exp/in");
            data.put("output_path", "/mnt/exp/out");
            data.put("stage_path", "/tmp/files");
        });

        dm.configure(config);

        assertPaths("/mnt/exp/in", "/mnt/exp/out", "/tmp/files");
    }


    @Test
    public void configInputOutputPaths() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", "/mnt/exp/in");
            data.put("output_path", "/mnt/exp/out");
        });

        dm.configure(config);

        assertPaths("/mnt/exp/in", "/mnt/exp/out", "/scratch");
    }


    private void assertPaths(String input, String output, String stage) {
        JSONObject result = dm.getConfiguration();

        assertThat(result.getString("input_path"), is(input));
        assertThat(result.getString("output_path"), is(output));
        assertThat(result.getString("stage_path"), is(stage));
    }


    @Test
    public void configReturnsErrorOnEmptyInputPath() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", "");
            data.put("output_path", "/mnt/exp/out");
        });

        assertErrorOnConfig(config, "empty input");
    }


    @Test
    public void configReturnsErrorOnEmptyOutputPath() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", "/mnt/exp/out");
            data.put("output_path", "");
        });

        assertErrorOnConfig(config, "empty output");
    }


    @Test
    public void configReturnsErrorOnEmptyStagePath() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", "/mnt/exp/in");
            data.put("output_path", "/mnt/exp/out");
            data.put("stage_path", "");
        });

        assertErrorOnConfig(config, "empty stage");
    }


    @Tag("integration")
    @Test
    public void configReturnsErrorWhenIntputPathExistsAndNotDirectory() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", tempFile());
            data.put("output_path", "/mnt/exp/out");
        });

        assertErrorOnConfig(config, "not a directory");
    }


    @Tag("integration")
    @Test
    public void configReturnsErrorWhenOutputPathExistsAndNotDirectory() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", "/mnt/exp/in");
            data.put("output_path", tempFile());
        });

        assertErrorOnConfig(config, "not a directory");
    }


    @Tag("integration")
    @Test
    public void configReturnsErrorWhenStagePathExistsAndNotDirectory() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", "/mnt/exp/in");
            data.put("output_path", "/mnt/exp/out");
            data.put("stage_path", tempFile());
        });

        assertErrorOnConfig(config, "not a directory");
    }


    private static String tempFile() {
        try {
            Path temp = Files.createTempFile("notdir", null);
            temp.toFile().deleteOnExit();
            return temp.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    @Test
    public void configReturnsErrorOnMissingPath() throws Exception {
        EngineData config = createJsonRequest(data -> {
            data.put("input_path", "/mnt/exp/in");
        });

        assertErrorOnConfig(config, "invalid request");
    }


    @Test
    public void configReturnsErrorOnMissingMimeType() throws Exception {
        EngineData config = new EngineData();
        config.setData("text/string", "bad config");

        assertErrorOnConfig(config, "wrong mimetype: text/string");
    }


    @Tag("integration")
    @Test
    public void executeCreatesDirectoryBeforeStagingInputFile() throws Exception {
        TestPaths paths = setTestDirectories();
        Files.delete(paths.stageDir);

        assertThat("Stage directory exists", paths.stageDir.toFile().exists(), is(false));

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "stage_input");
            data.put("file", paths.inputFile.getFileName().toString());
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Stage directory exists", paths.stageDir.toFile().exists(), is(true));
    }


    @Tag("integration")
    @Test
    public void executeStagesInputFile() throws Exception {
        TestPaths paths = setTestDirectories();

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "stage_input");
            data.put("file", paths.inputFile.getFileName().toString());
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Staged input exists", paths.stagedInputFile.toFile().exists(), is(true));
    }


    @Tag("integration")
    @Test
    public void executeStagesInputFileIntoExistingSymlinkDirectory() throws Exception {
        TestPaths paths = setTestDirectories(p -> {
            p.stageDir = getSymlink(p.stageDir);
            p.stagedInputFile = Paths.get(p.stageDir.toString(),
                                          p.stagedInputFile.getFileName().toString());
        });

        assertThat("Stage directory is link", Files.isSymbolicLink(paths.stageDir), is(true));

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "stage_input");
            data.put("file", paths.inputFile.getFileName().toString());
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Staged input exists", paths.stagedInputFile.toFile().exists(), is(true));
    }


    @Tag("integration")
    @Test
    public void executeRemovesStagedInputFile() throws Exception {
        TestPaths paths = setTestDirectories();
        Files.copy(paths.inputFile, paths.stagedInputFile);
        assertThat("Staged input exists", paths.stagedInputFile.toFile().exists(), is(true));

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "remove_input");
            data.put("file", paths.inputFile.getFileName().toString());
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Staged input exists", paths.stagedInputFile.toFile().exists(), is(false));
    }


    @Tag("integration")
    @Test
    public void executeCreatesDirectoryBeforeSavingOutputFile() throws Exception {
        TestPaths paths = setTestDirectories();
        Files.copy(paths.inputFile, paths.stagedOutputFile);
        Files.delete(paths.outputDir);

        assertThat("Staged output exists", paths.stagedOutputFile.toFile().exists(), is(true));
        assertThat("Output directory exists", paths.outputDir.toFile().exists(), is(false));

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "save_output");
            data.put("file", paths.inputFile.getFileName().toString());
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Output directory exists", paths.outputDir.toFile().exists(), is(true));
    }


    @Tag("integration")
    @Test
    public void executeSavesOutputFile() throws Exception {
        TestPaths paths = setTestDirectories();
        Files.copy(paths.inputFile, paths.stagedOutputFile);

        assertThat("Staged output exists", paths.stagedOutputFile.toFile().exists(), is(true));
        assertThat("Saved output exists", paths.outputFile.toFile().exists(), is(false));

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "save_output");
            data.put("file", paths.inputFile.getFileName().toString());
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Staged output exists", paths.stagedOutputFile.toFile().exists(), is(false));
        assertThat("Saved output exists", paths.outputFile.toFile().exists(), is(true));
    }


    @Tag("integration")
    @Test
    public void executeSavesOutputFileIntoExistingSymlinkDirectory() throws Exception {
        TestPaths paths = setTestDirectories(p -> {
            p.outputDir = getSymlink(p.outputDir);
            p.outputFile = Paths.get(p.outputDir.toString(),
                                     p.outputFile.getFileName().toString());
        });

        Files.copy(paths.inputFile, paths.stagedOutputFile);

        assertThat("Output directory is link", Files.isSymbolicLink(paths.outputDir), is(true));

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "save_output");
            data.put("file", paths.inputFile.getFileName().toString());
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Staged output exists", paths.stagedOutputFile.toFile().exists(), is(false));
        assertThat("Saved output exists", paths.outputFile.toFile().exists(), is(true));
    }


    @Tag("integration")
    @Test
    public void executeClearsStageDirectory() throws Exception {
        TestPaths paths = setTestDirectories();
        Files.copy(paths.inputFile, paths.stagedInputFile);

        assertThat("Staged input exists", paths.stagedInputFile.toFile().exists(), is(true));

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "clear_stage");
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Stage directory exists", paths.stageDir.toFile().exists(), is(false));
    }


    @Tag("integration")
    @Test
    public void executeClearsNonExistingStageDirectory() throws Exception {
        TestPaths paths = setTestDirectories();
        Files.delete(paths.stageDir);
        assertThat("Stage directory exists", paths.stageDir.toFile().exists(), is(false));

        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "clear_stage");
        });

        EngineData result = dm.execute(request);

        assertThat("Result is not an error", result.getStatus(), is(not(EngineStatus.ERROR)));
        assertThat("Stage directory exists", paths.stageDir.toFile().exists(), is(false));
    }


    private static class TestPaths {
        private Path inputDir;
        private Path outputDir;
        private Path stageDir;

        private Path inputFile;
        private Path outputFile;
        private Path stagedInputFile;
        private Path stagedOutputFile;
    }


    private TestPaths setTestDirectories() throws Exception {
        return setTestDirectories(paths -> { });
    }


    private TestPaths setTestDirectories(Consumer<TestPaths> editPaths) throws Exception {
        TestPaths paths = new TestPaths();

        paths.inputDir = Paths.get(testFilePath).getParent();
        paths.inputFile = Paths.get(testFilePath);

        paths.outputDir = Files.createTempDirectory("output");
        paths.outputDir.toFile().deleteOnExit();

        paths.outputFile = paths.outputDir.resolve("out_" + testFileName);
        paths.outputFile.toFile().deleteOnExit();

        paths.stageDir = Files.createTempDirectory("stage");
        paths.stageDir.toFile().deleteOnExit();

        paths.stagedInputFile = paths.stageDir.resolve(testFileName);
        paths.stagedOutputFile = paths.stageDir.resolve("out_" + testFileName);

        paths.stagedInputFile.toFile().deleteOnExit();
        paths.stagedOutputFile.toFile().deleteOnExit();

        editPaths.accept(paths);

        EngineData config = createJsonRequest(data -> {
            data.put("input_path", paths.inputDir.toString());
            data.put("output_path", paths.outputDir.toString());
            data.put("stage_path", paths.stageDir.toString());
        });

        dm.configure(config);

        return paths;
    }


    private Path getSymlink(Path target) {
        try {
            Path baseDir = Files.createTempDirectory("base");
            baseDir.toFile().deleteOnExit();

            Path link = Paths.get(baseDir.toString(), "link");
            link.toFile().deleteOnExit();

            return Files.createSymbolicLink(link, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    @Test
    public void executeStageInputFailureReturnsError() throws Exception {
        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "stage_input");
            data.put("file", "file.ev");
        });

        assertErrorOnExecute(request, "could not complete request");
    }


    @Tag("integration")
    @Test
    public void executeRemoveStagedInputFailureReturnsError() throws Exception {
        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "remove_input");
            data.put("file", "file.ev");
        });

        assertErrorOnExecute(request, "could not complete request");
    }


    @Tag("integration")
    @Test
    public void executeSavesOutputFailureReturnsError() throws Exception {
        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "save_output");
            data.put("file", "file.ev");
        });

        assertErrorOnExecute(request, "could not complete request");
    }


    @Test
    public void executeReturnsErrorOnMissingProperty() throws Exception {
        EngineData request = createJsonRequest(data -> {
            data.put("command", "bad_action");
            data.put("file", "/mnt/exp/in/file.ev");
        });

        assertErrorOnExecute(request, "invalid request");
    }


    @Test
    public void executeReturnsErrorOnWrongAction() throws Exception {
        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "bad_action");
            data.put("file", "file.ev");
        });

        assertErrorOnExecute(request, "invalid action value: bad_action");
    }


    @Test
    public void executeReturnsErrorOnMissingMimeType() throws Exception {
        EngineData request = new EngineData();
        request.setData("text/number", 42);

        assertErrorOnExecute(request, "wrong mimetype: text/number");
    }


    @Test
    public void executeReturnsErrorOnInputFileWithFullPath() throws Exception {
        EngineData request = createJsonRequest(data -> {
            data.put("type", "exec");
            data.put("action", "stage_input");
            data.put("file", "/mnt/exp/in/file.ev");
        });

        assertErrorOnExecute(request, "invalid input file name");
    }


    private EngineData createJsonRequest(Consumer<JSONObject> builder) {
        JSONObject data = new JSONObject();
        builder.accept(data);
        EngineData request = new EngineData();
        request.setData(EngineDataType.JSON.mimeType(), data.toString());
        return request;
    }


    private void assertErrorOnConfig(EngineData config, String msg) {
        EngineData result = dm.configure(config);

        assertThat("Result is an error", result.getStatus(), is(EngineStatus.ERROR));
        assertThat("Description matches", result.getDescription(), containsString(msg));
    }


    private void assertErrorOnExecute(EngineData request, String msg) {
        EngineData result = dm.execute(request);

        assertThat("Result is an error", result.getStatus(), is(EngineStatus.ERROR));
        assertThat("Description matches", result.getDescription(), containsString(msg));
    }
}
