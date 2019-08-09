/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.rsaoaep;

import com.icodici.crypto.AbstractPrivateKey;
import com.icodici.crypto.AbstractPublicKey;
import com.icodici.crypto.HashType;
import net.sergeych.tools.Hashable;
import org.junit.Test;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by amyodov on 18.04.16.
 */
public class RSAOAEPPublicKeyTest {
    /**
     * Test vectors from RSA OAEP specification.
     */
    private static final RSAOAEPTestVectors oaepSpec = new RSAOAEPTestVectors();
    /**
     * Test vectors from RSASSA-PSS specification.
     */
    private static final RSASSAPSSTestVectors pssSpec = new RSASSAPSSTestVectors();

    /**
     * Random public key.
     */
    private static RSAKeyParameters randomPublicKey1;

    static {
        RSAKeyPairGenerator keyGen1 = new RSAKeyPairGenerator();
        BigInteger e1 = BigInteger.valueOf(65537);
        keyGen1.init(new RSAKeyGenerationParameters(e1, new SecureRandom(), 4096, 1));
        AsymmetricCipherKeyPair keyPair1 = keyGen1.generateKeyPair();
        randomPublicKey1 = (RSAKeyParameters) keyPair1.getPublic();
    }

    /**
     * Test {@link RSAOAEPPublicKey} basic methods:
     * {@link RSAOAEPPublicKey#canEncrypt},
     * {@link RSAOAEPPublicKey#getBitStrength},
     * {@link RSAOAEPPublicKey#isInitialized},
     * {@link RSAOAEPPublicKey#algorithmTag}.
     */
    @Test
    public void basicMethods() throws Exception {
        AbstractPublicKey goodPublicKey1 = oaepSpec.getPublicKey();

        assertEquals(goodPublicKey1.canEncrypt(), true);
        assertEquals(goodPublicKey1.getBitStrength(), 1024);
        assertEquals(goodPublicKey1.isInitialized(), true);
        assertEquals(goodPublicKey1.algorithmTag(), "r1");
    }

    /**
     * Test {@link RSAOAEPPublicKey#encrypt}.
     */
    @Test
    public void encrypt() throws Exception {
        // Test on RSA vectors.
        AbstractPublicKey rsaPublicKey = oaepSpec.getPublicKey();
        ((RSAOAEPPublicKey) rsaPublicKey).resetEncryptor();
        assertArrayEquals(rsaPublicKey.encrypt(oaepSpec.M), oaepSpec.C);
    }

    /**
     * Test {@link RSAOAEPPublicKey#checkSignature}.
     */
    @Test
    public void checkSignature() throws Exception {
        // Test sample RSA vectors.
        AbstractPublicKey rsaPublicKey = pssSpec.getPublicKey();
        AbstractPrivateKey rsaPrivateKey = pssSpec.getPrivateKey();
        assertArrayEquals(
                rsaPrivateKey.sign(pssSpec.M, HashType.SHA1, RSASSAPSSTestVectors.salt),
                pssSpec.S);
        assertTrue(rsaPublicKey.checkSignature(
                pssSpec.M,
                rsaPrivateKey.sign(pssSpec.M, HashType.SHA1, RSASSAPSSTestVectors.salt),
                HashType.SHA1,
                RSASSAPSSTestVectors.salt.length));
    }

