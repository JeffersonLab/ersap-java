/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.base;

import org.jlab.coda.xmsg.data.xMsgRegQuery;

/**
 * A filter to select DPEs.
 * Use the {@link ErsapFilters} factory to choose one of the filters.
 */
public class DpeFilter extends ErsapFilter {

    DpeFilter(xMsgRegQuery query) {
        super(query, TYPE_DPE);
    }
}
