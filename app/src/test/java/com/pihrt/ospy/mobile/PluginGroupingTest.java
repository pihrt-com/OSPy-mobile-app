package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class PluginGroupingTest {
    @Test
    public void runningPluginWithDataIsInRunningGroup() {
        assertEquals(
                PluginGrouping.Group.RUNNING_WITH_DATA,
                PluginGrouping.classify(true, true));
    }

    @Test
    public void stoppedPluginIsInStoppedGroupEvenWhenDataIsAdvertised() {
        assertEquals(
                PluginGrouping.Group.STOPPED,
                PluginGrouping.classify(false, true));
        assertEquals(
                PluginGrouping.Group.STOPPED,
                PluginGrouping.classify(false, false));
    }

    @Test
    public void runningPluginWithoutDataIsInNoDataGroup() {
        assertEquals(
                PluginGrouping.Group.RUNNING_WITHOUT_DATA,
                PluginGrouping.classify(true, false));
    }
}