    /**
     * Test {@link RSAOAEPPublicKey#checkSignature} with non-usual salt.
     */
    @Test
    public void checkSignatureWithCustomSalt() throws Exception {
        byte[] e = Hex.decode("010001");
        byte[] p = Hex.decode("cd28ffefa9150bd3a8e9b0410e411920ed853c797e41038e9325125bb5fec3258eaba816fcc71ba86a2489da965d825fc0a3dabb31f5190ee80c2a0e7a73c9122e099b8a00c0a819e779e0b2cb07ca71acfb6209d8d607eb58ed314529eced00de559f3201bd3346a64c119cb92290d486a53f77087a232ea3fdb37946ad8e6dc6498da8defc63c30238d2622bcd58f3186e6e3d20f9b2872fd32127236d6eb4b57fa297c9814b2a3881fe2a538ed8d459f7f04faddbffc143f6e02b37d6b648b3c9df51aa7f2425e470ffe105d583c50d1a2da2c89f913a3af90a71ae2fafaff900e09ed4425f2a1c752b7e4f0c54b15640ae9adfd1c9cfcd75717d45a71381");
        byte[] q = Hex.decode("fef327944862e322f6dae4649354af28bd32d938b0aeb8a25bf9186e6e3d20f9b2872fd32127236d6eb4b57fa26c2664af885a9e0830cf0a4a4b61d4e3650b3e0dd4507f21f2945479993d6a7d67d960017a42da25e470ff22fd6304e803bcb36cf1ec791ba1e4cc0a88863ca6d8e178a5cba71bb09504e2decfded5a81bbb01e43c02ea8ae795485864f91e3591f864c43da77a5993d213bab24cd9f95a2648f4cba8a56423e7b999b72fa4824f4bd95c5d61f4430e230b71473ef0cc530924654d0cb8c71f4ddba5007ceb93b80a466ec54665f9fe7b9c3804d7ebd13f0768af2d28cefa7240544cd2eb1815fbd40df864dcc4ad8bf4ac05383c56960b17c7");

        byte[] nExpected = Hex.decode("cc518b92cef4a1baf1fe3fd3a4419bb5a0a5fe381c7d4b365dd672343a911236474a2fdff759dac21b40af42e83ec8ff30e403ed339faca0ab3a15f72a22dc822184a4949179590cbd53098d443fed61209a47223c4c6212e1b0085824d4ffd7f2d4927533f89a98132d070a61b062873c22b7ae65411a1ea6a9d33d30c5bbe63b19e05fe7589ac50ba5b704ee6fe9338d09dd7e9efd071534646101d058e676c9b650381ff5a0cdb2f11c3167378a25493957cb3ac71770a43cd77bc605b41f11c437560c0a0271154c4782f9c6a731477260e7334a380b81b197c1af53608d9ea451b136afdf7ada9ebba46db0a92464c7283b48a2eb332a89cc70ec02b8c66adc1e2344365db7f7bae30fe793e36eeacc93663969aca23a863556b2b9c4ff690f9f87994fa246c514bec71c91d0df26436934da51a6d484667d5e8f46f3599a8a5f52287dfd019e919ef4650406a44657f59342426ad61d33668b217ffe5f333c1858ce4cbbdcbbb71d486bca83f4eefed82088ea13e8b82288b639446831f61f298e96ebf5281056ed51d5f3e8e25c341386c699f4954a3f33a82efaf88e7d791e311bfbbcc947865349af32ddad1a5addafb10ff7401549a1c53bb7777533e269ec94e73d6f5927662c403a05b7b0541b3af816e91da94bbab8b095fedbb003253deffcbafb4190057f523564646d3f16d9e43a3b8be29a2694942bc047");

        RSAOAEPPrivateKey privateKey = new RSAOAEPPrivateKey(e, p, q, HashType.SHA1, HashType.SHA1, new SecureRandom());
        RSAOAEPPublicKey publicKey = (RSAOAEPPublicKey) privateKey.getPublicKey();

        assertEquals(publicKey.state.keyParameters.getModulus(), BigIntegers.fromUnsignedByteArray(nExpected));

        byte[] message = Hex.decode("4655424152206d65616e73204675636b6564205570204265796f756420416c6c205265636f676e6974696f6e");
        // Signature using SHA-1
        byte[] signatureExpected = Hex.decode("78def239f5d4809c0557d11407c4825e6afb261873ab9f5d3e3fc22d4faa6c358b81c96d486ae2dbc8ad5ccecec6f49a0d5207579444b85ee4ec9a2d06a737a87717083282c4cf4af1ecc14a4fdfbdaa0d53e139fc77226bc4a01fe55bbc8a29403969911c3599508aaa8701f064b95e7e64b349e320724d6c9e2af5a8556d253bed772fb659bbee0e0a6dfe205d58f71f049b023d9ce8b278eaf3141cec06aab46e78cde55d3c403784819c34741deb681bdc2cee01c41e549f17aeb59ca80b8f045de1cf4ff983599e422bce2e68903d717291d897cf39961577e5fc9af9619379790628dbf369fee707a6a4daa3211ff840b46807351204acb60acc528099f851b8a76b4eaae5f84715ecc971c296f9cf3e058badc544a01e7d1dbef1c353d8704c6cffea9398e7ee6fda895d0dabc8ac18ed88c9497664c867e93e56fbebd4436eb3efa755f8fac3fa627e795be43d92d904fe0a9af989a6c504b1e11617d45b75cb5166795e58e69dfed4bf800f8088b0b48e12600c7f460bdaf34a1999d47ce3f5e11343d1e2b1797fc744aab9fcc938d08f70f91dd30c937e0515f8eb03e1a034044c33fbfbed83df5a1b7145ef0fcbb0f41f909793bd23bd964af1c5a53f72ef7b5920cd77d25cc2d9a7a38cbd86cbb3314222ae1ea3432f1370aefb1ea5780630b5f41c0cd408391537b05e242d0c7e0e6dadfd1de2c6c9500298c3");
        int expectedSaltLength = 490;

        // If we don't specify the salt size, it should be maximum possible; in our specific case, it's 490.
        assertTrue(publicKey.checkSignature(message, signatureExpected, HashType.SHA1, expectedSaltLength));
        assertTrue(publicKey.checkSignature(message, signatureExpected, HashType.SHA1));
        // It should NOT be 0 by default!
        assertFalse(publicKey.checkSignature(message, signatureExpected, HashType.SHA1, 0));
        // Nevertheless, a signature made with the default parameters
        // should be checkable with the default parameters.
        assertTrue(publicKey.checkSignature(message, privateKey.sign(message, HashType.SHA1), HashType.SHA1));
    }

