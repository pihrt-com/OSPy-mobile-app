package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class InstallationTest {
    @Test
    public void detectsLocalNetworkHosts() {
        assertTrue(Installation.needsLocalNetworkAccess("http://localhost:8080"));
        assertTrue(Installation.needsLocalNetworkAccess("http://127.0.0.1"));
        assertTrue(Installation.needsLocalNetworkAccess("http://10.0.0.2"));
        assertTrue(Installation.needsLocalNetworkAccess("http://172.16.0.2"));
        assertTrue(Installation.needsLocalNetworkAccess("http://172.31.255.2"));
        assertTrue(Installation.needsLocalNetworkAccess("http://192.168.1.2"));
        assertTrue(Installation.needsLocalNetworkAccess("http://169.254.1.2"));
        assertTrue(Installation.needsLocalNetworkAccess("http://ospy.local"));
        assertTrue(Installation.needsLocalNetworkAccess("http://raspberrypi"));
        assertTrue(Installation.needsLocalNetworkAccess("http://[::1]:8080"));
        assertTrue(Installation.needsLocalNetworkAccess("http://[fd12::5]"));
        assertTrue(Installation.needsLocalNetworkAccess("http://[fe80::5]"));
    }

    @Test
    public void leavesPublicHostsUnrestricted() {
        assertFalse(Installation.needsLocalNetworkAccess("https://ospy.example.com"));
        assertFalse(Installation.needsLocalNetworkAccess("http://172.15.0.2"));
        assertFalse(Installation.needsLocalNetworkAccess("http://172.32.0.2"));
        assertFalse(Installation.needsLocalNetworkAccess("https://8.8.8.8"));
        assertFalse(Installation.needsLocalNetworkAccess("not a URL"));
    }
}
