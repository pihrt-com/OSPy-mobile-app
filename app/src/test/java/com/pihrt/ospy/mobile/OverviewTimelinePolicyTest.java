package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;

public final class OverviewTimelinePolicyTest {
    @Test
    public void yesterdayUsesActualRunHistory() {
        assertEquals(
                "/logs/runs?date=2026-08-16&limit=500",
                OverviewTimelinePolicy.pathFor(
                        -1, LocalDate.of(2026, 8, 16)));
    }

    @Test
    public void todayAndTomorrowUseSchedule() {
        assertEquals(
                "/schedule?date=today",
                OverviewTimelinePolicy.pathFor(
                        0, LocalDate.of(2026, 8, 17)));
        assertEquals(
                "/schedule?date=2026-08-18",
                OverviewTimelinePolicy.pathFor(
                        1, LocalDate.of(2026, 8, 18)));
    }
}
