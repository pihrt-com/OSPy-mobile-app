package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotificationSpeechTest {
    @Test
    public void disabledSpeechNeverAnnounces() {
        assertFalse(NotificationCenter.shouldSpeak(
                NotificationCenter.CATEGORY_STATION_STARTED, false, true));
    }

    @Test
    public void stationOnlyModeAnnouncesStartsAndStops() {
        assertTrue(NotificationCenter.shouldSpeak(
                NotificationCenter.CATEGORY_STATION_STARTED, true, false));
        assertTrue(NotificationCenter.shouldSpeak(
                NotificationCenter.CATEGORY_STATION_STOPPED, true, false));
        assertFalse(NotificationCenter.shouldSpeak(
                NotificationCenter.CATEGORY_RAIN, true, false));
    }

    @Test
    public void allCategoriesModeAnnouncesOtherAllowedEvents() {
        assertTrue(NotificationCenter.shouldSpeak(
                NotificationCenter.CATEGORY_DIAGNOSTICS, true, true));
        assertTrue(NotificationCenter.shouldSpeak(
                NotificationCenter.CATEGORY_OTHER, true, true));
    }
}
