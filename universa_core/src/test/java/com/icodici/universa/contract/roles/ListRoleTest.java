/*
 * Created by Maxim Pogorelov <pogorelovm23@gmail.com>, 9/28/17.
 */

package com.icodici.universa.contract.roles;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.KeyRecord;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static junit.framework.TestCase.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class ListRoleTest {

    static List<PrivateKey> keys = new ArrayList<>();

    static {
        for (int i = 0; i < 4; i++)
            try {
                keys.add(TestKeys.privateKey(i));
            } catch (EncryptionError encryptionError) {
                encryptionError.printStackTrace();
            }
    }

    @Test
    public void shouldPerformRoleWithAnyMode() {
        Contract c = new Contract();

        SimpleRole s1 = new SimpleRole("owner");
        SimpleRole s2 = new SimpleRole("owner2");

        s1.addKeyRecord(new KeyRecord(keys.get(1).getPublicKey()));
        s2.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));

        c.registerRole(s1);
        c.registerRole(s2);

        ListRole roleList = new ListRole("listAnyMode", ListRole.Mode.ANY, Do.listOf(s1, s2));

        assertTrue(roleList.isAllowedForKeys(new HashSet<>(Do.listOf(keys.get(1).getPublicKey()))));
    }

    @Test
    public void shouldNotPerformRoleWithAnyMode() {
        Contract c = new Contract();

        SimpleRole s1 = new SimpleRole("owner");
        SimpleRole s2 = new SimpleRole("owner2");

        s1.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));
        s2.addKeyRecord(new KeyRecord(keys.get(2).getPublicKey()));

        c.registerRole(s1);
        c.registerRole(s2);

        ListRole roleList = new ListRole("listAnyMode");
        roleList.setMode(ListRole.Mode.ANY);
        roleList.addRole(s1)
                .addRole(s2);


        assertFalse(roleList.isAllowedForKeys(new HashSet<>(Do.listOf(keys.get(1).getPublicKey()))));
    }

    @Test
    public void shouldPerformRoleWithAllMode() {
        Contract c = new Contract();

        SimpleRole s1 = new SimpleRole("owner");
        SimpleRole s2 = new SimpleRole("owner2");

        s1.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));
        s2.addKeyRecord(new KeyRecord(keys.get(1).getPublicKey()));

        c.registerRole(s1);
        c.registerRole(s2);

        ListRole roleList = new ListRole("listAllMode");
        roleList.setMode(ListRole.Mode.ALL);
        roleList.addRole(s1)
                .addRole(s2);


        HashSet<AbstractKey> keys = new HashSet<>(Do.listOf(
                ListRoleTest.keys.get(0).getPublicKey(),
                ListRoleTest.keys.get(1).getPublicKey()));

        assertTrue(roleList.isAllowedForKeys(keys));
    }

    @Test
    public void shouldNotPerformRoleWithAllMode() {
        Contract c = new Contract();

        SimpleRole s1 = new SimpleRole("owner");
        SimpleRole s2 = new SimpleRole("owner2");

        s1.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));
        s2.addKeyRecord(new KeyRecord(keys.get(1).getPublicKey()));

        c.registerRole(s1);
        c.registerRole(s2);

        ListRole roleList = new ListRole("listAllMode", ListRole.Mode.ALL, Do.listOf(s1, s2));

        HashSet<AbstractKey> keys = new HashSet<>(Do.listOf(
                ListRoleTest.keys.get(1).getPublicKey()));

        assertFalse(roleList.isAllowedForKeys(keys));
    }

    @Test
    public void shouldNotAllowToSetQuorum() {
        ListRole listRole = new ListRole("roles");

        try {
            listRole.setMode(ListRole.Mode.QUORUM);

            fail("Expected exception to be thrown.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("Only ANY or ALL of the modes should be set."));
        }
    }

    @Test
    public void shouldNotAllowToSetQuorumInConstructor() {
        try {
            new ListRole("roles", ListRole.Mode.QUORUM, Collections.emptyList());

            fail("Expected exception to be thrown.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().equalsIgnoreCase("Only ANY or ALL of the modes should be set."));
        }
    }

    @Test
    public void shouldPerformRoleWithQuorumMode() {
        Contract c = new Contract();

        SimpleRole s1 = new SimpleRole("owner");
        SimpleRole s2 = new SimpleRole("owner2");
        SimpleRole s3 = new SimpleRole("owner3");

        s1.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));
        s2.addKeyRecord(new KeyRecord(keys.get(2).getPublicKey()));
        s3.addKeyRecord(new KeyRecord(keys.get(3).getPublicKey()));

        c.registerRole(s1);
        c.registerRole(s2);
        c.registerRole(s3);

        ListRole roleList = new ListRole("listQuorumMode", 2, Do.listOf(s1, s3, s2));


        HashSet<AbstractKey> keys = new HashSet<>(Do.listOf(
                ListRoleTest.keys.get(0).getPublicKey(),
                ListRoleTest.keys.get(2).getPublicKey()));

        assertTrue(roleList.isAllowedForKeys(keys));
    }

    @Test
    public void shouldNotPerformRoleWithQuorumMode() {
        Contract c = new Contract();

        SimpleRole s1 = new SimpleRole("owner");
        SimpleRole s2 = new SimpleRole("owner2");

        s1.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));
        s2.addKeyRecord(new KeyRecord(keys.get(1).getPublicKey()));

        c.registerRole(s1);
        c.registerRole(s2);

        ListRole roleList = new ListRole("listQuorumMode");
        roleList.setQuorum(2);
        roleList.addAll(Do.listOf(s1, s2));

        c.registerRole(roleList);


        HashSet<AbstractKey> keys = new HashSet<>(Do.listOf(
                ListRoleTest.keys.get(1).getPublicKey()));

        assertFalse(roleList.isAllowedForKeys(keys));
    }

    @Test
    public void serializeAll() throws Exception {
        Contract c = new Contract();

        SimpleRole s1 = new SimpleRole("owner");
        s1.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));

        ListRole roleList = new ListRole("listAllMode", ListRole.Mode.ALL, Do.listOf(s1));

        c.registerRole(s1);
        c.registerRole(roleList);

        Binder serialized = DefaultBiMapper.serialize(roleList);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(r1, roleList);
    }

    @Test
    public void serializeAny() throws Exception {
        SimpleRole s1 = new SimpleRole("owner");
        SimpleRole s2 = new SimpleRole("owner2");

        s1.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));
        s2.addKeyRecord(new KeyRecord(keys.get(2).getPublicKey()));

        ListRole roleList = new ListRole("listAnyMode", ListRole.Mode.ANY, Do.listOf(s1, s2));

        Binder serialized = DefaultBiMapper.serialize(roleList);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(r1, roleList);
    }


    @Test
    public void serializeQuorum() throws Exception {
        SimpleRole s1 = new SimpleRole("owner");
        SimpleRole s2 = new SimpleRole("owner2");
        SimpleRole s3 = new SimpleRole("owner3");

        s1.addKeyRecord(new KeyRecord(keys.get(0).getPublicKey()));
        s2.addKeyRecord(new KeyRecord(keys.get(2).getPublicKey()));
        s3.addKeyRecord(new KeyRecord(keys.get(1).getPublicKey()));

        ListRole roleList = new ListRole("listAnyMode", 2, Do.listOf(s1, s2, s3));

        Binder serialized = DefaultBiMapper.serialize(roleList);
        Role r1 = DefaultBiMapper.deserialize(serialized);
        assertEquals(r1, roleList);
    }

    @Test
    public void serializeContractWithListRole() throws Exception {
        Contract c = Contract.fromDslFile("./src/test_contracts/simple_root_contract.yml");

        SimpleRole s1 = new SimpleRole("role");
        ListRole listRole = new ListRole("owner", 1, Do.listOf(s1));
        c.registerRole(listRole);

        Binder b = BossBiMapper.serialize(c);
        Contract c1 = DefaultBiMapper.deserialize(b);
        assertEquals(c.getRole("owner"), c1.getRole("owner"));
    }

    @Test
    public void serializeWithMoreRoles() {
        SimpleRole s1 = new SimpleRole("s1");
        SimpleRole s2 = new SimpleRole("s2");

        ListRole lr1 = new ListRole("lr1", ListRole.Mode.ALL, Do.listOf(s1, s2));
        ListRole lr2 = new ListRole("lr2", ListRole.Mode.ANY, Do.listOf(s1, s2));

        assertEquals(lr1.getRoles(), lr2.getRoles());

        Binder blr = BossBiMapper.serialize(lr1);
        ListRole slr1 = DefaultBiMapper.deserialize(blr);

        blr = BossBiMapper.serialize(lr1);
        ListRole slr2 = DefaultBiMapper.deserialize(blr);

        assertEquals(slr1.getRoles(), slr2.getRoles());
    }

}
