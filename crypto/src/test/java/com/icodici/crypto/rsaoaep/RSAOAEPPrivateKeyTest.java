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
import com.icodici.crypto.test.RSAOAEPTestVectors;
import com.icodici.crypto.test.RSASSAPSSTestVectors;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Created by amyodov on 19.04.16.
 */
public class RSAOAEPPrivateKeyTest {

    static int NUMBER_OF_RANDOM_ENCRYPTION_DECRYPTION_CYCLES = 5;

    /**
     * Test vectors from RSA OAEP specification.
     */
    private static final RSAOAEPTestVectors oaepSpec = new RSAOAEPTestVectors();
    /**
     * Test vectors from RSASSA-PSS specification.
     */
    private static final RSASSAPSSTestVectors pssSpec = new RSASSAPSSTestVectors();

    /**
     * Random private key with 4096 bit length and SHA-512 for hash/SHA-224 for MGF-1.
     */
    private static AbstractPrivateKey randomPrivateKey4096;
    /**
     * Random private key with 4096 bit length and SHA-256 for hash/SHA-256 for MGF-1.
     */
    private static AbstractPrivateKey randomPrivateKey4096SHADefault;

    static {
        randomPrivateKey4096 = new RSAOAEPPrivateKey();
        randomPrivateKey4096SHADefault = new RSAOAEPPrivateKey();

        ((RSAOAEPPrivateKey) randomPrivateKey4096).generate(
                4096,
                BigIntegers.asUnsignedByteArray(BigInteger.valueOf(65537)),
                1,
                HashType.SHA512,
                HashType.SHA512);
        ((RSAOAEPPrivateKey) randomPrivateKey4096SHADefault).generate(
                4096,
                BigIntegers.asUnsignedByteArray(BigInteger.valueOf(65537)),
                1,
                HashType.SHA1,
                HashType.SHA1);
    }


    /**
     * Given a private key, get the maximum size of the block, considering some digest will be used.
     * See {@link OAEPEncoding::getInputBlockSize} for details.
     */
    static private int getMaxBlockSizeForPrivateKey(RSAOAEPPrivateKey privateKey) {
        int
                keySize = privateKey.getBitStrength(),
                digestSize = privateKey.state.oaepHashType.makeDigest().getDigestSize(),
                maxBlockSize = keySize / 8 - 2 - 2 * digestSize;
        return maxBlockSize;
    }


    /**
     * Test {@link RSAOAEPPrivateKey#generate} and other basic methods:
     * {@link RSAOAEPPrivateKey#isInitialized}, {@link RSAOAEPPrivateKey#getBitStrength}.
     */
    @org.junit.Test
    public void generateAndBasicMethods() throws Exception {
        assertTrue(randomPrivateKey4096.isInitialized());
        assertEquals(randomPrivateKey4096.getBitStrength(), 4096);
        assertTrue(randomPrivateKey4096.canDecrypt());
    }

    /**
     * Test {@link RSAOAEPPrivateKey#decrypt}.
     */
    @org.junit.Test
    public void decrypt() throws Exception {
        /*
        Test on RSA vectors.
        */
        AbstractPrivateKey rsaPrivateKey = oaepSpec.getPrivateKey();
        AbstractPublicKey rsaPublicKey = rsaPrivateKey.getPublicKey();
        ((RSAOAEPPrivateKey) rsaPrivateKey).resetDecryptor();
        assertArrayEquals(rsaPrivateKey.decrypt(oaepSpec.C), oaepSpec.M);
        ((RSAOAEPPublicKey) rsaPublicKey).resetEncryptor();
        assertArrayEquals(rsaPublicKey.encrypt(oaepSpec.M), oaepSpec.C);

        /*
        Test on some random data.
        */
        AbstractPrivateKey randomPrivateKey = randomPrivateKey4096;
        AbstractPublicKey randomPublicKey = randomPrivateKey.getPublicKey();

        SecureRandom rng = new SecureRandom();

        int
                maxMessageSize = getMaxBlockSizeForPrivateKey((RSAOAEPPrivateKey) randomPrivateKey),
                minMessageSize = maxMessageSize,
                messageSize = (maxMessageSize >= minMessageSize) ?
                        rng.nextInt(maxMessageSize - minMessageSize + 1) + minMessageSize :
                        0;

        /* For our key pair we do multiple encryption-decryption cycles on different data. */
        for (int i = 0; i < NUMBER_OF_RANDOM_ENCRYPTION_DECRYPTION_CYCLES; i++) {
            ((RSAOAEPPrivateKey) randomPrivateKey).resetDecryptor();
            ((RSAOAEPPublicKey) randomPublicKey).resetEncryptor();
            /* Create random message */
            byte[] message = new byte[messageSize];
            rng.nextBytes(message);
            byte[]
                    encrypted = randomPublicKey.encrypt(message),
                    decrypted = randomPrivateKey.decrypt(encrypted);
            assertArrayEquals(decrypted, message);
        }
    }

