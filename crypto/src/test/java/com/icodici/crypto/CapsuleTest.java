/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by sergeych on 17.12.16.
 */
public class CapsuleTest {
    @Test
    public void testNotSigned() throws Exception {
        Capsule c1 = new Capsule();
        c1.setPublicData("hello", "world", "I'm", "the coffer");
        Capsule c2 = new Capsule();
        c2.setPublicData("hello", "world", "I'm", "the coffer");
        assertEquals(c1, c2);

        Capsule c4 = new Capsule(c1.pack(), null);
        assertFalse(c1.isSigned());
        assertEquals(c1, c4);
        assertNotSame(c1, c4);
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void testSigned() throws Exception {
        Capsule c1 = new Capsule();
        c1.setPublicData("hello", "world", "I'm", "the coffer");

        PrivateKey k1 = TestKeys.privateKey(0);
        PrivateKey k2 = TestKeys.privateKey(1);

        c1.addSigners(k1, k2);

        Capsule c2 = new Capsule();
        c2.setPublicData("hello", "world", "I'm", "the coffer");

        byte[] packed = c1.pack();
        Capsule c4 = new Capsule(packed, null);
        assertEquals(c1, c4);
        assertTrue(c4.isSigned());
        assertFalse(c4.isPartiallySigned());

        Collection<AbstractKey> signers = c4.getSigningKeys();
        assertEquals(2, signers.size());
        assertTrue(signers.contains(k1.getPublicKey()));
        assertTrue(signers.contains(k2.getPublicKey()));

        packed[0x456]--;
        exception.expect(Capsule.BadSignatureException.class);
        c4 = new Capsule(packed, null);
    }

    @Test
    public void testSignedExtra() throws Exception {
        Capsule c1 = new Capsule();
        c1.setPublicData("hello", "world", "I'm", "the coffer");

        PrivateKey k1 = TestKeys.privateKey(0);
        PrivateKey k2 = TestKeys.privateKey(1);

        c1.addSigners(k1);
        String kid = c1.addSigner(k2,
                                  new Binder("extra", "data", "is", "OK")
                     );

        Capsule c2 = new Capsule();
        c2.setPublicData("hello", "world", "I'm", "the coffer");

        byte[] packed = c1.pack();
        Capsule c4 = new Capsule(packed, null);
        assertEquals(c1, c4);
        assertTrue(c4.isSigned());
        assertFalse(c4.isPartiallySigned());

        Map<String,Binder> signers = c4.getSigners();
        assertEquals(2, signers.size());
        ArrayList<AbstractKey> kk = new ArrayList<>();
        for(Binder x: signers.values()) {
            kk.add((AbstractKey) x.get("key"));
        }
        assertTrue(kk.contains(k1.getPublicKey()));
        assertTrue(kk.contains(k2.getPublicKey()));

        Binder extra = c4.getSignerData(k2.getPublicKey());

        assertEquals("data", extra.getStringOrThrow("extra"));
        assertEquals("OK", extra.getStringOrThrow("is"));
        assertEquals(extra.size(), 2);

        assertEquals(kid, c4.getSignerId(k2.getPublicKey()));
        assertEquals(k2.getPublicKey(), c4.getSignerKey(kid));

        assertEquals("data", extra.getStringOrThrow("extra"));
        assertEquals("OK", extra.getStringOrThrow("is"));
        assertEquals(extra.size(), 2);

        extra = c4.getSignerData(kid);
        assertEquals("data", extra.getStringOrThrow("extra"));
        assertEquals("OK", extra.getStringOrThrow("is"));
        assertEquals(extra.size(), 2);


        packed[0x456]--;
        exception.expect(Capsule.BadSignatureException.class);
        c4 = new Capsule(packed, null);
    }

    @Test
    public void testPartiallySigned() throws Exception {
        Capsule c1 = new Capsule();
        c1.setPublicData("hello", "world", "I'm", "the coffer");

        PrivateKey k1 = TestKeys.privateKey(0);
        PrivateKey k2 = TestKeys.privateKey(1);

        c1.addSigners(k1, k2);

        // Let's remove one signature
        byte[] packed = c1.pack();
        Binder b = Boss.unpack(packed);
        ArrayList<Binder> ss = b.getBinders("signatures");
        ss.remove(0);
        b.put("signatures", ss);
        packed = Boss.pack(b);

        // Now it is only partially signed
        Capsule c3 = new Capsule(packed, null, true, false);
        assertFalse(c3.isSigned());
        assertTrue(c3.isPartiallySigned());
    }

    class KRing implements Capsule.KeySource {

        Collection<AbstractKey> keys;

        KRing(AbstractKey... kk) {
            keys = Do.collection(kk);
        }

        @Override
        public Collection<AbstractKey> findKey(KeyInfo keyInfo) {
            return keys;
        }
    }

    @Test
    public void testEncrypted() throws Exception {
        Capsule c1 = new Capsule();
        c1.setPrivateData("hello", "world", "I'm", "the coffer");

        PrivateKey k1 = TestKeys.privateKey(0);
        PrivateKey k2 = TestKeys.privateKey(1);

        SymmetricKey k3 = new SymmetricKey();
        SymmetricKey k4 = new SymmetricKey();
        c1.addKeys(k1.getPublicKey());
        c1.addKeys(k3);

        byte[] packed = c1.pack();

        Capsule c2 = new Capsule(packed, new KRing(k3));
        assertEquals(c1, c2);

        c2 = new Capsule(packed, k1.asKeySource());
        assertEquals(c1, c2);

        exception.expect(Capsule.DecryptionFailedException.class);
        c2 = new Capsule(packed, new KRing(k2));
        assertEquals(c1, c2);

    }

    @Test
    public void decryptWithPassword() throws Exception {
        Capsule c1 = new Capsule();
        c1.setPrivateData("Very", "secret materials");
        String password = "icodici forever";
        c1.addKeys(new KeyInfo(KeyInfo.PRF.HMAC_SHA256, 1000, null, null).derivePassword(password));
        c1.addKeys(new KeyInfo(KeyInfo.PRF.HMAC_SHA256, 1000, null, null).derivePassword(password+"12"));

        byte[] packed = c1.pack();
        Capsule c2 = new Capsule(password, packed);
        assertEquals(c1, c2);

        exception.expect(Capsule.DecryptionFailedException.class);
        new Capsule(password + "bad", packed);
    }

}