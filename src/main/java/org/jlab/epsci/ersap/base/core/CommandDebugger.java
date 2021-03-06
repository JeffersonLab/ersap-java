/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base.core;

import org.jlab.epsci.ersap.base.ErsapUtil;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.coda.xmsg.core.xMsgConnection;
import org.jlab.coda.xmsg.core.xMsgMessage;
import org.jlab.coda.xmsg.core.xMsgTopic;
import org.jlab.coda.xmsg.data.xMsgMimeType;
import org.jlab.coda.xmsg.excp.xMsgException;
import org.jlab.coda.xmsg.net.xMsgProxyAddress;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CommandDebugger extends ErsapBase {

    private final Pattern commentPattern = Pattern.compile("^\\s*#.*$");
    private final Pattern sleepPattern = Pattern.compile("^\\s*(sleep)\\s+(\\d*)$");

    private CommandDebugger() {
        super(ErsapComponent.orchestrator("broker", 1, "broker"), ErsapComponent.dpe());
    }

    private void processFile(String file) {
        Path path = Paths.get(file);
        try (Stream<String> stream = Files.lines(path, Charset.defaultCharset())) {
            stream.forEach(this::processCommand);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processCommand(String line) {
        line = line.trim();
        if (line.length() == 0) { // empty line
            return;
        }
        if (commentPattern.matcher(line).matches()) { // commented line
            return;
        }
        Matcher sleep = sleepPattern.matcher(line);
        if (sleep.matches()) { // sleep command
            int time = Integer.parseInt(sleep.group(2));
            System.out.printf("Sleeping %d ms%n", time);
            ErsapUtil.sleep(time);
            return;
        }

        try {
            Command cmd = new Command(line);
            System.out.println("C: " + cmd);
            try (xMsgConnection connection = getConnection(cmd.address)) {
                xMsgMessage message = MessageUtil.buildRequest(cmd.topic, cmd.request);
                message.getMetaData().setAuthor(getName());
                message.getMetaData().setSender(getName());
                if (cmd.action.equals("send")) {
                    publish(connection, message);
                } else {
                    printResponse(syncPublish(connection, message, cmd.timeout));
                }
            }
        } catch (xMsgException | ErsapException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    private void printResponse(xMsgMessage res) {
        String mimeType = res.getMimeType();
        if (mimeType.equals(xMsgMimeType.STRING)) {
            String data = new String(res.getData());
            System.out.printf("R: %s%n", data);
        } else {
            System.out.printf("R: mime-type = %s%n", mimeType);
        }
    }


    private static class Command {

        private final String action;
        private final xMsgProxyAddress address;
        private final xMsgTopic topic;
        private final String request;

        private int timeout = 0;

        Command(String cmd) throws ErsapException {
            try {
                StringTokenizer tk = new StringTokenizer(cmd, " ");
                action = tk.nextToken();
                if (!action.equals("send") && !action.equals("sync_send")) {
                    throw new ErsapException("Invalid action: " + action);
                }
                if (action.equals("sync_send")) {
                    timeout = Integer.parseInt(tk.nextToken());
                }
                String component = tk.nextToken().replace("localhost", ErsapUtil.localhost());
                address = new xMsgProxyAddress(ErsapUtil.getDpeHost(component),
                                               ErsapUtil.getDpePort(component));
                if (ErsapUtil.isDpeName(component)) {
                    topic = xMsgTopic.build("dpe", component);
                } else if (ErsapUtil.isContainerName(component)) {
                    topic = xMsgTopic.build("dpe", component);
                } else if (ErsapUtil.isServiceName(component)) {
                    topic = xMsgTopic.wrap(component);
                } else {
                    throw new ErsapException("Not a ERSAP component: " + component);
                }
                request = tk.nextToken();
            } catch (NoSuchElementException | NumberFormatException e) {
                throw new RuntimeException("Invalid line: " + cmd, e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("action = ").append(action).append(" ");
            if (timeout > 0)  {
                sb.append(" timeout = ").append(timeout).append(" ");
            }
            sb.append(" proxy = ").append(address).append(" ");
            sb.append(" topic = ").append(topic).append(" ");
            sb.append(" request = ").append(request);
            return sb.toString();
        }
    }


    public static void main(String[] args) {
        try (CommandDebugger broker = new CommandDebugger()) {
            broker.processFile(args[0]);
        }
    }
}
