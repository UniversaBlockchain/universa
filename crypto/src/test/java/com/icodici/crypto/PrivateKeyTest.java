/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/**
 * Created by sergeych on 02/12/16.
 */
public class PrivateKeyTest {

    @Test
    public void testKeys() throws Exception {
        // Test vectors are for key #1

        PrivateKey key = (PrivateKey) TestKeys.privateKey(1);
        byte[] encrypted = Bytes.fromBase64(encrypted64).toArray();
        byte[] decrypted = key.decrypt(encrypted);
        assertEquals(plainText, new String(decrypted));

        PublicKey publicKey = key.getPublicKey();
        byte[] encrypted2 = publicKey.encrypt(plainText);
        assertEquals(plainText, new String(key.decrypt(encrypted2)));

        publicKey = new PublicKey(Do.decodeBase64(publicKey64));
        encrypted2 = publicKey.encrypt(plainText);
        assertEquals(plainText, new String(key.decrypt(encrypted2)));
    }

    @Test
    public void serializationTest() throws Exception {
        byte[] packedPublicKey = Do.decodeBase64(publicKey64);
        PublicKey publicKey = new PublicKey(packedPublicKey);
        byte[] packedPublicKey2 = publicKey.pack();
        assertArrayEquals(packedPublicKey, packedPublicKey2);

        byte[] packedPrivateKey = Do.decodeBase64(TestKeys.binaryKeys[3]);
        PrivateKey privateKey = new PrivateKey(packedPrivateKey);
        byte[] packedPrivateKey2 = privateKey.pack();
        assertArrayEquals(packedPrivateKey, packedPrivateKey2);
    }

    @Test
    public void signatureTest() throws Exception {
        PrivateKey privateKey = TestKeys.privateKey(1);
        PublicKey publicKey = privateKey.getPublicKey();
        byte [] signature256 = Do.decodeBase64(signature256_64);
        final byte[] signature512 = Do.decodeBase64(signature512_64);

        assertTrue(publicKey.verify(plainText, signature256, HashType.SHA256));
        assertFalse(publicKey.verify(plainText+"tampered", signature256, HashType.SHA256));
        assertFalse(publicKey.verify(plainText, signature256, HashType.SHA512));

        assertTrue(publicKey.verify(plainText, signature512, HashType.SHA512));
        assertFalse(publicKey.verify(plainText, signature512, HashType.SHA256));
        assertFalse(publicKey.verify(plainText+"tampered", signature512, HashType.SHA512));
    }


    @Test
    public void passwordProtectedKeyTest() throws Exception {
        PrivateKey key = TestKeys.privateKey(3);
        String password = UUID.randomUUID().toString();
        byte[] packed = key.pack();
        byte[] packedWithPassword = key.packWithPassword(password);

        //try unpack password protected
        try {
            new PrivateKey(packedWithPassword);
            fail("exception expected");
        } catch (PrivateKey.PasswordProtectedException e) {
            assertEquals(e.getMessage(),"key is password protected");
        }

        //try unpack password with wrong password
        try {
            PrivateKey.unpackWithPassword(packedWithPassword,UUID.randomUUID().toString());
            fail("exception expected");
        } catch (PrivateKey.PasswordProtectedException e) {
            assertEquals(e.getMessage(),"wrong password");
        }

        //try unpack plain key with password
        assertEquals(PrivateKey.unpackWithPassword(packed,UUID.randomUUID().toString()),key);

        assertEquals(PrivateKey.unpackWithPassword(packedWithPassword,password),key);
    }

    @Test
    public void concurrencyTest() throws Exception {
        PrivateKey privateKey = new PrivateKey(2048);
        PublicKey publicKey = privateKey.getPublicKey();
        ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(128);
        AtomicInteger errorsCount = new AtomicInteger(0);
        for (int i = 0; i < 100000; ++i) {
            executorService.submit(() -> {
                try {
                    byte[] data = Bytes.random(128).getData();
                    byte[] encrypted = publicKey.encrypt(data);
                    byte[] decrypted = privateKey.decrypt(encrypted);
                    assertArrayEquals(data, decrypted);
                } catch (Exception e) {
                    e.printStackTrace();
                    errorsCount.incrementAndGet();
                }
            });
        }
        assertEquals(0, errorsCount.get());
    }

