/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.Digest;
import com.icodici.crypto.Sha256;
import com.icodici.crypto.Sha512;
import com.icodici.crypto.SymmetricKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Created by sergeych on 18.12.16.
 */
public class SymmetricKeyTest {

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void etaEncrypt() throws Exception {
        SymmetricKey k = new SymmetricKey();
        byte[] plainText = "Hello, world!".getBytes();
        byte[] cipherText = k.etaEncrypt(plainText);
//        Bytes.dump(cipherText);
        assertEquals(16 + 32 + plainText.length, cipherText.length);
        byte[] decryptedText = k.etaDecrypt(cipherText);
        assertArrayEquals(plainText, decryptedText);

        exception.expect(SymmetricKey.AuthenticationFailed.class);
        cipherText[19] += 1;
        k.etaDecrypt(cipherText);
    }

    @Test
    public void testHashes() throws Exception {
        byte[] valid = Hex.decode("ba7816bf 8f01cfea 414140de 5dae2223 b00361a3 96177a9c b410ff61" +
                                          " f20015ad");
        Digest d = new Sha256();
        d.update('a');
        d.update("bc".getBytes());
        assertArrayEquals(valid, d.digest());

        // And SHA512 too
        valid = Hex.decode("ddaf35a193617aba cc417349ae204131 12e6fa4e89a97ea2 0a9eeee64b55d39a " +
                                   "2192992a274fc1a8 36ba3c23a3feebbd 454d4423643ce80e " +
                                   "2a9ac94fa54ca49f");
        assertArrayEquals(valid, new Sha512().digest("abc"));

    }

    @Test
    public void hmac() throws Exception {
        // Now we check the empty HMAC
        byte[] key = Hex.decode("4a656665");
        byte[] source = Hex.decode("7768617420646f2079612077616e7420666f72206e6f7468696e673f");
        byte []valid = Hex.decode
                ("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");

        SymmetricKey k = new SymmetricKey(key);
        byte[] data = k.etaSign(source);
//        Bytes.dump(key);
//        Bytes.dump(source);
        data = Arrays.copyOfRange(data, data.length - 32, data.length);
//        Bytes.dump(data);
        assertEquals(valid.length, data.length);
        assertArrayEquals(valid, data);

        key = Hex.decode("0102030405060708090a0b0c0d0e0f10" +
                                 "111213141516171819");
        source = Hex.decode("cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd" +
                                    "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd" +
                                    "cdcdcdcdcdcdcdcdcdcdcdcdcdcdcdcd" +
                                    "cdcd");
        valid = Hex.decode("82558a389a443c0ea4cc819899f2083a" +
                                   "85f0faa3e578f8077a2e3ff46729665b");
        k.setKey(key);
        data = k.etaSign(source);
        data = Arrays.copyOfRange(data, data.length - 32, data.length);
        assertEquals(valid.length, data.length);
        assertArrayEquals(valid, data);

        k.setKey(Hex.decode("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                                    "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n" +
                                    "aaaaaa"));
        source = "Test Using Larger Than Block-Size Key - Hash Key First".getBytes();
        valid = Hex.decode("60e431591ee0b67f0d8a26aacbf5b77f\n" +
                                   "                  8e0bc6213728c5140546040f0ee37f54");
        data = k.etaSign(source);
        data = Arrays.copyOfRange(data, data.length - 32, data.length);
        assertEquals(valid.length, data.length);
        assertArrayEquals(valid, data);
    }

    @Test
    public void xor() throws Exception {
        byte[] test = new byte[]{0, 0x55, (byte) 0xFF};
        byte[] src = new byte[]{0, 0x55, (byte) 0xFF};
        test = SymmetricKey.xor(test, 0);
        assertArrayEquals(test, src);
        test = SymmetricKey.xor(test, 0xFF);
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xAA, 0}, test);
        test = SymmetricKey.xor(src, 0x11);
        assertArrayEquals(new byte[]{0x11, (byte) 0x44, (byte) 0xEE}, test);
    }

}