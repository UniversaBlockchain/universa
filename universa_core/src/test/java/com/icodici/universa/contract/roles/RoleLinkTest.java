/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;

public class RoleLinkTest {
    @Test
    public void resolve() throws Exception {
        Contract c = new Contract();
        SimpleRole s1 = new SimpleRole("owner");
        c.registerRole(s1);
        RoleLink r1 = new RoleLink("lover", "owner");
        c.registerRole(r1);
        RoleLink r2 = r1.linkAs("mucker");
        assertSame(s1, s1.resolve());
        assertSame(s1, r1.resolve());
        assertSame(s1, r2.resolve());
    }

    @Test
    public void detectsCirculars() throws Exception {
        Contract c = new Contract();
        RoleLink r1 = new RoleLink("bar", "foo");
        RoleLink r2 = new RoleLink("foo", "bar");
        c.registerRole(r1);
        c.registerRole(r2);
        assertNull(r2.resolve());
    }

    @Test
    public void equals() throws Exception {
        RoleLink r1 = new RoleLink("name", "target");
        RoleLink r2 = new RoleLink("name", "target1");
        RoleLink r3 = new RoleLink("name1", "target");
        assertTrue(r1.equalsIgnoreName(r3));
        assertFalse(r1.equalsIgnoreName(r2));
        assertFalse(r2.equalsIgnoreName(r3));
        assertNotEquals(r1, r3);
        assertNotEquals(r1, r2);
        assertNotEquals(r2, r3);
    }

    @Test
    public void serialize() throws Exception {
        RoleLink r1 = new RoleLink("name", "target");
        r1.addRequiredReference("ref", Role.RequiredMode.ALL_OF);

        Binder s = DefaultBiMapper.serialize(r1);
        RoleLink r2 = DefaultBiMapper.deserialize(s);
        assertEquals(r1, r2);
        assertEquals(r1.getName(), r2.getName());
    }

    @Test
    public void testGetSimpleAddress() throws Exception {
        Set<Object> keyAddresses = new HashSet<>();
        keyAddresses.add(TestKeys.publicKey(0).getLongAddress());
        SimpleRole sr = new SimpleRole("sr", keyAddresses);
        Contract c = new Contract();
        c.registerRole(sr);
        RoleLink rl = new RoleLink("rl",sr.getName());
        c.registerRole(rl);
        assertEquals(rl.getSimpleAddress(),TestKeys.publicKey(0).getLongAddress());

        rl.addRequiredReference("dummy", Role.RequiredMode.ALL_OF);
        assertNull(rl.getSimpleAddress());
        assertEquals(RoleExtractor.extractSimpleAddress(rl),TestKeys.publicKey(0).getLongAddress());


    }
}