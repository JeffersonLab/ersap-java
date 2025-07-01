/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base.core;

/**
 * ERSAP internal constants.
 *
 *
 *
 */
public final class ErsapConstants {

    private ErsapConstants() {
    }

    /** java port number. */
    public static final int JAVA_PORT = 7771;
    /** cpp port number. */
    public static final int CPP_PORT = 7781;
    /** python port number. */
    public static final int PYTHON_PORT = 7791;
    /** registration port shift. */
    public static final int REG_PORT_SHIFT = 4;
    /** monitor port number. */
    public static final int MONITOR_PORT = 9000;

    /** dpe name. */
    public static final String DPE = "dpe";
    /** ersapSession name. */
    public static final String SESSION = "ersapSession";
    /** startDpe string. */
    public static final String START_DPE = "startDpe";
    /** stopDpe string. */
    public static final String STOP_DPE = "stopDpe";
    /** stopRemoteDpe string. */
    public static final String STOP_REMOTE_DPE = "stopRemoteDpe";
    /** dpeExit string. */
    public static final String DPE_EXIT = "dpeExit";
    /** pingDpe string. */
    public static final String PING_DPE = "pingDpe";
    /** pingRemoteDpe string. */
    public static final String PING_REMOTE_DPE = "pingRemoteDpe";
    /** dpeAlive string. */
    public static final String DPE_ALIVE = "dpeAlive";
    /** dpeReport string. */
    public static final String DPE_REPORT = "dpeReport";
    /** ring string. */
    public static final String MONITOR_REPORT = "ring";

    /** container string. */
    public static final String CONTAINER = "container";
    /** getContainerState string. */
    public static final String STATE_CONTAINER = "getContainerState";
    /** startContainer string. */
    public static final String START_CONTAINER = "startContainer";
    /** startRemoteContainer string. */
    public static final String START_REMOTE_CONTAINER = "startRemoteContainer";
    /** stopContainer string. */
    public static final String STOP_CONTAINER = "stopContainer";
    /** stopRemoteContainer string. */
    public static final String STOP_REMOTE_CONTAINER = "stopRemoteContainer";
    /** containerIsDown string. */
    public static final String CONTAINER_DOWN = "containerIsDown";
    /** removeContainer string. */
    public static final String REMOVE_CONTAINER = "removeContainer";

    /** getServiceState string. */
    public static final String STATE_SERVICE = "getServiceState";
    /** startService string. */
    public static final String START_SERVICE = "startService";
    /** startRemoteService string. */
    public static final String START_REMOTE_SERVICE = "startRemoteService";
    /** stopService string. */
    public static final String STOP_SERVICE = "stopService";
    /** stopRemoteService string. */
    public static final String STOP_REMOTE_SERVICE = "stopRemoteService";
    /** deployService string. */
    public static final String DEPLOY_SERVICE = "deployService";
    /** removeService string. */
    public static final String REMOVE_SERVICE = "removeService";

    /** serviceReportInfo string. */
    public static final String SERVICE_REPORT_INFO = "serviceReportInfo";
    /** serviceReportDone string. */
    public static final String SERVICE_REPORT_DONE = "serviceReportDone";
    /** serviceReportData string. */
    public static final String SERVICE_REPORT_DATA = "serviceReportData";
    /** serviceReportRing string. */
    public static final String SERVICE_REPORT_RING = "serviceReportRing";

    /** setFrontEnd string. */
    public static final String SET_FRONT_END = "setFrontEnd";
    /** setFrontEndRemote string. */
    public static final String SET_FRONT_END_REMOTE = "setFrontEndRemote";

    /** setSession string. */
    public static final String SET_SESSION = "setSession";

    /**  date format string. */
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** reportRegistration string. */
    public static final String REPORT_REGISTRATION = "reportRegistration";
    /** reportRuntime string. */
    public static final String REPORT_RUNTIME = "reportRuntime";
    /** reportJson string. */
    public static final String REPORT_JSON = "reportJson";

    /** DPERegistration string. */
    public static final String REGISTRATION_KEY = "DPERegistration";
    /** DPERuntime string. */
    public static final String RUNTIME_KEY = "DPERuntime";

    /**  share memory key string. */
    public static final String SHARED_MEMORY_KEY = "ersap/shmkey";

    /**  markup key separation string. */
    public static final String MAPKEY_SEP = "#";
    /**  data separation string. */
    public static final String DATA_SEP = "?";
    /** language separation string. */
    public static final String LANG_SEP = "_";
    /** port separation string. */
    public static final String PORT_SEP = "%";

    /** INFO string. */
    public static final String INFO = "INFO";
    /** WARNING string. */
    public static final String WARNING = "WARNING";
    /** ERROR string. */
    public static final String ERROR = "ERROR";
    /** done string. */
    public static final String DONE = "done";
    /** data string. */
    public static final String DATA = "data";

    /**  Benchmark const integer. */
    public static final int BENCHMARK = 10000;

    /** java lang string. */
    public static final String JAVA_LANG = "java";
    /** python lang string. */
    public static final String PYTHON_LANG = "python";
    /** cpp lang string. */
    public static final String CPP_LANG = "cpp";

    /** undefined string. */
    public static final String UNDEFINED = "undefined";

    /** ERSAP_MONITOR_FE string. */
    public static final String ENV_MONITOR_FE = "ERSAP_MONITOR_FE";
}