    /**
     * Test {@link RSAOAEPPublicKey#toHash}.
     */
    @Test
    public void toHash() throws Exception {
        // Test sample RSA vectors.
        AbstractPublicKey rsaPublicKey = oaepSpec.getPublicKey();
        Map mapRSA = rsaPublicKey.toHash();
        assertArrayEquals((byte[]) mapRSA.get("n"), oaepSpec.n);
        assertArrayEquals((byte[]) mapRSA.get("e"), oaepSpec.e);
        assertFalse(mapRSA.containsKey("mgf1Hash")); // SHA-1 is the default value

        // Test random public key.
        AbstractPublicKey goodPublicKey1 = new RSAOAEPPublicKey(
                BigIntegers.asUnsignedByteArray(randomPublicKey1.getModulus()),
                BigIntegers.asUnsignedByteArray(randomPublicKey1.getExponent()),
                HashType.SHA512, HashType.SHA512, new SecureRandom());
        Map map1 = goodPublicKey1.toHash();
        assertArrayEquals((byte[]) map1.get("n"), BigIntegers.asUnsignedByteArray(randomPublicKey1.getModulus()));
        assertArrayEquals((byte[]) map1.get("e"), BigIntegers.asUnsignedByteArray(randomPublicKey1.getExponent()));
        assertEquals(map1.get("mgf1Hash"), "SHA-512");
    }

    /**
     * Test {@link RSAOAEPPublicKey#toHash} where some data is default.
     */
    @Test
    public void toHashWithDefaultData() throws Exception {
        // Test random public key.
        AbstractPublicKey goodPublicKey1 = new RSAOAEPPublicKey(
                BigIntegers.asUnsignedByteArray(randomPublicKey1.getModulus()),
                BigIntegers.asUnsignedByteArray(randomPublicKey1.getExponent()),
                HashType.SHA1, HashType.SHA1, new SecureRandom());
        Map map1 = goodPublicKey1.toHash();
        assertArrayEquals((byte[]) map1.get("n"), BigIntegers.asUnsignedByteArray(randomPublicKey1.getModulus()));
        assertArrayEquals((byte[]) map1.get("e"), BigIntegers.asUnsignedByteArray(randomPublicKey1.getExponent()));
        // With the default values (SHA-256 for hash, SHA-1 for MGF1),
        // hash and mgf1Hash fields should be missing from the hash.
        assertFalse(map1.containsKey("hash"));
        assertFalse(map1.containsKey("mgf1Hash"));
    }

    /**
     * Test {@link RSAOAEPPublicKey#updateFromHash} on success scenarios,
     * using RSA spec vectors.
     */
    @Test
    public void updateFromHashGoodRSASpec() throws Exception {
        Map mapRSA = new HashMap<String, Object>() {{
            put("n", oaepSpec.n);
            put("e", oaepSpec.e);
            put("hash", "SHA-1");
            put("mgf1Hash", "SHA-1");
        }};
        AbstractPublicKey rsaPublicKey = new RSAOAEPPublicKey();
        rsaPublicKey.updateFromHash(mapRSA);

        // We've read the public key from hash;
        // but, to test it over the RSA spec vectors,
        // we need to hack into the state.rng (and substitute it with our custom one),
        // even though it is declared as final.
        RSAOAEPPublicKey.State state = ((RSAOAEPPublicKey) rsaPublicKey).state;
        Field rngField = RSAOAEPPublicKey.State.class.getDeclaredField("rng");
        rngField.setAccessible(true);
        rngField.set(state, oaepSpec.getRandSeed());
        // Now we can even test the encryption on the RSA spec vectors.

        assertTrue(rsaPublicKey.isInitialized());
        assertEquals(rsaPublicKey.getBitStrength(), 1024);
        ((RSAOAEPPublicKey) rsaPublicKey).resetEncryptor();
        assertArrayEquals(rsaPublicKey.encrypt(oaepSpec.M), oaepSpec.C);
    }

