/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.util.report;

import org.jlab.epsci.ersap.base.ErsapLang;
import org.jlab.epsci.ersap.base.ErsapUtil;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The base report class.
 */
public class BaseReport {

    /** the name of the report.*/
    protected final String name;
    /** the author of the report.*/
    protected final String author;
    /** the lang of the report.*/
    protected final String lang;
    /** the description of the report.*/
    protected final String description;
    /** the startTime of the report.*/
    protected final String startTime;

    private final AtomicInteger requestCount = new AtomicInteger();

    public BaseReport(String name, String author, String description) {
        this.name = name;
        this.author = author;
        this.lang = ErsapLang.JAVA.toString();
        this.description = description;
        this.startTime = ErsapUtil.getCurrentTime();
    }

    public String getName() {
        return name;
    }

    public String getLang() {
        return lang;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getStartTime() {
        return startTime;
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    public void incrementRequestCount() {
        requestCount.getAndIncrement();
    }
}
