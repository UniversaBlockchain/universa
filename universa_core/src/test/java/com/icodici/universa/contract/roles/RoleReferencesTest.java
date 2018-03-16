/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.KeyRecord;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

public class RoleReferencesTest {

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
    public void serializeAny() throws Exception {
        SimpleRole sr = new SimpleRole("tr1");
        sr.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));

        sr.addRequiredReference("ref", Role.RequiredMode.ANY_OF);

        Binder serialized = DefaultBiMapper.serialize(sr);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(sr, r1);
        assertTrue(sr.getReferences(Role.RequiredMode.ALL_OF).isEmpty());
        assertEquals(sr.getReferences(Role.RequiredMode.ANY_OF).size(),1);
        assertEquals(sr.getReferences(Role.RequiredMode.ANY_OF).iterator().next(),"ref");
    }


    @Test
    public void serializeAll() throws Exception {
        SimpleRole sr = new SimpleRole("tr1");
        sr.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));

        sr.addRequiredReference("ref", Role.RequiredMode.ALL_OF);

        Binder serialized = DefaultBiMapper.serialize(sr);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(sr, r1);
        assertTrue(sr.getReferences(Role.RequiredMode.ANY_OF).isEmpty());
        assertEquals(sr.getReferences(Role.RequiredMode.ALL_OF).size(),1);
        assertEquals(sr.getReferences(Role.RequiredMode.ALL_OF).iterator().next(),"ref");
    }

    @Test
    public void serializeBoth() throws Exception {
        SimpleRole sr = new SimpleRole("tr1");
        sr.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));

        sr.addRequiredReference("ref1", Role.RequiredMode.ALL_OF);
        sr.addRequiredReference("ref2", Role.RequiredMode.ANY_OF);

        Binder serialized = DefaultBiMapper.serialize(sr);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(sr, r1);
        assertEquals(r1.getReferences(Role.RequiredMode.ALL_OF).size(),1);
        assertEquals(r1.getReferences(Role.RequiredMode.ANY_OF).size(),1);
        assertEquals(r1.getReferences(Role.RequiredMode.ALL_OF).iterator().next(),"ref1");
        assertEquals(r1.getReferences(Role.RequiredMode.ANY_OF).iterator().next(),"ref2");
    }

    @Test
    public void serializeNone() throws Exception {
        SimpleRole sr = new SimpleRole("tr1");
        sr.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));


        Binder serialized = DefaultBiMapper.serialize(sr);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(sr, r1);
        assertTrue(r1.getReferences(Role.RequiredMode.ANY_OF).isEmpty());
        assertTrue(r1.getReferences(Role.RequiredMode.ALL_OF).isEmpty());
    }
}