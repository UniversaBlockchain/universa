/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto.rsaoaep;

import org.junit.Test;
import org.spongycastle.crypto.AsymmetricBlockCipher;
import org.spongycastle.crypto.AsymmetricCipherKeyPair;
import org.spongycastle.crypto.Digest;
import org.spongycastle.crypto.digests.SHA1Digest;
import org.spongycastle.crypto.digests.SHA224Digest;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.digests.SHA512Digest;
import org.spongycastle.crypto.encodings.OAEPEncoding;
import org.spongycastle.crypto.generators.RSAKeyPairGenerator;
import org.spongycastle.crypto.params.ParametersWithRandom;
import org.spongycastle.crypto.params.RSAKeyGenerationParameters;
import org.spongycastle.crypto.params.RSAKeyParameters;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.SecureRandom;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test SpongyCastle implementation of OEAPEncoding.
 *
 * Created by amyodov on 15.04.16.
 */
public class OAEPEncodingTest {
    /** Test vectors from RSA OAEP specification. */
    private static final RSAOAEPTestVectors oaepSpec = new RSAOAEPTestVectors();

    private static final Digest[] DIGESTS = {new SHA1Digest(), new SHA224Digest(), new SHA256Digest(), new SHA512Digest()};

    private static final int
            NUMBER_OF_RANDOM_ENCRYPTION_KEY_PAIRS = 0, // 4
            NUMBER_OF_RANDOM_ENCRYPTION_DECRYPTION_CYCLES_PER_KEY_PAIR = 0, // 5
            RSA_KEY_CERTAINTY = 0; // 2

    private static final int[] PUBLIC_EXPONENTS = {3, 5, 17, 257, 65537};
    private static final int[] KEY_SIZES = {1024, 2048, 4096};


    @Test
    public void testConstants() throws Exception {
        assertEquals(oaepSpec.nInt, oaepSpec.pInt.multiply(oaepSpec.qInt));
        assertEquals(oaepSpec.dPInt, oaepSpec.dInt.remainder(oaepSpec.pInt.subtract(BigInteger.ONE)));
        assertEquals(oaepSpec.dQInt, oaepSpec.dInt.remainder(oaepSpec.qInt.subtract(BigInteger.ONE)));
        assertEquals(oaepSpec.qInvInt, oaepSpec.qInt.modInverse(oaepSpec.pInt));
    }

    /**
     * Make sure the SpongyCastle OAEPEncoding encodes the block according
     * to the test vectors from RSA OAEP specification.
     *
     * @throws Exception
     */
    @Test
    public void encodeBlock() throws Exception {
        AsymmetricBlockCipher encoder = new OAEPEncoding(RSAOAEPEngine.make());
        encoder.init(true, new ParametersWithRandom(oaepSpec.pubParameters, oaepSpec.getRandSeed()));
        byte[] encoded = encoder.processBlock(oaepSpec.M, 0, oaepSpec.M.length);
        assertArrayEquals(encoded, oaepSpec.C);
    }

    /**
     * Make sure the SpongyCastle OAEPEncoding decodes the block according
     * to the test vectors from RSA OAEP specification.
     *
     * @throws Exception
     */
    @Test
    public void decodeBlock() throws Exception {
        AsymmetricBlockCipher decoder = new OAEPEncoding(RSAOAEPEngine.make());
        decoder.init(false, oaepSpec.privParameters);
        byte[] decoded = decoder.processBlock(oaepSpec.C, 0, oaepSpec.C.length);
        assertArrayEquals(decoded, oaepSpec.M);
    }

