package com.octapulse.backend.service;

import java.util.Locale;

public enum PickMethod {
    KO_TKO,
    SUBMISSION,
    DECISION;

    /** Maps a free-text result method ("KO/TKO", "Submission (RNC)", "Decision - Unanimous") or null. */
    public static PickMethod fromResult(String method) {
        if (method == null) {
            return null;
        }
        String m = method.toUpperCase(Locale.ROOT);
        if (m.contains("KO")) {
            return KO_TKO;
        }
        if (m.contains("SUB")) {
            return SUBMISSION;
        }
        if (m.contains("DEC")) {
            return DECISION;
        }
        return null;
    }
}
