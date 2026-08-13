package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ManualStationDurationTest {
    @Test
    public void convertsMinutesAndSeconds() {
        assertEquals(1, MainActivity.manualDurationSeconds("0", "1"));
        assertEquals(125, MainActivity.manualDurationSeconds("2", "5"));
        assertEquals(59999, MainActivity.manualDurationSeconds("999", "59"));
    }

    @Test
    public void rejectsEmptyZeroNegativeAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class,
                () -> MainActivity.manualDurationSeconds("", "0"));
        assertThrows(IllegalArgumentException.class,
                () -> MainActivity.manualDurationSeconds("0", "0"));
        assertThrows(IllegalArgumentException.class,
                () -> MainActivity.manualDurationSeconds("-1", "0"));
        assertThrows(IllegalArgumentException.class,
                () -> MainActivity.manualDurationSeconds("1000", "0"));
        assertThrows(IllegalArgumentException.class,
                () -> MainActivity.manualDurationSeconds("1", "60"));
    }
}