    /**
     * Test {@link RSAOAEPPublicKey#updateFromHash} on success scenarios,
     * on some sample vector.
     */
    @Test
    public void updateFromHashGoodSample() throws Exception {
        Map map = new HashMap<String, Object>() {{
            put("n", Hex.decode("ad9a9c60d98ee0463f7629675703b23ed4b330ae851c278233a105d120713a6286945f71e6d46f8d9ad0d30e4551a5de8bb9d9a74750645c579c3902266aefecdc111e032048a84ea76220bc570c5cade835909546029a65baff6d29c4207c4b918ff524d80812ecfda388c8cd4ac1878699193040a075bcd4b3987fcbe749bbdfc44665616ff6789f7363b765eb72530e17698d9bd778fd476b3aefc6bce6cec5d44c52e4acb8981390af5174bcb84e3a17e719ba58b93d13bc929d48dbb78d8f5d85002b281159f10e4b801627d3f8acfab100bd2c6380d04ca08bcb9227d84fe282150d71f0660fbbff744800dc6466d47cf5c22d03bac3c0d73dac9bc2a020cb51ca229448c317e73e8f97125a5ff8568b6af0b493b822096021ee1c7eed6d0ad6164e0798055b3b24404983728c2923e9d959c655b2e138650de0ffb5a74b7e612e764fb0e3104ceb5b8df3aaae80a57dbb8f7c305503a51253fc0f1f7a82057c6af262a484f7243f69046c946fd8fc93d71ad90315bfb42467c826dad0253ec83e0ed9b3fa3dca8162f5c691a9e91f69b890b04138de12e0657d8b044f77b455d3ff4257c52e1a33e6792c7a64d9bd8009d79a5fe73dadcd9a80a2f5f51b1c80e6ee41928f0b08a1e9f5d552ec2e5380de554dac97d76aeac2000c4f992eb582532448641d89bbee2334e51d76a3952119093eb5baa545bf1beabd49f5"));
            put("e", Hex.decode("010001"));
            put("mgf1Hash", "SHA-512");
        }};
        AbstractPublicKey publicKey = new RSAOAEPPublicKey();
        publicKey.updateFromHash(map);

        assertTrue(publicKey.isInitialized());
        assertEquals(publicKey.getBitStrength(), 4096);

        RSAOAEPPublicKey.State publicKeyState = ((RSAOAEPPublicKey) publicKey).state;
        assertEquals(publicKeyState.oaepHashType, HashType.SHA1); /* Default for now */
        assertEquals(publicKeyState.mgf1HashType, HashType.SHA512);
    }

    /**
     * Test {@link RSAOAEPPublicKey#updateFromHash} on success scenarios,
     * on some sample vector where some data is default.
     */
    @Test
    public void updateFromHashGoodSampleWithDefaultData() throws Exception {
        Map map = new HashMap<String, byte[]>() {{
            put("n", Hex.decode("ad9a9c60d98ee0463f7629675703b23ed4b330ae851c278233a105d120713a6286945f71e6d46f8d9ad0d30e4551a5de8bb9d9a74750645c579c3902266aefecdc111e032048a84ea76220bc570c5cade835909546029a65baff6d29c4207c4b918ff524d80812ecfda388c8cd4ac1878699193040a075bcd4b3987fcbe749bbdfc44665616ff6789f7363b765eb72530e17698d9bd778fd476b3aefc6bce6cec5d44c52e4acb8981390af5174bcb84e3a17e719ba58b93d13bc929d48dbb78d8f5d85002b281159f10e4b801627d3f8acfab100bd2c6380d04ca08bcb9227d84fe282150d71f0660fbbff744800dc6466d47cf5c22d03bac3c0d73dac9bc2a020cb51ca229448c317e73e8f97125a5ff8568b6af0b493b822096021ee1c7eed6d0ad6164e0798055b3b24404983728c2923e9d959c655b2e138650de0ffb5a74b7e612e764fb0e3104ceb5b8df3aaae80a57dbb8f7c305503a51253fc0f1f7a82057c6af262a484f7243f69046c946fd8fc93d71ad90315bfb42467c826dad0253ec83e0ed9b3fa3dca8162f5c691a9e91f69b890b04138de12e0657d8b044f77b455d3ff4257c52e1a33e6792c7a64d9bd8009d79a5fe73dadcd9a80a2f5f51b1c80e6ee41928f0b08a1e9f5d552ec2e5380de554dac97d76aeac2000c4f992eb582532448641d89bbee2334e51d76a3952119093eb5baa545bf1beabd49f5"));
            put("e", Hex.decode("010001"));
            // Not putting any of the optional fields.
        }};
        AbstractPublicKey publicKey = new RSAOAEPPublicKey();
        publicKey.updateFromHash(map);

        assertTrue(publicKey.isInitialized());
        assertEquals(publicKey.getBitStrength(), 4096);

        RSAOAEPPublicKey.State publicKeyState = ((RSAOAEPPublicKey) publicKey).state;
        assertEquals(publicKeyState.oaepHashType, HashType.SHA1);
        assertEquals(publicKeyState.mgf1HashType, HashType.SHA1);
    }