    /**
     * Test {@link RSAOAEPPrivateKey#getPublicKey}.
     */
    @org.junit.Test
    public void getPublicKey() throws Exception {
        AbstractPublicKey randomPublicKey4096 = randomPrivateKey4096.getPublicKey();
        assertTrue(randomPublicKey4096 instanceof RSAOAEPPublicKey);

        AbstractPrivateKey rsaPrivateKey = oaepSpec.getPrivateKey();
        AbstractPublicKey rsaPublicKey = rsaPrivateKey.getPublicKey();
        ((RSAOAEPPublicKey) rsaPublicKey).resetEncryptor();
        assertArrayEquals(rsaPublicKey.encrypt(oaepSpec.M), oaepSpec.C);
    }

    /**
     * Test {@link RSAOAEPPrivateKey#sign}.
     */
    @org.junit.Test
    public void sign() throws Exception {
        /*
        Test on RSA vectors.
        */
        AbstractPrivateKey rsaPrivateKey = pssSpec.getPrivateKey();
        assertArrayEquals(rsaPrivateKey.sign(pssSpec.M, HashType.SHA1, RSASSAPSSTestVectors.salt), pssSpec.S);
    }

    /**
     * Test {@link RSAOAEPPrivateKey#toHash}.
     */
    @org.junit.Test
    public void toHash() throws Exception {
        /*
        Test sample RSA vectors.
        */
        AbstractPrivateKey rsaPrivateKey = oaepSpec.getPrivateKey();
        Map mapRSA = rsaPrivateKey.toHash();
        assertArrayEquals((byte[]) mapRSA.get("e"), oaepSpec.e);
        assertArrayEquals((byte[]) mapRSA.get("p"), oaepSpec.p);
        assertArrayEquals((byte[]) mapRSA.get("q"), oaepSpec.q);
        assertFalse(mapRSA.containsKey("mgf1Hash")); /* SHA-1 is the default value */

        /*
        Test random key pair (4096 bit, SHA-512).
        */
        AbstractPublicKey randomPublicKey4096 = randomPrivateKey4096.getPublicKey();
        Map
                mapPrivate4096 = randomPrivateKey4096.toHash(),
                mapPublic4096 = randomPublicKey4096.toHash();
        RSAPrivateCrtKeyParameters privateKeyParameters4096 =
                ((RSAOAEPPrivateKey) randomPrivateKey4096).state.keyParameters;
        RSAKeyParameters publicKeyParameters4096 =
                ((RSAOAEPPublicKey) (randomPrivateKey4096.getPublicKey())).state.keyParameters;
        /* Check private key serialization. */
        assertArrayEquals(
                (byte[]) mapPrivate4096.get("e"),
                BigIntegers.asUnsignedByteArray(privateKeyParameters4096.getPublicExponent()));
        assertArrayEquals(
                (byte[]) mapPrivate4096.get("p"),
                BigIntegers.asUnsignedByteArray(privateKeyParameters4096.getP()));
        assertArrayEquals(
                (byte[]) mapPrivate4096.get("q"),
                BigIntegers.asUnsignedByteArray(privateKeyParameters4096.getQ()));
        assertEquals(mapPrivate4096.get("mgf1Hash"), "SHA-512");
        /* Check public key serialization. */
        assertArrayEquals(
                (byte[]) mapPublic4096.get("n"),
                BigIntegers.asUnsignedByteArray(publicKeyParameters4096.getModulus()));
        assertArrayEquals(
                (byte[]) mapPublic4096.get("e"),
                BigIntegers.asUnsignedByteArray(publicKeyParameters4096.getExponent()));
        assertEquals(mapPublic4096.get("mgf1Hash"), "SHA-512");
    }

