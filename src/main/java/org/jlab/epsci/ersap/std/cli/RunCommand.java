/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.cli;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.base.ErsapLang;
import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.core.ErsapConstants;
import org.jlab.epsci.ersap.util.FileUtils;
import org.jlab.epsci.ersap.std.orchestrators.OrchestratorConfigException;
import org.jlab.epsci.ersap.std.orchestrators.OrchestratorConfigParser;

class RunCommand extends BaseCommand {

    RunCommand(Context context) {
        super(context, "run", "Start ERSAP data processing");
        addSubCommand(RunLocal::new);
    }

    private static class RunLocal extends AbstractCommand {

        private static final int LOWER_PORT = 7000;
        private static final int UPPER_PORT = 9000;
        private static final int STEP_PORTS = 20;

        private final Map<ErsapLang, DpeProcess> backgroundDpes;
        private final RunUtils runUtils;

        RunLocal(Context context) {
            super(context, "local", "Run ERSAP data processing on the local node.");
            this.backgroundDpes = new HashMap<>();
            this.runUtils = new RunUtils(config);
        }

        @Override
        public int execute(String[] args) {
            try {
                DpeName feDpe = startLocalDpes();
                int exitStatus = runOrchestrator(feDpe);
                if (Thread.interrupted()) {
                    destroyDpes();
                    Thread.currentThread().interrupt();
                }
                return exitStatus;
            } catch (OrchestratorConfigException e) {
                writer.println("Error: " + e.getMessage());
                return EXIT_ERROR;
            } catch (Exception e) {
                e.printStackTrace();
                return EXIT_ERROR;
            }
        }

        private int runOrchestrator(DpeName feName) {
            String[] cmd = orchestratorCmd(feName);
            Path logFile = runUtils.getLogFile(getHost(feName), "orch");
            return CommandUtils.runProcess(buildProcess(cmd, logFile));
        }

        private String[] orchestratorCmd(DpeName feName) {
            Path orchestrator = FileUtils.ersapPath("bin", "ersap-orchestrator");
            SystemCommandBuilder cmd = new SystemCommandBuilder(orchestrator);

            cmd.addOption("-F");
            cmd.addOption("-f", feName);

            cmd.addOption("-t", getThreads());
            if (config.hasValue(Config.REPORT_EVENTS)) {
                cmd.addOption("-r", config.getInt(Config.REPORT_EVENTS));
            }
            if (config.hasValue(Config.SKIP_EVENTS)) {
                cmd.addOption("-k", config.getInt(Config.SKIP_EVENTS));
            }
            if (config.hasValue(Config.MAX_EVENTS)) {
                cmd.addOption("-e", config.getInt(Config.MAX_EVENTS));
            }
            cmd.addOption("-i", config.getString(Config.INPUT_DIR));
            cmd.addOption("-o", config.getString(Config.OUTPUT_DIR));
            cmd.addOption("-z", config.getString(Config.OUT_FILE_PREFIX));

            cmd.addArgument(config.getString(Config.SERVICES_FILE));
            cmd.addArgument(config.getString(Config.FILES_LIST));

            return cmd.toArray();
        }

        private DpeName startLocalDpes() throws IOException {
            String configFile = config.getString(Config.SERVICES_FILE);
            OrchestratorConfigParser parser = new OrchestratorConfigParser(configFile);
            Set<ErsapLang> languages = parser.parseLanguages();

            if (checkDpes(languages)) {
                return backgroundDpes.get(ErsapLang.JAVA).name;
            }
            destroyDpes();

            DpeName feName = new DpeName(findHost(), findPort(), ErsapLang.JAVA);
            String javaDpe = FileUtils.ersapPath("bin", "j_dpe").toString();
            addBackgroundDpeProcess(feName, javaDpe,
                    "--host", getHost(feName),
                    "--port", getPort(feName),
                    "--session", runUtils.getSession());

            if (languages.contains(ErsapLang.CPP)) {
                int cppPort = feName.address().pubPort() + 10;
                DpeName cppName = new DpeName(feName.address().host(), cppPort, ErsapLang.CPP);
                String cppDpe = FileUtils.ersapPath("bin", "c_dpe").toString();
                addBackgroundDpeProcess(cppName, cppDpe,
                        "--host", getHost(cppName),
                        "--port", getPort(cppName),
                        "--fe-host", getHost(feName),
                        "--fe-port", getPort(feName));
            }

            if (languages.contains(ErsapLang.PYTHON)) {
                int pyPort = feName.address().pubPort() + 5;
                DpeName pyName = new DpeName(feName.address().host(), pyPort, ErsapLang.PYTHON);
                String pyDpe = FileUtils.ersapPath("bin", "p_dpe").toString();
                addBackgroundDpeProcess(pyName, pyDpe,
                        "--host", getHost(pyName),
                        "--port", getPort(pyName),
                        "--fe-host", getHost(feName),
                        "--fe-port", getPort(feName));
            }

            return feName;
        }

