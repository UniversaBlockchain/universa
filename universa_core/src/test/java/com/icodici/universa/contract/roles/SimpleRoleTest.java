/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.KeyRecord;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class SimpleRoleTest {

    static List<PrivateKey> keys = new ArrayList<>();
    static {
        for( int i=0; i<4; i++)
//            keys[i] = new PrivateKey(2048);
            try {
                keys.add(TestKeys.privateKey(i));
            } catch (EncryptionError encryptionError) {
                encryptionError.printStackTrace();
            }
    }

    @Test
    public void serializeOne() throws Exception {
        SimpleRole sr = new SimpleRole("tr1");
        sr.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));
        Binder serialized = DefaultBiMapper.serialize(sr);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(sr, r1);
    }

    @Test
    public void serializeMany() throws Exception {
        SimpleRole sr = new SimpleRole("tr1");
        keys.forEach(k-> sr.addKeyRecord(new KeyRecord(k.getPublicKey())));
        Binder serialized = DefaultBiMapper.serialize(sr);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(sr, r1);
        Set<PublicKey> kk = r1.getKeys();
        keys.forEach(k->assertTrue(kk.contains(k.getPublicKey())));
    }

}