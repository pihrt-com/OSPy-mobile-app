package com.pihrt.ospy.mobile;

import java.time.LocalDate;

final class OverviewTimelinePolicy {
    private OverviewTimelinePolicy() {
    }

    static String pathFor(int offsetDays, LocalDate selectedDate) {
        if (offsetDays < 0) {
            return "/logs/runs?date=" + selectedDate + "&limit=500";
        }
        if (offsetDays == 0) {
            return "/schedule?date=today";
        }
        return "/schedule?date=" + selectedDate;
    }
}
