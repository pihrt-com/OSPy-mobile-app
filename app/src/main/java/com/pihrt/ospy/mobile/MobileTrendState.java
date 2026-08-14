package com.pihrt.ospy.mobile;

import java.util.Locale;

/** Normalizes stable and legacy Wind Monitor trend codes for localized UI. */
final class MobileTrendState {
    private MobileTrendState() {
    }

    static String normalize(String value) {
        String normalized = value == null
                ? "" : value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "up":
            case "rising":
                return "rising";
            case "down":
            case "falling":
                return "falling";
            case "steady":
                return "steady";
            default:
                return "unknown";
        }
    }
}
