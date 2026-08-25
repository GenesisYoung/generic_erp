package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class RefreshTokenStoreTest {

    @Test
    void fingerprintsAreStableAndDoNotContainRawTokens() {
        String first = RefreshTokenStore.fingerprint("refresh-token-one");
        String same = RefreshTokenStore.fingerprint("refresh-token-one");
        String second = RefreshTokenStore.fingerprint("refresh-token-two");

        assertEquals(first, same);
        assertNotEquals(first, second);
        assertEquals(64, first.length());
        assertNotEquals("refresh-token-one", first);
    }
}
