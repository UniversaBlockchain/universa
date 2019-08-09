/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import com.icodici.crypto.rsaoaep.RSAOAEPPrivateKey;
import org.junit.Test;
import org.bouncycastle.util.encoders.Hex;

import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * Created by sergeych on 08/12/16.
 */
public class IntegrationTest {
    @Test
    public void testEncryptDecrypt() throws Exception {
        String n = "bb f8 2f 09 06 82 ce 9c 23 38 ac 2b 9d a8 71 f7 36 8d 07 ee d4 10 43 a4" +
                "40 d6 b6 f0 74 54 f5 1f b8 df ba af 03 5c 02 ab 61 ea 48 ce eb 6f cd 48" +
                "76 ed 52 0d 60 e1 ec 46 19 71 9d 8a 5b 8b 80 7f af b8 e0 a3 df c7 37 72" +
                "3e e6 b4 b7 d9 3a 25 84 ee 6a 64 9d 06 09 53 74 88 34 b2 45 45 98 39 4e" +
                "e0 aa b1 2d 7b 61 a5 1f 52 7a 9a 41 f6 c1 68 7f e2 53 72 98 ca 2a 8f 59" +
                "46 f8 e5 fd 09 1d bd cb";

        String e = "11";

        String p = "ee cf ae 81 b1 b9 b3 c9 08 81 0b 10 a1 b5 60 01 99 eb 9f 44 ae f4 fd a4 93 b8" +
                " 1a 9e 3d 84 f6 32 12 4e f0 23 6e 5d 1e 3b 7e 28 fa e7 aa 04 0a 2d 5b 25 21 76 " +
                "45 9d 1f 39 75 41 ba 2a 58 fb 65 99";


        String q = "c9 7f b1 f0 27 f4 53 f6 34 12 33 ea aa d1 d9 35 3f 6c 42 d0 88 66 b1 d0 5a 0f" +
                " 20 35 02 8b 9d 86 98 40 b4 16 66 b4 2e 92 ea 0d a3 b4 32 04 b5 cf ce 33 52 52 " +
                "4d 04 16 a5 a4 41 e7 00 af 46 15 03";

        String message = "d4 36 e9 95 69 fd 32 a7 c8 a0 5b bc 90 d3 2c 49";

        String encrypted_message = "12 53 e0 4d c0 a5 39 7b b4 4a 7a b8 7e 9b f2 a0 39 a3 3d 1e " +
                "99 6f c8 2a 94 cc d3 00 74 c9 5d f7 63 72 20 17 06 9e 52 68 da 5d 1c 0b 4f 87 2c" +
                " f6 53 c1 1d f8 23 14 a6 79 68 df ea e2 8d ef 04 bb 6d 84 b1 c3 1d 65 4a 19 70 " +
                "e5 78 3b d6 eb 96 a0 24 c2 ca 2f 4a 90 fe 9f 2e f5 c9 c1 40 e5 bb 48 da 95 36 ad" +
                " 87 00 c8 4f c9 13 0a de a7 4e 55 8d 51 a7 4d df 85 d8 b5 0d e9 68 38 d6 06 3e " +
                "09 55";

        HashMap<String, Object> privateParams = new HashMap<String, Object>() {{
            put("e", Hex.decode(e));
            put("p", Hex.decode(p));
            put("q", Hex.decode(q));
        }};

        byte[] encrypted_bytes = Hex.decode(encrypted_message);
        RSAOAEPPrivateKey privateKey = new RSAOAEPPrivateKey();
        privateKey.updateFromHash(privateParams);

        byte[] decrypted = privateKey.decrypt(encrypted_bytes);
        assertArrayEquals(Hex.decode(message), decrypted);
    }

    @Test
    public void testSign() throws Exception
    {

        byte[] message = Hex.decode(
                "85 9e ef 2f d7 8a ca 00 30 8b dc 47 11 93 bf 55" +
                        "bf 9d 78 db 8f 8a 67 2b 48 46 34 f3 c9 c2 6e 64" +
                        "78 ae 10 26 0f e0 dd 8c 08 2e 53 a5 29 3a f2 17" +
                        "3c d5 0c 6d 5d 35 4f eb f7 8b 26 02 1c 25 c0 27" +
                        "12 e7 8c d4 69 4c 9f 46 97 77 e4 51 e7 f8 e9 e0" +
                        "4c d3 73 9c 6b bf ed ae 48 7f b5 56 44 e9 ca 74" +
                        "ff 77 a5 3c b7 29 80 2f 6e d4 a5 ff a8 ba 15 98" +
                        "90 fc");
        byte[] e = Hex.decode("01 00 01");
        byte[] p = Hex.decode(
                "d1 7f 65 5b f2 7c 8b 16 d3 54 62 c9 05 cc 04 a2" +
                        "6f 37 e2 a6 7f a9 c0 ce 0d ce d4 72 39 4a 0d f7" +
                        "43 fe 7f 92 9e 37 8e fd b3 68 ed df f4 53 cf 00" +
                        "7a f6 d9 48 e0 ad e7 57 37 1f 8a 71 1e 27 8f 6b");

        byte[] q = Hex.decode(
                "c6 d9 2 b 6f ee 74 14 d1 35 8 c e1 54 6f b6 29 87" +
                        "53 0b 90 bd 15 e0 f1 49 63 a5 e2 63 5 a db 69 34" +
                        "7e c0 c0 1 b 2 a b1 76 3f d8 ac 1 a 59 2f b2 27 57" +
                        "46 3 a 98 24 25 bb 97 a3 a4 37 c5 bf 86 d0 3f 2f");

        byte[] signature = Hex.decode(
                "8d aa 62 7d 3d e7 59 5d 63 05 6 c 7e c6 59 e5 44" +
                        "06 f1 06 10 12 8 b aa e8 21 c8 b2 a0 f3 93 6d 54" +
                        "dc 3 b dc e4 66 89 f6 b7 95 1 b b1 8e 84 05 42 76" +
                        "97 18 d5 71 5d 21 0d 85 ef bb 59 61 92 03 2 c 42" +
                        "be 4 c 29 97 2 c 85 62 75 eb 6d 5 a 45 f0 5f 51 87" +
                        "6f c6 74 3d ed dd 28 ca ec 9 b b3 0e a9 9e 02 c3" +
                        "48 82 69 60 4f e4 97 f7 4 c cd 7 c 7f ca 16 71 89" +
                        "71 23 cb d3 0d ef 5d 54 a2 b5 53 6 a d9 0 a 74 7e ");


        HashMap<String, Object> privateParams = new HashMap<String, Object>() {{
            put("e", e);
            put("p", p);
            put("q", q);
        }};

        RSAOAEPPrivateKey privateKey = new RSAOAEPPrivateKey();
        privateKey.updateFromHash(privateParams);

        AbstractPublicKey publicKey = privateKey.getPublicKey();
        // Не должен проходиться с длиной соли по умолчанию - надо задать явно 20
        // чтобы проходился!
        assertFalse(publicKey.checkSignature(message, signature, HashType.SHA1));
        assertTrue(publicKey.checkSignature(message, signature, HashType.SHA1, 20));
        message[0]++;
        assertFalse(publicKey.checkSignature(message, signature, HashType.SHA1, 20));
    }

}
