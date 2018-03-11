/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.node.TestCase;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

public class ExtendedSignatureTest extends TestCase{

    @Test
    public void sign() throws Exception {
        byte[] data = "Hello world".getBytes();
        PrivateKey k = TestKeys.privateKey(3);
        byte [] signature = ExtendedSignature.sign(k, data);
        PublicKey pubKey = k.getPublicKey();
        ExtendedSignature es = ExtendedSignature.verify(pubKey, signature, data);
        assertNotNull(es);
        assertAlmostSame(es.getCreatedAt(), ZonedDateTime.now());
        assertEquals(ExtendedSignature.keyId(k), ExtendedSignature.keyId(pubKey));
        assertEquals(ExtendedSignature.keyId(k), ExtendedSignature.extractKeyId(signature));
    }

    @Test
    public void parallelExecutionSign() throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor();
        ExecutorService multiple = Executors.newCachedThreadPool();

        byte[] data = "Hello world".getBytes();
        PrivateKey k = new PrivateKey(2048);

        // warm up
        for(int i=0; i<200; i++)
            ExtendedSignature.sign(k, data);

        double t1 = parallelize(single, 1, ()-> {
            for(int i=0; i<100; i++)
                ExtendedSignature.sign(k, data);
        });
        System.out.println(t1);
        double t2 = parallelize(multiple, 4, ()-> {
            for(int i=0; i<100; i++)
                ExtendedSignature.sign(k, data);
        });
        System.out.println(t2);
        assertThat(Math.abs(t1-t2), is(lessThan(0.15)));
    }

    @Test
    public void parallelExecutionVerify() throws Exception {
        ExecutorService single = Executors.newSingleThreadExecutor();
        ExecutorService multiple = Executors.newCachedThreadPool();

        byte[] data = "Hello world".getBytes();
        PrivateKey k = new PrivateKey(2048);
        PublicKey key = k.getPublicKey();
        byte[] signature = ExtendedSignature.sign(k, data);

        // warm up
        for(int i=0; i<200; i++)
            ExtendedSignature.sign(k, data);

        double t1 = parallelize(single, 1, ()-> {
            for(int i=0; i<1000; i++)
                ExtendedSignature.verify(key, signature, data);
        });
        System.out.println(t1);
        double t2 = parallelize(multiple, 4, ()-> {
            for(int i=0; i<1000; i++)
                ExtendedSignature.verify(key, signature, data);
        });
        System.out.println(t2);
        assertThat(Math.abs(t1-t2), is(lessThan(0.15)));
    }

    @Test
    public void verifyOldSignatureWithSha512Only() throws Exception {
        String oldSignatureHex = "17 23 73 69 67 6E C4 00 01 D3 2C 73 A2 33 D9 8B 4A 3E 73 02 53 E7 15 D9 C9 9E 8B 08 A3 6A A4 76 ED 5E E3 81 82 1F 47 A2 A1 2D 2B 3F CF 73 90 47 2C 4F 3C 88 76 59 BB 9D 67 02 F1 8F AE 5E 43 3F A3 7E 69 51 4F 7F 32 CC 73 A3 F4 86 EB 49 AA FC 96 97 5B 30 8B 3D F1 29 02 0B 1D 65 D4 DD D1 1F D6 AD 3E AC 1C 5E 0B FC F9 3C 63 11 FE BC 2D 8F CC 35 FD 3E 48 AF 6A B5 98 0A 79 E2 38 A4 46 70 F8 D4 F6 A7 43 3D F3 76 12 65 4D 80 4C 7E 09 6B D1 72 A8 1C E2 60 68 7A FD 9B 1A 9C 78 E3 62 F2 BF FF C0 24 27 5C AE 8E FA 3F B5 59 26 02 D1 0D 55 B6 4C FE 6A 99 58 50 45 9B AB 88 BC E1 1F 26 DA 0D 8D 87 B1 D9 D0 52 EA 20 09 46 0D D0 32 CA 31 53 DA 29 32 7D E4 8E D1 77 94 83 87 68 3A 5B 65 0D 94 E4 A3 8B 7A 14 E2 4B 7B CA F0 30 25 A9 87 51 01 F6 2F FC CD FF 57 BE F4 D5 14 6C 5A 3D 0F 25 38 1E 90 C3 9E D6 06 23 65 78 74 73 BC 82 1F 33 73 68 61 35 31 32 BC 40 B7 F7 83 BA ED 82 97 F0 DB 91 74 62 18 4F F4 F0 8E 69 C2 D5 E5 F7 9A 94 26 00 F9 72 5F 58 CE 1F 29 C1 81 39 BF 80 B0 6C 0F FF 2B DD 34 73 84 52 EC F4 0C 48 8C 22 A7 E3 D8 0C DF 6F 9C 1C 0D 47 53 63 72 65 61 74 65 64 5F 61 74 79 20 28 16 55 85 1B 6B 65 79 BC 21 07 7A 32 A6 07 72 37 6F E4 DA D9 43 55 34 19 45 14 1D 17 14 28 B6 F2 3D CE 11 1D 6F 52 4B DF 60 13";
        String oldZonedDateTimePackedHex = "0F 1B 6E 6F 77 79 20 28 16 55 85";
        byte[] data = "Hello world".getBytes();
        PrivateKey k = TestKeys.privateKey(3);
        byte [] signature = Bytes.fromHex(oldSignatureHex).getData();
        PublicKey pubKey = k.getPublicKey();
        ExtendedSignature es = ExtendedSignature.verify(pubKey, signature, data);
        assertNotNull(es);
        ZonedDateTime savedCreationTime = Boss.unpack(Bytes.fromHex(oldZonedDateTimePackedHex).getData()).getZonedDateTimeOrThrow("now");
        assertAlmostSame(es.getCreatedAt(), savedCreationTime);
        assertEquals(ExtendedSignature.keyId(k), ExtendedSignature.keyId(pubKey));
        assertEquals(ExtendedSignature.keyId(k), ExtendedSignature.extractKeyId(signature));
    }

    public static double parallelize(ExecutorService es,int nThreads,Runnable r) throws ExecutionException, InterruptedException {
        long t = System.nanoTime();
        ArrayList<Future<?>> all = new ArrayList<>();
        for( int i=0; i < nThreads; i++ ) {
            all.add( es.submit(()->r.run()));
        }
        for(Future<?> f: all)
            f.get();
        return (System.nanoTime() - t) * 1e-9;
    }
}