    static String plainText = "FUBAR means Fucked Up Beyoud All Recognition";

    static String encrypted64 =
            "TvBODRFNWiE6lzCcbFrZkQMynCRxXhin2B892hLYYa6HXBWqCb9DspHRpwO/\n" +
                    "5E/Ad0vhwuUP2LyqsWRrD5S4j3IbM2kLdBnZpjU/XnC2X7AUchSKntpgXQ0G\n" +
                    "dUHW7T1hm4YIn2w1VW5F3Anr8MuZ1eOgr7d/GRGAOxv9BV7OE2Ty5y6tYV5I\n" +
                    "Ntlw0XmhdCcKXbMoJ2ySrMxMv9ww2nxoKD9KsVcmI7LjbBvgqdOixb6LYp7B\n" +
                    "8yEdF1E66YH+tOcGIOf5B98DilxaSDTUp74uu1JkvHxAlbiht/cFlpxsi3SJ\n" +
                    "+25x/jo/wC72feNTDHBE/sYAZhnUQLktEyVHPL9Pv608eis2LXacK/ARslMd\n" +
                    "uPk1gBW9KxGNzxVY3Wa74r5mU1iUNUna/jhUcY2vExKoUwgoZYsxc8ZuxV9z\n" +
                    "O5eKzIY4dQJzrHIapj7MGMu305CxvNpLeRIop/TbxyJ0stRkHuFA+ZQorUhl\n" +
                    "GemTx+3sx5tQcqHCXN2EBCxlX2bK0y6pF3ei64mmJLqL3RGbpwWv3B2cw6fV\n" +
                    "oZmdwZkvUAeWLk5R3GklBCfeVzrI6HLRKLLk8+BmK8mMC75w7Suu962nNQ5f\n" +
                    "N184daPskemg1blhZqofqZ4ANDS8EgXcmkkHNbwvj0fWucaVxzwAzsnKKPxE\n" +
                    "hviHSY+0fbfA738vmOQA9gc=\n";
    static String signature256_64 = "bJg9KSSKRGE7UoXx4O+OGQTloguaj/gQ/rbFxLP2Pl0OPTg4pjhuswRAM6MQ\n" +
            "fLfNC4r4J3wvZTy6Y1g8Kp8/vQ5AdFnROhWACNsucKrqL3Mfs7QS+hT/FDCQ\n" +
            "0nkDzAIe96HEknz4FhceWdc4byrRq4UNzhIACwnWwilXfh80KDTwAtkhDrsY\n" +
            "akK7wu275NUrLZ37hhw5iRgCy67D1Ro0hEI7ajWq6NH9QUzhP5QxnTUKlJ4t\n" +
            "pns7QX53QLcvbPPwxkiXpipVcQnnTnwOTgccX2dLLK/BcZvUp0SX+e9u4EY5\n" +
            "iQK5wGEJJGaqpHtfrZvlH4Lf3nsUDcn9nw/I6W7knvvuBZIIoHr7m3fkgRQO\n" +
            "ktxGTIokJ3T3Gkng5F63hot+vdzmbLWG5/lfpWkFVv4/7rH3/yCvb7rrxrOi\n" +
            "MnXxWk29Y9bknJEydQzI0rCP9ZL+HIsJxOy8b20e0rNI02VP10Ls6bvQH0gC\n" +
            "fn+XIst39dhxEN8uojKCrtP8AUpDn8GAnpOhSvUkcf3EwoF9PROPOiQy9cwL\n" +
            "TuB7fCcQ8ywPDV4vV/ck1UCn8vfmvQMeTLOArTdbX56ugdRC9YI35rydAi23\n" +
            "KHYPD4T7aAesaXj3ARV1lcN8T1X/ZwgcUmYhLIPAHS+1EtALSjjNPW1WQ0lu\n" +
            "mZ/Krju0VjBtwIRUOjXMGWg\n";
    static String signature512_64 =
            "krcjOVKXM1FYpCFGWRMhimUsJKoHGzfadqW17SuDmeOeAbqiQ3Ts4crqKIsb\n" +
                    "j7TYqE8NM49JFh4ybGrL7Rqt6VzX3a2sY822z8JV9Evu6Ffsp66I4WEISzBp\n" +
                    "ZZcrph+LiyBXBqOxCS6JbXyKITMX8dLZXdLoYsWmVYQtJcTwmuXwIEB2x4t7\n" +
                    "h8I2W0U2PK57q3r9LWKLwtCSKIBlRFxkCSLrU14LmhOPmgEqX5aeqoJLzrAQ\n" +
                    "sq2Fa6CzJ/sL5H8I6rMXzRMHJwB0TjBfJNYRxN0+0xZjiZwWaS1Avvm7A2ge\n" +
                    "+MNbUG+Mx1YhXw0wPdnipdoFU+nwSLLsmEFGPHIwHe6ioYdG+eT9J8Kpp6uw\n" +
                    "+IXZVbakEBmCKlUQSBFw0xlbc7XgImdob4KxEe2FNIom2Wg+jDYtOfc0YPSi\n" +
                    "QWlovTmGmN6U/KROkg/cLNfHw6TlXfKN1r0HKyAN/eRCZfU9h88/vXCpsOW9\n" +
                    "eSd50Y6p0rtD02zj/d2l+ZfHBmwrEQxXeIUjqQsD/VusBIi6N93IPu3ImiuC\n" +
                    "aZ99e0Un/ocVyc4qxawXbWzeh8YyxzF6TAp+N1Pyu49kvs2ysDfT38B8ecm7\n" +
                    "tUJdkxVP/Oyxo9wchUyallWAhfjytFdzVsa8AwDv6sRsnR0NAGZdHTNwlSQd\n" +
                    "yU/q1rzuYrwWHEykuwk5Ons\n";

