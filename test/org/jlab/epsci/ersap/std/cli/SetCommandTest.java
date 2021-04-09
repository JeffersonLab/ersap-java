/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import org.jlab.epsci.ersap.util.EnvUtils;
import org.jline.terminal.Terminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SetCommandTest {

    private static final Terminal TERMINAL = mock(Terminal.class);

    private Config config;
    private SetCommand command;

    @BeforeEach
    public void setup() throws Exception {
        config = new Config();
        command = new SetCommand(new Context(TERMINAL, config));
    }

    @Test
    public void testDefaultSession() throws Exception {
        assertThat(config.getString(Config.SESSION), is(EnvUtils.userName()));
    }

    @Test
    public void testSetSession() throws Exception {
        command.execute(new String[]{"session", "trevor"});

        assertThat(config.getString(Config.SESSION), is("trevor"));
    }

    @Test
    public void testSetServicesFile() throws Exception {
        String userFile = createTempFile("yaml");

        command.execute(new String[]{"servicesFile", userFile});

        assertThat(config.getString(Config.SERVICES_FILE), is(userFile));
    }

    @Test
    public void testSetFileList() throws Exception {
        String userFile = createTempFile("fileList");

        command.execute(new String[]{"fileList", userFile});

        assertThat(config.getString(Config.FILES_LIST), is(userFile));
    }

    @Test
    public void testSetMaxThreads() throws Exception {
        command.execute(new String[]{"threads", "5"});

        assertThat(config.getInt(Config.MAX_THREADS), is(5));
    }

    @Test
    public void testSetInputDir() throws Exception {
        String userDir = createTempDir("input");

        command.execute(new String[]{"inputDir", userDir});

        assertThat(config.getString(Config.INPUT_DIR), is(userDir));
    }

    @Test
    public void testSetOutputDir() throws Exception {
        String userDir = createTempDir("output");

        command.execute(new String[]{"outputDir", userDir});

        assertThat(config.getString(Config.OUTPUT_DIR), is(userDir));
    }

    private static String createTempDir(String prefix) throws IOException {
        Path tmpDir = Files.createTempDirectory(prefix);
        tmpDir.toFile().deleteOnExit();
        return tmpDir.toString();
    }

    private static String createTempFile(String prefix) throws IOException {
        File tmpFile = File.createTempFile(prefix, "");
        tmpFile.deleteOnExit();
        return tmpFile.toString();
    }
}
