/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class VersionUtils {

    private static class LazyHolder {

        private static final String PROPERTIES_FILE = "/META-INF/version.properties";

        private static final Properties INSTANCE = loadProperties();

        private static Properties loadProperties() {
            Properties properties = new Properties();
            try (InputStream in = VersionUtils.class.getResourceAsStream(PROPERTIES_FILE)) {
                properties.load(in);
                return properties;
            } catch (IOException e) {
                throw new RuntimeException("could not load version.properties", e);
            }
        }
    }


    private VersionUtils() { }

    private static Properties getInstance() {
        return LazyHolder.INSTANCE;
    }

    public static String getErsapVersion() {
        Properties properties = getInstance();
        String version = properties.getProperty("version");
        if (version == null) {
            throw new RuntimeException("missing ERSAP version property");
        }
        // remove snapshot string for now
        if (version.endsWith("-SNAPSHOT")) {
            return version.replace("-SNAPSHOT", "");
        }
        return version;
    }

    public static String getErsapVersionFull() {
        Properties properties = getInstance();
        String version = properties.getProperty("version");
        if (version == null) {
            throw new RuntimeException("missing ERSAP version property");
        }
        StringBuilder fullVersion = new StringBuilder();
        fullVersion.append("ERSAP version ").append(version);
        if (version.endsWith("-SNAPSHOT")) {
            String describe = properties.getProperty("git.describe");
            if (describe != null) {
                fullVersion.append(" (build ").append(describe).append(")");
            } else {
                String revision = properties.getProperty("git.revision");
                if (revision != null) {
                    fullVersion.append(" (revision ").append(revision).append(")");
                }
            }
        }
        return fullVersion.toString();
    }
}