    static String publicKey64 = "HggcAQABxAACzFGLks70obrx/j/TpEGbtaCl/jgcfUs2XdZyNDqREjZHSi/f\n" +
            "91nawhtAr0LoPsj/MOQD7TOfrKCrOhX3KiLcgiGEpJSReVkMvVMJjUQ/7WEg\n" +
            "mkciPExiEuGwCFgk1P/X8tSSdTP4mpgTLQcKYbBihzwit65lQRoepqnTPTDF\n" +
            "u+Y7GeBf51iaxQultwTub+kzjQndfp79BxU0ZGEB0Fjmdsm2UDgf9aDNsvEc\n" +
            "MWc3iiVJOVfLOscXcKQ813vGBbQfEcQ3VgwKAnEVTEeC+canMUdyYOczSjgL\n" +
            "gbGXwa9TYI2epFGxNq/fetqeu6RtsKkkZMcoO0ii6zMqicxw7AK4xmrcHiNE\n" +
            "Nl2397rjD+eT427qzJNmOWmsojqGNVayucT/aQ+fh5lPokbFFL7HHJHQ3yZD\n" +
            "aTTaUabUhGZ9Xo9G81mail9SKH39AZ6RnvRlBAakRlf1k0JCatYdM2aLIX/+\n" +
            "XzM8GFjOTLvcu7cdSGvKg/Tu/tggiOoT6LgiiLY5RGgx9h8pjpbr9SgQVu1R\n" +
            "1fPo4lw0E4bGmfSVSj8zqC76+I59eR4xG/u8yUeGU0mvMt2tGlrdr7EP90AV\n" +
            "SaHFO7d3dTPiaeyU5z1vWSdmLEA6BbewVBs6+BbpHalLuriwlf7bsAMlPe/8\n" +
            "uvtBkAV/UjVkZG0/FtnkOjuL4pomlJQrwEc=\n";

}