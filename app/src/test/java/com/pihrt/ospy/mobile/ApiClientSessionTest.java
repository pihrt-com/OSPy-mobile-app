package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ApiClientSessionTest {
    @Test
    public void reusesSharedTokenUntilSafetyMargin() {
        long now = 1_000_000L;

        assertTrue(ApiClient.tokenStillUsable("access", now + 60_000L, now));
        assertFalse(ApiClient.tokenStillUsable("access", now + 20_000L, now));
        assertFalse(ApiClient.tokenStillUsable("", now + 60_000L, now));
    }

    @Test
    public void concurrentUnauthorizedRequestUsesNewerSharedToken() {
        assertTrue(ApiClient.shouldReuseAfterFailure("new-token", "old-token"));
        assertFalse(ApiClient.shouldReuseAfterFailure("same-token", "same-token"));
        assertFalse(ApiClient.shouldReuseAfterFailure("new-token", ""));
    }
}
