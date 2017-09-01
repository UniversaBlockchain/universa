package com.icodici.crypto;

import org.junit.Test;

import java.util.Arrays;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class AbstractKeyTest {
    @Test
    public void fingerprint() throws Exception {
        PrivateKey k = TestKeys.privateKey(0);
        byte[] f1 = k.getPublicKey().fingerprint();
        assertEquals(33, f1.length);
        byte[] f2 = k.fingerprint();
        assertArrayEquals(f1, f2);
        assertEquals(AbstractKey.FINGERPRINT_SHA512, f1[0]);
    }

    @Test
    public void matchAnonymousId() throws Exception {
        PrivateKey k1 = TestKeys.privateKey(0);
        byte[] id1 = k1.createAnonymousId();
        byte[] id12 = k1.createAnonymousId();
        PrivateKey k2 = TestKeys.privateKey(1);
        byte[] id2 = k2.createAnonymousId();
        assertEquals(64, id1.length);
        assertEquals(64, id12.length);
        assertEquals(64, id2.length);
        assertFalse(Arrays.equals(id1, id12));
        assertFalse(Arrays.equals(id1, id2));
        assertFalse(Arrays.equals(id12, id2));

        assertTrue(k1.matchAnonymousId(id1));
        assertTrue(k1.matchAnonymousId(id12));
        assertTrue(k2.matchAnonymousId(id2));

        assertFalse(k2.matchAnonymousId(id1));
        assertFalse(k2.matchAnonymousId(id12));
        assertFalse(k1.matchAnonymousId(id2));
    }

}