    /**
     * Test {@link RSAOAEPPrivateKey#toHash} where some data is default.
     */
    @org.junit.Test
    public void toHashDefaultData() throws Exception {
        /*
        Test random key pair (4096 bit, SHA-256/256).
        */
        AbstractPublicKey randomPublicKey4096SHA256 = randomPrivateKey4096SHADefault.getPublicKey();
        Map
                mapPrivate4096SHA256 = randomPrivateKey4096SHADefault.toHash(),
                mapPublic4096SHA256 = randomPublicKey4096SHA256.toHash();
        RSAPrivateCrtKeyParameters privateKeyParameters4096SHA256 =
                ((RSAOAEPPrivateKey) randomPrivateKey4096SHADefault).state.keyParameters;
        RSAKeyParameters publicKeyParameters4096 =
                ((RSAOAEPPublicKey) (randomPrivateKey4096SHADefault.getPublicKey())).state.keyParameters;

        /* Check private key serialization. */
        assertArrayEquals(
                (byte[]) mapPrivate4096SHA256.get("e"),
                BigIntegers.asUnsignedByteArray(privateKeyParameters4096SHA256.getPublicExponent()));
        assertArrayEquals(
                (byte[]) mapPrivate4096SHA256.get("p"),
                BigIntegers.asUnsignedByteArray(privateKeyParameters4096SHA256.getP()));
        assertArrayEquals(
                (byte[]) mapPrivate4096SHA256.get("q"),
                BigIntegers.asUnsignedByteArray(privateKeyParameters4096SHA256.getQ()));
        /* With the default values (SHA-1), hash and mgf1Hash fields should be missing from the hash. */
        assertFalse(mapPrivate4096SHA256.containsKey("mgf1Hash"));

        /* Check public key serialization. */
        assertArrayEquals(
                (byte[]) mapPublic4096SHA256.get("n"),
                BigIntegers.asUnsignedByteArray(publicKeyParameters4096.getModulus()));
        assertArrayEquals(
                (byte[]) mapPublic4096SHA256.get("e"),
                BigIntegers.asUnsignedByteArray(publicKeyParameters4096.getExponent()));
        /* With the default values (SHA-256), hash and mgf1Hash fields should be missing from the hash. */
        assertFalse(mapPublic4096SHA256.containsKey("mgf1Hash"));
    }

