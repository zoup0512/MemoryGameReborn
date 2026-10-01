package com.activeandroid.util;

import java.util.Comparator;

public class NaturalOrderComparator implements Comparator<Object> {
    int compareRight(String a, String b) {
        int bias = 0;
        int ia = 0;
        int ib = 0;
        while (true) {
            char ca = charAt(a, ia);
            char cb = charAt(b, ib);
            if (!Character.isDigit(ca) && !Character.isDigit(cb)) {
                return bias;
            }
            if (!Character.isDigit(ca)) {
                return -1;
            }
            if (!Character.isDigit(cb)) {
                return 1;
            }
            if (ca < cb) {
                if (bias == 0) {
                    bias = -1;
                }
            } else if (ca > cb) {
                if (bias == 0) {
                    bias = 1;
                }
            } else if (ca == '\u0000' && cb == '\u0000') {
                return bias;
            }
            ia++;
            ib++;
        }
    }

    public int compare(Object o1, Object o2) {
        String a = o1.toString();
        String b = o2.toString();
        int ia = 0;
        int ib = 0;
        while (true) {
            int nzb = 0;
            int nza = 0;
            char ca = charAt(a, ia);
            char cb = charAt(b, ib);
            while (true) {
                if (!Character.isSpaceChar(ca) && ca != '0') {
                    break;
                }
                if (ca == '0') {
                    nza++;
                } else {
                    nza = 0;
                }
                ia++;
                ca = charAt(a, ia);
            }
            while (true) {
                if (!Character.isSpaceChar(cb) && cb != '0') {
                    break;
                }
                if (cb == '0') {
                    nzb++;
                } else {
                    nzb = 0;
                }
                ib++;
                cb = charAt(b, ib);
            }
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int result = compareRight(a.substring(ia), b.substring(ib));
                if (result != 0) {
                    return result;
                }
            }
            if (ca == '\u0000' && cb == '\u0000') {
                return nza - nzb;
            }
            if (ca < cb) {
                return -1;
            }
            if (ca > cb) {
                return 1;
            }
            ia++;
            ib++;
        }
    }

    static char charAt(String s, int i) {
        if (i >= s.length()) {
            return '\u0000';
        }
        return s.charAt(i);
    }
}