    /**
     * Calls the private implementation of MGF function in OAEPEncoding class,
     * using reflection.
     */
    private static byte[] invokeMaskGeneratorFunction1(
            OAEPEncoding oaepEncoding,
            byte[] Z,
            int zOff,
            int zLen,
            int length) {
        Class[] args = {byte[].class, int.class, int.class, int.class};
        Method maskGeneratorFunction1 = null;
        try {
            maskGeneratorFunction1 = OAEPEncoding.class.getDeclaredMethod("maskGeneratorFunction1", args);
            maskGeneratorFunction1.setAccessible(true);
            return (byte[]) maskGeneratorFunction1.invoke(oaepEncoding, Z, zOff, zLen, length);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] MGF(OAEPEncoding oaepEncoding, byte[] Z, int len) {
        return invokeMaskGeneratorFunction1(oaepEncoding, Z, 0, Z.length, len);
    }

    /**
     * Tests the private implementation of maskGeneratorFunction1 using reference vectors
     * from RSA OAEP spec.
     *
     * @throws Exception
     */
    @Test
    public void maskGeneratorFunction1() throws Exception {
        OAEPEncoding oaepEncoder = new OAEPEncoding(RSAOAEPEngine.make(), new SHA1Digest());

        byte[] dbMask_test = MGF(oaepEncoder, oaepSpec.seed, 107);
        assertArrayEquals(dbMask_test, oaepSpec.dbMask);

        byte[] maskedDB_test = new byte[oaepSpec.DB.length];
        for (int i = 0; i < oaepSpec.DB.length; i++) {
            maskedDB_test[i] = (byte) (oaepSpec.DB[i] ^ oaepSpec.dbMask[i]);
        }
        assertArrayEquals(maskedDB_test, oaepSpec.maskedDB);

        byte[] seedMask_test = MGF(oaepEncoder, oaepSpec.maskedDB, 20);
        assertArrayEquals(seedMask_test, oaepSpec.seedMask);

        byte[] maskedSeed_test = new byte[oaepSpec.seed.length];
        for (int i = 0; i < oaepSpec.seed.length; i++) {
            maskedSeed_test[i] = (byte) (oaepSpec.seed[i] ^ oaepSpec.seedMask[i]);
        }
        assertArrayEquals(maskedSeed_test, oaepSpec.maskedSeed);

        ByteArrayOutputStream emStream = new ByteArrayOutputStream();
        emStream.write(oaepSpec.maskedSeed);
        emStream.write(oaepSpec.maskedDB);
        byte[] em_test = emStream.toByteArray();
        assertArrayEquals(em_test, oaepSpec.EM);
    }


    /**
     * Make sure the SpongyCastle OAEPEncoding encoding and decoding operations
     * do not lose or corrupt data.
     * We test it:
     * For each hash function we may use with OEAP (like, SHA1 or SHA512),
     * and for each RSA key size (among 1024, 2048, 4096)
     * we create multiple (NUMBER_OF_RANDOM_ENCRYPTION_KEY_PAIRS) RSA key pairs;
     * for each of the key pair we test encryption-decryption cycle
     * with NUMBER_OF_RANDOM_ENCRYPTION_DECRYPTION_CYCLES_PER_KEY_PAIR random messages
     * (each of the maximum possible size for this cipher configuration)
     * and make sure the result matches the original random message.
     *
     * @throws Exception
     */
    @Test
    public void randomEncodeDecode() throws Exception {
        RSAKeyPairGenerator keyGen = new RSAKeyPairGenerator();

        for (Digest digest : DIGESTS) {
            for (int i = 0; i < NUMBER_OF_RANDOM_ENCRYPTION_KEY_PAIRS; i++) {
                // Create key pair
                SecureRandom rng = new SecureRandom();
                int publicExponent = PUBLIC_EXPONENTS[rng.nextInt(PUBLIC_EXPONENTS.length)];
                int keySize = KEY_SIZES[rng.nextInt(KEY_SIZES.length)];

                keyGen.init(new RSAKeyGenerationParameters(BigInteger.valueOf(publicExponent), new SecureRandom(), keySize, RSA_KEY_CERTAINTY));
                AsymmetricCipherKeyPair keyPair = keyGen.generateKeyPair();

                RSAKeyParameters
                        publicKey = (RSAKeyParameters) keyPair.getPublic(),
                        privateKey = (RSAKeyParameters) keyPair.getPrivate();

                assertEquals(keySize, publicKey.getModulus().bitLength());
                // though actually it is sufficient to keysize <= publicKey.getModulus().bitLength()

                int
                        maxMessageSize = keySize / 8 - 2 - 2 * digest.getDigestSize(),
                        minMessageSize = 1,
                        messageSize = (maxMessageSize >= minMessageSize) ?
                                rng.nextInt(maxMessageSize - minMessageSize + 1) + minMessageSize :
                                0;
                // messageSize may become negative with too small RSA key size and too large digest.
                if (messageSize > 0) {

                    // For each key pair we do multiple encryption-decryption cycles
                    for (int j = 0; j < NUMBER_OF_RANDOM_ENCRYPTION_DECRYPTION_CYCLES_PER_KEY_PAIR; j++) {
                        // Create random message
                        byte[] message = new byte[messageSize];
                        rng.nextBytes(message);

                        AsymmetricBlockCipher
                                encoder = new OAEPEncoding(RSAOAEPEngine.make(), digest),
                                decoder = new OAEPEncoding(RSAOAEPEngine.make(), digest);
                        encoder.init(true, publicKey);
                        decoder.init(false, privateKey);

                        byte[] encoded = encoder.processBlock(message, 0, message.length);
                        byte[] decoded = decoder.processBlock(encoded, 0, encoded.length);

                        // Finally, test the encoding/decoding cycle
                        String assertMessage = String.format(
                                "Digest %s,\n message %s,\n public key %s / %s,\n private key %s / %s",
                                digest, Hex.toHexString(message), publicKey.getExponent(), publicKey.getModulus(), privateKey.getExponent(), privateKey.getModulus());
                        assertArrayEquals(assertMessage, message, decoded);
                    }
                }
            }
        }
    }
}
