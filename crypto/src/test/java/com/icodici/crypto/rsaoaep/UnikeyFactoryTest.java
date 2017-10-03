package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.HashType;
import net.sergeych.boss.Boss;
import net.sergeych.utils.Bytes;
import org.junit.Test;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.security.SecureRandom;
import java.util.ArrayList;

import static org.junit.Assert.*;

public class UnikeyFactoryTest {

    /**
     * Private key 1 in “.unikey” format.
     * Serialized form and its e/p/q/ components are provided for integration testing.
     * 524 bytes long.
     */

    private byte[] pk1Bytes = Hex.decode("" +
            "26001c010001c40001cd28ffefa9150bd3a8e9b0410e411920ed853c797e41038e9325125bb5fec3258eaba816fcc71ba86a" +
            "2489da965d825fc0a3dabb31f5190ee80c2a0e7a73c9122e099b8a00c0a819e779e0b2cb07ca71acfb6209d8d607eb58ed31" +

            "4529eced00de559f3201bd3346a64c119cb92290d486a53f77087a232ea3fdb37946ad8e6dc6498da8defc63c30238d2622b" +
            "cd58f3186e6e3d20f9b2872fd32127236d6eb4b57fa297c9814b2a3881fe2a538ed8d459f7f04faddbffc143f6e02b37d6b6" +

            "48b3c9df51aa7f2425e470ffe105d583c50d1a2da2c89f913a3af90a71ae2fafaff900e09ed4425f2a1c752b7e4f0c54b156" +
            "40ae9adfd1c9cfcd75717d45a71381c40001fef327944862e322f6dae4649354af28bd32d938b0aeb8a25bf9186e6e3d20f9" +

            "b2872fd32127236d6eb4b57fa26c2664af885a9e0830cf0a4a4b61d4e3650b3e0dd4507f21f2945479993d6a7d67d960017a" +
            "42da25e470ff22fd6304e803bcb36cf1ec791ba1e4cc0a88863ca6d8e178a5cba71bb09504e2decfded5a81bbb01e43c02ea" +

            "8ae795485864f91e3591f864c43da77a5993d213bab24cd9f95a2648f4cba8a56423e7b999b72fa4824f4bd95c5d61f4430e" +
            "230b71473ef0cc530924654d0cb8c71f4ddba5007ceb93b80a466ec54665f9fe7b9c3804d7ebd13f0768af2d28cefa724054" +

            "4cd2eb1815fbd40df864dcc4ad8bf4ac05383c56960b17c7"
    );

    private byte[] pk1E = Hex.decode("010001");
    private byte[] pk1P = Hex.decode("" +
            "cd28ffefa9150bd3a8e9b0410e411920ed853c797e41038e9325125bb5fec3258eaba816fcc71ba86a2489da965d825fc0a3" +
            "dabb31f5190ee80c2a0e7a73c9122e099b8a00c0a819e779e0b2cb07ca71acfb6209d8d607eb58ed314529eced00de559f32" +
            "01bd3346a64c119cb92290d486a53f77087a232ea3fdb37946ad8e6dc6498da8defc63c30238d2622bcd58f3186e6e3d20f9" +
            "b2872fd32127236d6eb4b57fa297c9814b2a3881fe2a538ed8d459f7f04faddbffc143f6e02b37d6b648b3c9df51aa7f2425" +
            "e470ffe105d583c50d1a2da2c89f913a3af90a71ae2fafaff900e09ed4425f2a1c752b7e4f0c54b15640ae9adfd1c9cfcd75" +
            "717d45a71381"
    );
    private byte[] pk1Q = Hex.decode("" +
            "fef327944862e322f6dae4649354af28bd32d938b0aeb8a25bf9186e6e3d20f9b2872fd32127236d6eb4b57fa26c2664af88" +
            "5a9e0830cf0a4a4b61d4e3650b3e0dd4507f21f2945479993d6a7d67d960017a42da25e470ff22fd6304e803bcb36cf1ec79" +
            "1ba1e4cc0a88863ca6d8e178a5cba71bb09504e2decfded5a81bbb01e43c02ea8ae795485864f91e3591f864c43da77a5993" +
            "d213bab24cd9f95a2648f4cba8a56423e7b999b72fa4824f4bd95c5d61f4430e230b71473ef0cc530924654d0cb8c71f4ddb" +
            "a5007ceb93b80a466ec54665f9fe7b9c3804d7ebd13f0768af2d28cefa7240544cd2eb1815fbd40df864dcc4ad8bf4ac0538" +
            "3c56960b17c7"
    );


    @Test
    public void testUnikeyStructure() throws Exception {
        assertEquals(524, pk1Bytes.length);
        assertEquals(3, pk1E.length);
        assertEquals(256, pk1P.length);
        assertEquals(256, pk1Q.length);

        // Private key structure

        // It contains 4 components...
        final ArrayList unpackedFromBoss = Boss.load(pk1Bytes);
        assertEquals(4, unpackedFromBoss.size());

        // First one is Integer, other ones are Bytes.
        assertTrue(unpackedFromBoss.get(0) instanceof Integer);
        assertTrue(unpackedFromBoss.get(1) instanceof Bytes);
        assertTrue(unpackedFromBoss.get(2) instanceof Bytes);
        assertTrue(unpackedFromBoss.get(3) instanceof Bytes);

        // Now their contents...
        assertEquals(0, unpackedFromBoss.get(0));
        assertArrayEquals(pk1E, ((Bytes) unpackedFromBoss.get(1)).toArray());
        assertArrayEquals(pk1P, ((Bytes) unpackedFromBoss.get(2)).toArray());
        assertArrayEquals(pk1Q, ((Bytes) unpackedFromBoss.get(3)).toArray());
    }

    @Test
    public void testFromUnikey() throws Exception {
        final RSAOAEPPrivateKey pk1 = UnikeyFactory.fromUnikey(pk1Bytes);
        assertNotNull(pk1);
        assertArrayEquals(
                pk1E,
                BigIntegers.asUnsignedByteArray(pk1.state.keyParameters.getPublicExponent()));
        assertArrayEquals(
                pk1P,
                BigIntegers.asUnsignedByteArray(pk1.state.keyParameters.getP()));
        assertArrayEquals(
                pk1Q,
                BigIntegers.asUnsignedByteArray(pk1.state.keyParameters.getQ()));
    }

    @Test
    public void testToUnikey() throws Exception {
        final RSAOAEPPrivateKey pk1 = new RSAOAEPPrivateKey(
                pk1E, pk1P, pk1Q,
                HashType.SHA1, HashType.SHA1,
                new SecureRandom());

        assertArrayEquals(
                pk1Bytes,
                UnikeyFactory.toUnikey(pk1));
    }
}