    /**
     * Test {@link RSAOAEPPublicKey#updateFromHash} on failure scenarios.
     */
    @Test
    public void updateFromHashBad() throws Exception {
        // No data in map
        Map badMapNoData = new HashMap<String, byte[]>();
        AbstractPublicKey publicKeyNoData = new RSAOAEPPublicKey();
        try {
            publicKeyNoData.updateFromHash(badMapNoData);
        } catch (Exception e) {
            assertTrue(e instanceof Hashable.Error);
        }
        assertFalse(publicKeyNoData.isInitialized());

        // Partial data in map
        Map badMapPartialData = new HashMap<String, byte[]>() {{
            put("hash", Hex.decode("5348412d353132"));
            put("mgf1Hash", Hex.decode("5348412d323536"));
        }};
        AbstractPublicKey publicKeyPartialData = new RSAOAEPPublicKey();
        try {
            publicKeyNoData.updateFromHash(badMapPartialData);
        } catch (Exception e) {
            assertTrue(e instanceof Hashable.Error);
        }
        assertFalse(publicKeyPartialData.isInitialized());

        // Bad string
        Map badMapBadString = new HashMap<String, byte[]>() {{
            put("n", Hex.decode("ad9a9c60d98ee0463f7629675703b23ed4b330ae851c278233a105d120713a6286945f71e6d46f8d9ad0d30e4551a5de8bb9d9a74750645c579c3902266aefecdc111e032048a84ea76220bc570c5cade835909546029a65baff6d29c4207c4b918ff524d80812ecfda388c8cd4ac1878699193040a075bcd4b3987fcbe749bbdfc44665616ff6789f7363b765eb72530e17698d9bd778fd476b3aefc6bce6cec5d44c52e4acb8981390af5174bcb84e3a17e719ba58b93d13bc929d48dbb78d8f5d85002b281159f10e4b801627d3f8acfab100bd2c6380d04ca08bcb9227d84fe282150d71f0660fbbff744800dc6466d47cf5c22d03bac3c0d73dac9bc2a020cb51ca229448c317e73e8f97125a5ff8568b6af0b493b822096021ee1c7eed6d0ad6164e0798055b3b24404983728c2923e9d959c655b2e138650de0ffb5a74b7e612e764fb0e3104ceb5b8df3aaae80a57dbb8f7c305503a51253fc0f1f7a82057c6af262a484f7243f69046c946fd8fc93d71ad90315bfb42467c826dad0253ec83e0ed9b3fa3dca8162f5c691a9e91f69b890b04138de12e0657d8b044f77b455d3ff4257c52e1a33e6792c7a64d9bd8009d79a5fe73dadcd9a80a2f5f51b1c80e6ee41928f0b08a1e9f5d552ec2e5380de554dac97d76aeac2000c4f992eb582532448641d89bbee2334e51d76a3952119093eb5baa545bf1beabd49f5"));
            put("e", Hex.decode("010001"));
            put("hash", Hex.decode("5348412d353132"));
            put("mgf1Hash", Hex.decode("00112233"));
        }};
        AbstractPublicKey publicKeyBadString = new RSAOAEPPublicKey();
        try {
            publicKeyNoData.updateFromHash(badMapBadString);
        } catch (Exception e) {
            assertTrue(e instanceof Hashable.Error);
        }
        assertFalse(publicKeyBadString.isInitialized());
    }
}
