/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EnvUtils {

    private static final String ID_GROUP = "([A-Za-z_][A-Za-z0-9_]*)";
    private static final String ENV_VAR_PATTERN = "((?:[\\\\$])\\$)"
            + "|\\$(?:" + ID_GROUP + "|\\{" + ID_GROUP + "(?::-([^\\}]*))?\\})"
            + "|(\\$)";
    private static final Pattern ENV_VAR_EXPR = Pattern.compile(ENV_VAR_PATTERN);

    private EnvUtils() { }

    /**
     * Gets the value of the ERSAP_HOME environment variable.
     *
     * @return the ERSAP home directory
     */
    public static String ersapHome() {
        String ersapHome = System.getenv("ERSAP_HOME");
        if (ersapHome == null) {
            throw new RuntimeException("Missing ERSAP_HOME environment variable");
        }
        return ersapHome;
    }

    /**
     * Gets the value of the ERSAP_USER_DATA environment variable.
     *
     * @return the ERSAP_USER_DATA home directory
     */
    public static String ersapUserData() {
        String ersapUserData = System.getenv("ERSAP_USER_DATA");
        if (ersapUserData == null) {
            ersapUserData = EnvUtils.ersapHome()
                + File.separator + "plugins"
                + File.separator + "epsci";
        }
        return ersapUserData;
    }

    /**
     * Gets the user account name.
     *
     * @return the account name of the user running ERSAP.
     */
    public static String userName() {
        String userName = System.getProperty("user.name");
        if (userName == null || userName.equals("?")) {
            if (inDockerContainer()) {
                return "docker";
            }
            throw new RuntimeException("Missing 'user.name' system property");
        }
        return userName;
    }

    /**
     * Gets the user home directory.
     *
     * @return the home directory of the user running ERSAP.
     */
    public static String userHome() {
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.equals("?")) {
            if (inDockerContainer()) {
                return "/";
            }
            throw new RuntimeException("Missing 'user.home' system property");
        }
        return userHome;
    }

    /**
     * Expands any environment variable present in the input string.
     *
     * @param input the string to be expanded
     * @param environment the map containing the environment variables
     *
     * @return the input string with all environment variables replaced by their values
     */
    public static String expandEnvironment(String input, Map<String, String> environment) {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = ENV_VAR_EXPR.matcher(input);
        while (matcher.find()) {
            String variable = matcher.group(2);
            if (variable == null) {
                variable = matcher.group(3);
            }
            if (variable != null) {
                String value = environment.get(variable);
                if (value == null) {
                    String defaultValue = matcher.group(4);
                    if (defaultValue != null) {
                        value = defaultValue;
                    } else {
                        value = "";
                    }
                }
                matcher.appendReplacement(sb, value);
            } else if (matcher.group(1) != null) {
                matcher.appendReplacement(sb, "\\$");
            } else {
                throw new IllegalArgumentException("Invalid environment variable format");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static boolean inDockerContainer() {
        return Files.exists(Paths.get("/.dockerenv"));
    }
}
