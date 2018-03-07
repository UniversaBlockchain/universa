package com.icodici.crypto;

import org.junit.Test;

import static org.junit.Assert.*;

public class KeyAddressTest {

    static AbstractKey key1 = TestKeys.privateKey(2).getPublicKey();

    @Test
    public void isMatchingKey() throws KeyAddress.IllegalAddressException {
        testMatch(true);
        testMatch(false);
    }

    private void testMatch(boolean use384) throws KeyAddress.IllegalAddressException {
        KeyAddress a = key1.address(use384, 7);
        KeyAddress b = new KeyAddress(a.toString());
        assertEquals(7, b.getTypeMark());
        assertEquals(7, a.getTypeMark());
        assertTrue(b.isMatchingKey(key1));
        assertTrue(a.isMatchingKeyAddress(b));
        assertTrue(b.isMatchingKeyAddress(a));

        assertTrue(key1.isMatchingKey(key1));
        assertTrue(key1.isMatchingKeyAddress(a));
        assertTrue(key1.isMatchingKeyAddress(b));


        byte[] pack = a.getPacked();
        pack[7] ^= 71;
        try {
            new KeyAddress(pack);
            fail("must throw error on spoiled address");
        }
        catch(KeyAddress.IllegalAddressException e) {
        }
    }

//    @Test
//    public void getPacked() {
//        System.out.println("l=" + new KeyAddress(key1, 7, false).toString().length());
//    }
}