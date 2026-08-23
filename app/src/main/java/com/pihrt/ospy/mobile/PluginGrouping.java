package com.pihrt.ospy.mobile;

final class PluginGrouping {
    enum Group {
        RUNNING_WITH_DATA,
        STOPPED,
        RUNNING_WITHOUT_DATA
    }

    private PluginGrouping() {
    }

    static Group classify(boolean running, boolean mobileDataAvailable) {
        if (!running) return Group.STOPPED;
        return mobileDataAvailable
                ? Group.RUNNING_WITH_DATA
                : Group.RUNNING_WITHOUT_DATA;
    }
}
