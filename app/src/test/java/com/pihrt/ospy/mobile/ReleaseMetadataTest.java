package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ReleaseMetadataTest {
    @Test
    public void releaseVersionAndAboutDateStayInSync() {
        assertEquals(45, BuildConfig.VERSION_CODE);
        assertEquals("0.3.37", BuildConfig.VERSION_NAME);
        assertEquals("2026-10-31", BuildConfig.VERSION_DATE);
        assertTrue(BuildConfig.VERSION_DATE.matches("\\d{4}-\\d{2}-\\d{2}"));
    }
}
