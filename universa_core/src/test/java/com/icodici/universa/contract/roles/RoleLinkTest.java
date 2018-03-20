/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import com.icodici.universa.contract.Contract;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import org.junit.Test;

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
        assertEquals( r1, r3);
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

}