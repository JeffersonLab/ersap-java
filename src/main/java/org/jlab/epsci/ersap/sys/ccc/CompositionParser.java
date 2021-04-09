/*
 * Copyright (c) 2021.  Jefferson Science Associates, LLC.
 * Subject to the terms in the LICENSE file found in the top-level directory.
 * Author gyurjyan
 */

package org.jlab.epsci.ersap.sys.ccc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

final class CompositionParser {

    private CompositionParser() { }

    public static String removeFirst(String s) {
        if (s == null || s.length() == 0) {
            return s;
        }
        return s.substring(1);
    }

    public static String removeFirst(String input, String firstCharacter) {
        input = input.startsWith(firstCharacter) ? input.substring(1) : input;
        return input;
    }

    public static String removeLast(String s) {
        if (s == null || s.length() == 0) {
            return s;
        }
        return s.substring(0, s.length() - 1);
    }

    public static String getFirstService(String composition) {
        StringTokenizer st = new StringTokenizer(composition, ";");
        String a = st.nextToken();

        if (a.contains(",")) {
            StringTokenizer stk = new StringTokenizer(a, ",");
            return stk.nextToken();
        } else {
            return a;
        }
    }

    public static String getJSetElementAt(List<String> set, int index) {
        int ind = -1;
        for (String s : set) {
            ind++;
            if (index == ind) {
                return s;
            }
        }
        throw new NoSuchElementException();
    }
}