        private String findHost() {
            if (config.hasValue(Config.FRONTEND_HOST)) {
                return config.getString(Config.FRONTEND_HOST);
            }
            return ErsapUtil.localhost();
        }

        private int findPort() {
            if (config.hasValue(Config.FRONTEND_PORT)) {
                return config.getInt(Config.FRONTEND_PORT);
            }

            List<Integer> ports = IntStream.iterate(LOWER_PORT, n -> n + STEP_PORTS)
                                           .limit((UPPER_PORT - LOWER_PORT) / STEP_PORTS)
                                           .boxed()
                                           .collect(Collectors.toList());
            Collections.shuffle(ports);

            for (Integer port : ports) {
                int ctrlPort = port + 2;
                try (ServerSocket socket = new ServerSocket(ctrlPort)) {
                    socket.setReuseAddress(true);
                    return port;
                } catch (IOException e) {
                    continue;
                }
            }
            throw new IllegalStateException("Cannot find an available port");
        }

        private boolean checkDpes(Set<ErsapLang> languages) {
            return languages.equals(backgroundDpes.keySet())
                && languages.stream().allMatch(this::isDpeAlive);
        }

        private boolean isDpeAlive(ErsapLang lang) {
            DpeProcess dpe = backgroundDpes.get(lang);
            return dpe != null && dpe.process.isAlive();
        }

        private void addBackgroundDpeProcess(DpeName name, String... command)
                throws IOException {
            if (!backgroundDpes.containsKey(name.language())) {
                Path logFile = runUtils.getLogFile(name);
                ProcessBuilder builder = buildProcess(command, logFile);
                if (name.language() == ErsapLang.JAVA) {
                    String javaOptions = getJVMOptions();
                    if (javaOptions != null) {
                        builder.environment().put("JAVA_OPTS", javaOptions);
                    }
                }
                String monitor = runUtils.getMonitorFrontEnd();
                if (monitor != null) {
                    builder.environment().put(ErsapConstants.ENV_MONITOR_FE, monitor);
                }
                DpeProcess dpe = new DpeProcess(name, builder);
                backgroundDpes.put(name.language(), dpe);
            }
        }

        private void destroyDpes() {
            // kill the DPEs in reverse order (the front-end last)
            for (ErsapLang lang : Arrays.asList(ErsapLang.PYTHON, ErsapLang.CPP, ErsapLang.JAVA)) {
                DpeProcess dpe = backgroundDpes.remove(lang);
                if (dpe == null) {
                    continue;
                }
                CommandUtils.destroyProcess(dpe.process);
            }
        }

        private ProcessBuilder buildProcess(String[] command, Path logFile) {
            String[] wrapper = CommandUtils.uninterruptibleCommand(command, logFile);
            ProcessBuilder builder = new ProcessBuilder(wrapper);
            builder.environment().putAll(config.getenv());
            builder.inheritIO();
            return builder;
        }

        private Integer getThreads() {
            if (config.hasValue(Config.MAX_THREADS)) {
                return config.getInt(Config.MAX_THREADS);
            }
            // For students who will use interactive nodes for processing
            // we limit default threading leve
            //            return 4;
            return Runtime.getRuntime().availableProcessors();
        }

        private String getJVMOptions() {
            if (config.hasValue(Config.JAVA_OPTIONS)) {
                return config.getString(Config.JAVA_OPTIONS);
            }
            if (config.hasValue(Config.JAVA_MEMORY)) {
                int memSize = config.getInt(Config.JAVA_MEMORY);
                return String.format("-Xms%dg -Xmx%dg -XX:+UseNUMA -XX:+UseBiasedLocking",
                                     memSize, memSize);
            }
            return null;
        }

        @Override
        public void close() {
            destroyDpes();
        }
    }

    private static class DpeProcess {

        private final DpeName name;
        private final Process process;

        DpeProcess(DpeName name, ProcessBuilder builder) throws IOException {
            this.name = name;
            this.process = builder.start();
            ErsapUtil.sleep(2000);
        }
    }

    private static String getHost(DpeName name) {
        return name.address().host();
    }

    private static String getPort(DpeName name) {
        return Integer.toString(name.address().pubPort());
    }
}
