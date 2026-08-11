package com.pihrt.ospy.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

public class PushRegistrationManagerTest {
    @Test
    public void classifiesAppCheckAttestationFailure() {
        Exception error = new ExecutionException(new Exception(
                "Error returned from API. code: 403 body: App attestation failed."));

        assertEquals(
                PushSyncStatusStore.DETAIL_APP_CHECK_ATTESTATION_FAILED,
                PushRegistrationManager.technicalDetail(error));
    }

    @Test
    public void classifiesAppCheckRateLimit() {
        Exception error = new ExecutionException(
                new Exception("Too many attempts."));

        assertEquals(
                PushSyncStatusStore.DETAIL_APP_CHECK_RATE_LIMITED,
                PushRegistrationManager.technicalDetail(error));
    }

    @Test
    public void classifiesTimeout() {
        Exception error = new ExecutionException(
                new SocketTimeoutException("timed out"));

        assertEquals(
                PushSyncStatusStore.DETAIL_TIMEOUT,
                PushRegistrationManager.technicalDetail(error));
    }

    @Test
    public void classifiesNetworkFailureWithoutExposingHost() {
        Exception error = new ExecutionException(
                new UnknownHostException("private.example"));

        assertEquals(
                PushSyncStatusStore.DETAIL_NETWORK,
                PushRegistrationManager.technicalDetail(error));
    }

    @Test
    public void replacesObfuscatedExceptionNameWithStableCode() {
        Exception error = new Exception("unexpected internal value");

        assertEquals(
                PushSyncStatusStore.DETAIL_UNEXPECTED,
                PushRegistrationManager.technicalDetail(error));
    }
}
