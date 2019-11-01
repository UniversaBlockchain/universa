package com.icodici.universa.contract.permissions;

import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.RoleLink;
import net.sergeych.tools.Binder;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChangeRolePermissionTest {

    @Test
    public void changeAllowed() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.addRole(new RoleLink("custom_role",contract,"owner"));
        contract.addPermission(new ChangeRolePermission(new RoleLink("@owner",contract,"owner"),"custom_role"));
        contract.seal();
        contract = contract.createRevision(TestKeys.privateKey(0));
        contract.addRole(new RoleLink("custom_role",contract,"issuer"));
        contract.seal();
        contract.check();
        assertTrue(contract.isOk());
    }


    @Test
    public void changeDeclied() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.addRole(new RoleLink("custom_role",contract,"owner"));
        contract.addPermission(new ChangeRolePermission(new RoleLink("@owner",contract,"owner"),"custom_role1"));
        contract.seal();
        contract = contract.createRevision(TestKeys.privateKey(0));
        contract.addRole(new RoleLink("custom_role",contract,"issuer"));
        contract.seal();
        contract.check();
        assertTrue(!contract.isOk());
    }


    @Test
    public void addAllowed() throws Exception {
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.addPermission(new ChangeRolePermission(new RoleLink("@owner",contract,"owner"),"custom_role"));
        contract.getStateData().put("foo",true);
        contract.getDefinition().getData().put("bar",false);
        contract.seal();
        contract = Contract.fromPackedTransaction(contract.getPackedTransaction()).createRevision(TestKeys.privateKey(0));
        contract.addRole(new RoleLink("custom_role",contract,"issuer"));
        contract.seal();
        contract.check();
        assertTrue(contract.isOk());
    }
}
