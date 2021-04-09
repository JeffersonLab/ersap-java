/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.coda.xmsg.data.xMsgRegQuery;
import org.jlab.coda.xmsg.data.xMsgRegRecord;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

abstract class ErsapFilter {

    static final String TYPE_DPE = "dpe";
    static final String TYPE_CONTAINER = "container";
    static final String TYPE_SERVICE = "service";

    private final xMsgRegQuery regQuery;
    private final String type;

    private List<Predicate<xMsgRegRecord>> regFilters = new ArrayList<>();
    private List<Predicate<JSONObject>> filters = new ArrayList<>();

    ErsapFilter(xMsgRegQuery query, String type) {
        this.regQuery = query;
        this.type = type;
    }

    void addRegFilter(Predicate<xMsgRegRecord> predicate) {
        regFilters.add(predicate);
    }

    void addFilter(Predicate<JSONObject> predicate) {
        filters.add(predicate);
    }

    xMsgRegQuery regQuery() {
        return regQuery;
    }

    Predicate<xMsgRegRecord> regFilter() {
        return regFilters.stream().reduce(Predicate::and).orElse(t -> true);
    }

    Predicate<JSONObject> filter() {
        return filters.stream().reduce(Predicate::and).orElse(t -> true);
    }

    boolean useDpe() {
        return !filters.isEmpty();
    }

    @Override
    public String toString() {
        return type;
    }
}
