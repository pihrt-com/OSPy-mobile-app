package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class MobileTrendStateTest {
    @Test
    public void normalizesWindMonitorCodes() {
        assertEquals("rising", MobileTrendState.normalize("up"));
        assertEquals("falling", MobileTrendState.normalize("down"));
        assertEquals("steady", MobileTrendState.normalize("steady"));
        assertEquals("unknown", MobileTrendState.normalize("unknown"));
    }

    @Test
    public void keepsLegacyLabelsCompatible() {
        assertEquals("rising", MobileTrendState.normalize("rising"));
        assertEquals("falling", MobileTrendState.normalize("falling"));
    }
}