    /**
     * Test {@link RSAOAEPPrivateKey#updateFromHash} on success scenarios,
     * using RSA spec vectors.
     */
    @org.junit.Test
    public void updateFromHashGoodRSASpec() throws Exception {
        Map mapRSA = new HashMap<String, Object>() {{
            put("e", oaepSpec.e);
            put("p", oaepSpec.p);
            put("q", oaepSpec.q);
            put("hash", "SHA-1");
            put("mgf1Hash", "SHA-1");
        }};
        AbstractPrivateKey rsaPrivateKey = new RSAOAEPPrivateKey();
        rsaPrivateKey.updateFromHash(mapRSA);
        AbstractPublicKey rsaPublicKey = rsaPrivateKey.getPublicKey();

        /*
        We've read the private key from hash (and created the public one);
        but, to test it over the RSA spec vectors,
        we need to hack into the state.rng (and substitute it with our custom one)
        of both keys, even though they are declared as final.
        */
        /* Here we fix public key. */
        RSAOAEPPublicKey.State publicState = ((RSAOAEPPublicKey)rsaPublicKey).state;
        Field publicRngField = RSAOAEPPublicKey.State.class.getDeclaredField("rng");
        publicRngField.setAccessible(true);
        publicRngField.set(publicState, oaepSpec.getRandSeed());
        /* Here we fix private key. */
        RSAOAEPPrivateKey.State privateState = ((RSAOAEPPrivateKey)rsaPrivateKey).state;
        Field privateRngField = RSAOAEPPrivateKey.State.class.getDeclaredField("rng");
        privateRngField.setAccessible(true);
        privateRngField.set(privateState, pssSpec.getRandSalt());
        /*
        Now we can even test the encryption/decryption on the RSA OAEP spec vectors,
        as well as signing.
        */

        assertTrue(rsaPrivateKey.isInitialized());
        assertEquals(rsaPrivateKey.getBitStrength(), 1024);
        ((RSAOAEPPrivateKey) rsaPrivateKey).resetDecryptor();
        assertArrayEquals(rsaPrivateKey.decrypt(oaepSpec.C), oaepSpec.M);
        /*
        Also test that the public key counterpart is also deserialized properly
        and is capable of encrypting the data (after deserialization) as needed.
        */
        assertTrue(rsaPublicKey.isInitialized());
        assertEquals(rsaPublicKey.getBitStrength(), 1024);
        ((RSAOAEPPublicKey) rsaPublicKey).resetEncryptor();
        assertArrayEquals(rsaPublicKey.encrypt(oaepSpec.M), oaepSpec.C);
    }

    /**
     * Test {@link RSAOAEPPrivateKey#updateFromHash} on success scenarios,
     * on some sample vector.
     */
    @org.junit.Test
    public void updateFromHashGoodSample() throws Exception {
        Map map = new HashMap<String, Object>() {{
            put("e", Hex.decode("010001"));
            put("p", Hex.decode("d4f56e648a7fe26ab052144abfd9a9cdb3ebf220ac05c3e3eb13e42c245ab0578e185a8782bf9b4c1fcc1f577a01cf9c86afef7b4fbb22ecf4fee4409f94c40da7527e0a084ae274494826a7352de4611d36b21bd3d43b4684d997dffff76765b2033a2dd949ccc5521bf1b2889c75a4ce5ce7eb09269dfeaa1f859449c4f359fcbbfc6a00593c1948665c494e7c7edd185c541a7709de5e0528345d0fc31bd214b4d36a7f96cea8fc003730fbeff97bb14267c14dad794d4f949a65fa10fcbc27d1df170128c42d294161ccdaec5e4886da42920d4f6d89dad7a038625111cd75f5d0268009d4fed07adb08da146729e3bf8e882646ad97df0a1e9391656951"));
            put("q", Hex.decode("c7524e21b6d44fe1e7467168b5888047216584821d4b12370f5ca42eefdf729c9f328fe4eaad06ae52aff40bf099d5e8383b34a6a5ea6d52fadf1178546b7afa48eee25801015fe174294a59c0f3fe24f8c3dcdc1572c96b602235e4c26e0fa85c2e9852f529574afb7ccc53ba47c6705de1170bd5b41cd8ca80efd1c324e1a5091a0a5a895d00b857185fd73fd284da14503b6b48a5ee165ce981732ae163d21ae4426204bb8a7e23c6793d4e9c9501a57abbf5457f745ff62e78d8bb250fe41bec2b3c6f77e64876a1d63d0b19ead7e393c7507358b5dbc4ce2c21554c00aa8e8d38c78e72ce0ed306e9c3ae0cd4cc1e92842caf7b70e883daf43b0cf25267"));
            put("mgf1Hash", "SHA-512");
        }};
        AbstractPrivateKey privateKey = new RSAOAEPPrivateKey();
        privateKey.updateFromHash(map);
        AbstractPublicKey publicKey = privateKey.getPublicKey();

        assertTrue(privateKey.isInitialized());
        assertEquals(privateKey.getBitStrength(), 4096);
        /*
        Also test that the public key counterpart is also deserialized properly
        and is capable of encrypting the data (after deserialization) as needed.
        */
        assertTrue(publicKey.isInitialized());
        assertEquals(publicKey.getBitStrength(), 4096);

        RSAOAEPPublicKey.State publicKeyState = ((RSAOAEPPublicKey)publicKey).state;
        assertEquals(publicKeyState.oaepHashType, HashType.SHA1); /* Default */
        assertEquals(publicKeyState.mgf1HashType, HashType.SHA512);

        RSAOAEPPrivateKey.State privateKeyState = ((RSAOAEPPrivateKey)privateKey).state;
        assertEquals(privateKeyState.oaepHashType, HashType.SHA1); /* Default */
        assertEquals(privateKeyState.mgf1HashType, HashType.SHA512);
    }

