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
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.KeyRecord;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Reference;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static junit.framework.TestCase.assertFalse;
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

    @Test
    public void  isAllowed()  throws Exception {

        PublicKey key = keys.get(0).getPublicKey();
        Set<PublicKey> keySet = new HashSet<>();
        keySet.add(key);
        Contract contract = new Contract();

        Reference ref1 = new Reference(contract);
        ref1.name = "ref1";
        ref1.setConditions(Binder.of("all_of", Do.listOf("ref.state.data.field==1")));
        contract.addReference(ref1);
        Reference ref2 = new Reference(contract);
        ref2.name = "ref2";
        ref2.setConditions(Binder.of("all_of", Do.listOf("ref.state.data.field==1")));
        contract.addReference(ref2);
        Reference ref3 = new Reference(contract);
        ref3.name = "ref3";
        ref3.setConditions(Binder.of("all_of", Do.listOf("ref.state.data.field==1")));
        contract.addReference(ref3);
        Reference ref4 = new Reference(contract);
        ref4.name = "ref4";
        ref4.setConditions(Binder.of("all_of", Do.listOf("ref.state.data.field==1")));
        contract.addReference(ref4);


        SimpleRole sr = new SimpleRole("tr1");
        sr.addKeyRecord(new KeyRecord(key));
        contract.registerRole(sr);

        assertTrue(!sr.isAllowedForKeys(new HashSet<>()));
        assertTrue(sr.isAllowedForKeys(keySet));
        sr.addRequiredReference("ref1", Role.RequiredMode.ALL_OF);
        sr.addRequiredReference("ref2", Role.RequiredMode.ALL_OF);

        contract.getValidRoleReferences().add("ref1");
        assertFalse(sr.isAllowedForKeys(keySet));
        contract.getValidRoleReferences().add("ref2");
        assertTrue(sr.isAllowedForKeys(keySet));
        sr.addRequiredReference("ref3", Role.RequiredMode.ANY_OF);
        assertFalse(sr.isAllowedForKeys(keySet));
        sr.addRequiredReference("ref4", Role.RequiredMode.ANY_OF);
        sr.addRequiredReference("ref5", Role.RequiredMode.ANY_OF);
        contract.getValidRoleReferences().add("ref4");
        assertTrue(sr.isAllowedForKeys(keySet));
    }
}