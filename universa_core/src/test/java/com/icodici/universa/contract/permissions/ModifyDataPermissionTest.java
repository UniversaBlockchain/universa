package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractTestBase;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.node.TestCase;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.time.ZonedDateTime;

import static org.junit.Assert.*;

public class ModifyDataPermissionTest extends TestCase {

    @Test
    public void modifyDataAllowed() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("field_to_be_changed", null);
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value2");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());
    }

    @Test
    public void modifyDataDeclined() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value2");
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());
    }

    @Test
    public void modifyDataWhiteList() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("field_to_be_changed", Do.listOf("value2", "value3"));
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value2");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value3");
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.getStateData().set("field_to_be_changed", "value4");
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());
    }

    @Test
    public void modifyExpiresAtAllowed() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("/expires_at", null);
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(ZonedDateTime.now().plusDays(1));
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

    }

    @Test
    public void modifyExpiresAtDeclined() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.getStateData().put("field_to_be_changed", "value1");
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("expires_at", null);
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(ZonedDateTime.now().plusDays(1));
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());

    }

    @Test
    public void modifyExpiresAtWhiteList() throws Exception {
        ZonedDateTime now = ZonedDateTime.now();
        Contract contract = new Contract(TestKeys.privateKey(0));
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(contract.getRole("owner"), new Binder());
        modifyDataPermission.addField("/expires_at", Do.listOf(now.plusDays(1), now.plusDays(2)));
        contract.addPermission(modifyDataPermission);
        contract.seal();

        Contract changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(now.plusDays(1));
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(now.plusDays(2));
        changed.seal();
        changed.check();
        assertTrue(changed.isOk());

        changed = contract.createRevision();
        changed.addSignerKey(TestKeys.privateKey(0));
        changed.setExpiresAt(now.plusDays(3));
        changed.seal();
        changed.check();
        assertTrue(!changed.isOk());
    }

    
}