    /**
     * Test {@link RSAOAEPPrivateKey#updateFromHash} on success scenarios,
     * on some sample vector where some data is default.
     */
    @org.junit.Test
    public void updateFromHashGoodSampleWithDefaultData() throws Exception {
        Map map = new HashMap<String, byte[]>() {{
            put("e", Hex.decode("010001"));
            put("p", Hex.decode("d4f56e648a7fe26ab052144abfd9a9cdb3ebf220ac05c3e3eb13e42c245ab0578e185a8782bf9b4c1fcc1f577a01cf9c86afef7b4fbb22ecf4fee4409f94c40da7527e0a084ae274494826a7352de4611d36b21bd3d43b4684d997dffff76765b2033a2dd949ccc5521bf1b2889c75a4ce5ce7eb09269dfeaa1f859449c4f359fcbbfc6a00593c1948665c494e7c7edd185c541a7709de5e0528345d0fc31bd214b4d36a7f96cea8fc003730fbeff97bb14267c14dad794d4f949a65fa10fcbc27d1df170128c42d294161ccdaec5e4886da42920d4f6d89dad7a038625111cd75f5d0268009d4fed07adb08da146729e3bf8e882646ad97df0a1e9391656951"));
            put("q", Hex.decode("c7524e21b6d44fe1e7467168b5888047216584821d4b12370f5ca42eefdf729c9f328fe4eaad06ae52aff40bf099d5e8383b34a6a5ea6d52fadf1178546b7afa48eee25801015fe174294a59c0f3fe24f8c3dcdc1572c96b602235e4c26e0fa85c2e9852f529574afb7ccc53ba47c6705de1170bd5b41cd8ca80efd1c324e1a5091a0a5a895d00b857185fd73fd284da14503b6b48a5ee165ce981732ae163d21ae4426204bb8a7e23c6793d4e9c9501a57abbf5457f745ff62e78d8bb250fe41bec2b3c6f77e64876a1d63d0b19ead7e393c7507358b5dbc4ce2c21554c00aa8e8d38c78e72ce0ed306e9c3ae0cd4cc1e92842caf7b70e883daf43b0cf25267"));
            /* Not putting any of the optional fields. */
        }};
        AbstractPrivateKey privateKey = new RSAOAEPPrivateKey();
        privateKey.updateFromHash(map);
        AbstractPublicKey publicKey = privateKey.getPublicKey();

        assertTrue(privateKey.isInitialized());
        assertEquals(privateKey.getBitStrength(), 4096);
        /*
        Also test that the public key counterpart is also deserialized properly
        and is capable of encrypting the data (after deserialization) as needed.
        */
        assertTrue(publicKey.isInitialized());
        assertEquals(publicKey.getBitStrength(), 4096);

        RSAOAEPPublicKey.State publicKeyState = ((RSAOAEPPublicKey)publicKey).state;
        assertEquals(publicKeyState.oaepHashType, HashType.SHA1);
        assertEquals(publicKeyState.mgf1HashType, HashType.SHA1);

        RSAOAEPPrivateKey.State privateKeyState = ((RSAOAEPPrivateKey)privateKey).state;
        assertEquals(privateKeyState.oaepHashType, HashType.SHA1);
        assertEquals(privateKeyState.mgf1HashType, HashType.SHA1);
    }
}