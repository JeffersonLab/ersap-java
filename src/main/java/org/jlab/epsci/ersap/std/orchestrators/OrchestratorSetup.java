/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.std.orchestrators;

import org.jlab.epsci.ersap.base.DpeName;
import org.jlab.epsci.ersap.base.error.ErsapException;
import org.jlab.epsci.ersap.engine.ErsapSerializer;
import org.jlab.epsci.ersap.engine.EngineDataType;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

class OrchestratorSetup {

    final ApplicationInfo application;
    final DpeName frontEnd;
    final String session;
    final JSONObject configuration;
    final OrchestratorConfigMode configMode;
    final Set<EngineDataType> dataTypes;


    static class Builder {

        private final ApplicationInfo application;

        private DpeName frontEnd = OrchestratorConfigParser.localDpeName();
        private String session = "";

        private JSONObject config = new JSONObject();
        private OrchestratorConfigMode configMode = OrchestratorConfigMode.DATASET;
        private Set<String> dataTypes = new HashSet<>();

        Builder(Map<String, ServiceInfo> ioServices,
                List<ServiceInfo> dataServices,
                List<ServiceInfo> monServices) {
            this.application = new ApplicationInfo(ioServices, dataServices, monServices);
        }

        Builder withFrontEnd(DpeName frontEnd) {
            Objects.requireNonNull(frontEnd, "frontEnd parameter is null");
            this.frontEnd = frontEnd;
            return this;
        }

        Builder withSession(String session) {
            Objects.requireNonNull(session, "session parameter is null");
            this.session = session;
            return this;
        }

        Builder withConfig(JSONObject config) {
            this.config = config;
            return this;
        }

        Builder withConfigMode(OrchestratorConfigMode mode) {
            this.configMode = mode;
            return this;
        }

        Builder withDataTypes(Set<String> dataTypes) {
            this.dataTypes = dataTypes;
            return this;
        }

        OrchestratorSetup build() {
            return new OrchestratorSetup(this);
        }
    }


    protected OrchestratorSetup(Builder builder) {
        this.frontEnd = builder.frontEnd;
        this.session = builder.session;
        this.application = builder.application;
        this.configuration = builder.config;
        this.configMode = builder.configMode;
        this.dataTypes = builder.dataTypes.stream()
                    .map(OrchestratorSetup::dummyDataType)
                    .collect(Collectors.toSet());
    }


    private static EngineDataType dummyDataType(String mimeType) {
        return new EngineDataType(mimeType, new ErsapSerializer() {

            @Override
            public ByteBuffer write(Object data) throws ErsapException {
                throw new IllegalStateException("orchestrator should not publish: " + mimeType);
            }

            @Override
            public Object read(ByteBuffer buffer) throws ErsapException {
                // ignore serialization
                return buffer;
            }
        });
    }
}
