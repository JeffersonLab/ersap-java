/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.ServiceName;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

class ServiceConfig {

    static final String READER = "reader";
    static final String WRITER = "writer";

    private static final String IO_CONFIG = "io-services";
    private static final String GLOBAL_CONFIG = "global";
    private static final String SERVICE_CONFIG = "services";

    private final JSONObject configData;
    private final Map<String, Object> model;

    private static final Configuration FTL_CONFIG = new Configuration(Configuration.VERSION_2_3_25);

    static {
        FTL_CONFIG.setDefaultEncoding("UTF-8");
        FTL_CONFIG.setNumberFormat("computer");
        FTL_CONFIG.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        FTL_CONFIG.setLogTemplateExceptions(false);
    }

    ServiceConfig(JSONObject configData) {
        this(configData, new HashMap<>());
    }

    ServiceConfig(JSONObject configData, Map<String, Object> model) {
        this.configData = configData;
        this.model = model;
    }

    JSONObject reader() {
        return getIO(READER);
    }

    JSONObject writer() {
        return getIO(WRITER);
    }

    private JSONObject getIO(String key) {
        JSONObject conf = new JSONObject();
        if (configData.has(IO_CONFIG)) {
            JSONObject ioConf = configData.getJSONObject(IO_CONFIG);
            if (ioConf.has(key)) {
                addServiceConfig(conf, ioConf, key);
            }
        }
        return conf;
    }

    JSONObject get(ServiceName service) {
        JSONObject conf = new JSONObject();
        if (configData.has(GLOBAL_CONFIG)) {
            addServiceConfig(conf, configData, GLOBAL_CONFIG);
        }
        if (configData.has(SERVICE_CONFIG)) {
            JSONObject services = configData.getJSONObject(SERVICE_CONFIG);
            if (services.has(service.name())) {
                addServiceConfig(conf, services, service.name());
            }
        }
        return conf;
    }

    private void addServiceConfig(JSONObject target, JSONObject parent, String serviceKey) {
        JSONObject config = parent.getJSONObject(serviceKey);
        for (String key : config.keySet()) {
            Object value = config.get(key);
            if (value instanceof String) {
                Object result = processTemplate(key, (String) value);
                if (result != null) {
                    target.put(key, result);
                }
            } else {
                target.put(key, value);
            }
        }
    }

    private String processTemplate(String key, String value) {
        try {
            Template tpl = new Template(key, new StringReader(value), FTL_CONFIG);
            StringWriter writer = new StringWriter();
            tpl.process(model, writer);
            return writer.toString();
        } catch (ParseException e) {
            String msg = String.format("\"%s\" template is not valid: %s", key, value);
            throw new OrchestratorConfigException(msg);
        } catch (TemplateException e) {
            String msg = String.format("\"%s\" template cannot not be evaluated: %s", key, value);
            throw new OrchestratorConfigException(msg);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
