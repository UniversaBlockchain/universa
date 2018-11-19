package com.icodici.universa.contract;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.permissions.RevokePermission;
import com.icodici.universa.contract.permissions.SplitJoinPermission;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.icodici.universa.contract.Reference.conditionsModeType.all_of;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoleReferencesTest {

    private PrivateKey key1;
    private PrivateKey key2;
    private PrivateKey key3;
    private PrivateKey key4;

    {
        try {
            key1 = TestKeys.privateKey(11);
            key2 = TestKeys.privateKey(12);
            key3 = TestKeys.privateKey(13);
            key4 = TestKeys.privateKey(14);
        } catch (EncryptionError encryptionError) {
            encryptionError.printStackTrace();
        }
    }


    @Test
    public void revokeSameNameInvalidRef() throws Exception {
        Contract referencedContract = new Contract(key1);
        referencedContract.seal();
        Contract referencedContract2 = new Contract(key1);
        referencedContract2.seal();


        Contract revokingContract = new Contract(key2);
        Reference revokeReference = new Reference(revokingContract);
        revokeReference.setName("ref1");
        ArrayList<String> revokeCondtitions = new ArrayList<>();
        revokeCondtitions.add("ref.id=="+referencedContract.getId().toBase64String());
        revokeReference.setConditions(Binder.of("all_of",revokeCondtitions));
        revokingContract.addReference(revokeReference);
        SimpleRole role = new SimpleRole("@revoke");
        revokingContract.registerRole(role);
        role.addRequiredReference(revokeReference.getName(), Role.RequiredMode.ALL_OF);
        RevokePermission permission = new RevokePermission(role);
        revokingContract.addPermission(permission);
        revokingContract.seal();


        Contract transactionRoot = new Contract(key3);
        transactionRoot.addRevokingItems(revokingContract);
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref1");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id=="+referencedContract2.getId().toBase64String());
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.getIssuer().addRequiredReference("ref1", Role.RequiredMode.ALL_OF);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract2);

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        assertFalse(transactionRoot.check());

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);
        assertTrue(transactionRoot.check());
        
    }

    @Test
    public void revokeSameNameInvalidRefNoRole() throws Exception {
        Contract referencedContract = new Contract(key1);
        referencedContract.seal();
        Contract referencedContract2 = new Contract(key1);
        referencedContract2.seal();


        Contract revokingContract = new Contract(key2);
        Reference revokeReference = new Reference(revokingContract);
        revokeReference.setName("ref1");
        ArrayList<String> revokeCondtitions = new ArrayList<>();
        revokeCondtitions.add("ref.id=="+referencedContract.getId().toBase64String());
        revokeReference.setConditions(Binder.of("all_of",revokeCondtitions));
        revokingContract.addReference(revokeReference);
        SimpleRole role = new SimpleRole("@revoke");
        revokingContract.registerRole(role);
        role.addRequiredReference(revokeReference.getName(), Role.RequiredMode.ALL_OF);
        RevokePermission permission = new RevokePermission(role);
        revokingContract.addPermission(permission);
        revokingContract.seal();


        Contract transactionRoot = new Contract(key3);
        transactionRoot.addRevokingItems(revokingContract);
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref1");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id=="+referencedContract2.getId().toBase64String());
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract2);

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        assertFalse(transactionRoot.check());

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);
        assertTrue(transactionRoot.check());

    }

    @Test
    public void revokeDifferentNameValidRef() throws Exception {
        Contract referencedContract = new Contract(key1);
        referencedContract.seal();


        Contract revokingContract = new Contract(key2);
        Reference revokeReference = new Reference(revokingContract);
        revokeReference.setName("ref1");
        ArrayList<String> revokeCondtitions = new ArrayList<>();
        revokeCondtitions.add("ref.id=="+referencedContract.getId().toBase64String());
        revokeReference.setConditions(Binder.of("all_of",revokeCondtitions));
        revokingContract.addReference(revokeReference);
        SimpleRole role = new SimpleRole("@revoke");
        revokingContract.registerRole(role);
        role.addRequiredReference(revokeReference.getName(), Role.RequiredMode.ALL_OF);
        RevokePermission permission = new RevokePermission(role);
        revokingContract.addPermission(permission);
        revokingContract.seal();


        Contract transactionRoot = new Contract(key3);
        transactionRoot.addRevokingItems(revokingContract);
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref2");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id=="+referencedContract.getId().toBase64String());
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        transactionRoot.check();
        assertTrue(transactionRoot.isOk());
    }

    @Test
    public void revokeDifferentNameValidRefRoleNotRegistered() throws Exception {
        Contract referencedContract = new Contract(key1);
        referencedContract.seal();


        Contract revokingContract = new Contract(key2);
        Reference revokeReference = new Reference(revokingContract);
        revokeReference.setName("ref1");
        ArrayList<String> revokeCondtitions = new ArrayList<>();
        revokeCondtitions.add("ref.id=="+referencedContract.getId().toBase64String());
        revokeReference.setConditions(Binder.of("all_of",revokeCondtitions));
        revokingContract.addReference(revokeReference);
        SimpleRole role = new SimpleRole("@revoke");
        role.addRequiredReference(revokeReference.getName(), Role.RequiredMode.ALL_OF);
        RevokePermission permission = new RevokePermission(role);
        revokingContract.addPermission(permission);
        revokingContract.seal();


        Contract transactionRoot = new Contract(key3);
        transactionRoot.addRevokingItems(revokingContract);
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref2");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id=="+referencedContract.getId().toBase64String());
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        transactionRoot.check();
        assertTrue(transactionRoot.isOk());
    }

    @Test
    public void joinSameNameInvalidRef() throws Exception {
        Contract referencedContract = new Contract(key1);
        referencedContract.seal();
        Contract referencedContract2 = new Contract(key1);
        referencedContract2.seal();


        Contract revokingContract = new Contract(key2);
        Reference revokeReference = new Reference(revokingContract);
        revokeReference.setName("ref1");
        ArrayList<String> revokeCondtitions = new ArrayList<>();
        revokeCondtitions.add("ref.id=="+referencedContract.getId().toBase64String());
        revokeReference.setConditions(Binder.of("all_of",revokeCondtitions));
        revokingContract.addReference(revokeReference);
        SimpleRole role = new SimpleRole("@revoke");
        revokingContract.registerRole(role);
        role.addRequiredReference(revokeReference.getName(), Role.RequiredMode.ALL_OF);
        revokingContract.getStateData().set("amount","100");
        revokingContract.getStateData().set("join_by","QQQ");

        Binder params = Binder.of("field_name", "amount", "join_match_fields",asList("state.data.join_by"));
        SplitJoinPermission revokingPermission = new SplitJoinPermission(role,params);
        revokingContract.addPermission(revokingPermission);
        revokingContract.seal();


        Contract transactionRoot = new Contract(key3);
        transactionRoot.getStateData().set("amount","100");
        transactionRoot.getStateData().set("join_by","QQQ");
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref1");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id=="+referencedContract2.getId().toBase64String());
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.getIssuer().addRequiredReference("ref1", Role.RequiredMode.ALL_OF);

        params = Binder.of("field_name", "amount", "join_match_fields",asList("state.data.join_by"));
        RoleLink roleLink = new RoleLink("@issuer","issuer");
        transactionRoot.registerRole(roleLink);
        SplitJoinPermission rootPermission = new SplitJoinPermission(roleLink,params);
        transactionRoot.addPermission(rootPermission);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract2);
        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        assertTrue(transactionRoot.check());

        transactionRoot = transactionRoot.createRevision(key3);
        transactionRoot.addRevokingItems(revokingContract);
        transactionRoot.getStateData().set("amount",200);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract2);
        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        assertFalse(transactionRoot.check());

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);
        assertTrue(transactionRoot.check());
    }

    @Test
    public void joinDifferentNameValidRef() throws Exception {
        Contract referencedContract = new Contract(key1);
        referencedContract.seal();


        Contract revokingContract = new Contract(key2);
        Reference revokeReference = new Reference(revokingContract);
        revokeReference.setName("ref1");
        ArrayList<String> revokeCondtitions = new ArrayList<>();
        revokeCondtitions.add("ref.id=="+referencedContract.getId().toBase64String());
        revokeReference.setConditions(Binder.of("all_of",revokeCondtitions));
        revokingContract.addReference(revokeReference);
        SimpleRole role = new SimpleRole("@revoke");
        revokingContract.registerRole(role);
        role.addRequiredReference(revokeReference.getName(), Role.RequiredMode.ALL_OF);
        revokingContract.getStateData().set("amount","100");
        revokingContract.getStateData().set("join_by","QQQ");

        Binder params = Binder.of("field_name", "amount", "join_match_fields",asList("state.data.join_by"));
        SplitJoinPermission revokingPermission = new SplitJoinPermission(role,params);
        revokingContract.addPermission(revokingPermission);
        revokingContract.seal();


        Contract transactionRoot = new Contract(key3);
        transactionRoot.getStateData().set("amount","100");
        transactionRoot.getStateData().set("join_by","QQQ");
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref2");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id=="+referencedContract.getId().toBase64String());
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.getIssuer().addRequiredReference("ref2", Role.RequiredMode.ALL_OF);

        params = Binder.of("field_name", "amount", "join_match_fields",asList("state.data.join_by"));
        RoleLink roleLink = new RoleLink("@issuer","issuer");
        transactionRoot.registerRole(roleLink);
        SplitJoinPermission rootPermission = new SplitJoinPermission(roleLink,params);
        transactionRoot.addPermission(rootPermission);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);
        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        assertTrue(transactionRoot.check());

        transactionRoot = transactionRoot.createRevision(key3);
        transactionRoot.addRevokingItems(revokingContract);
        transactionRoot.getStateData().set("amount",200);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);
        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        assertTrue(transactionRoot.check());

    }

    @Test
    public void newItemSameNameInvalidRef() throws Exception {
        Contract referencedContract = new Contract(key1);
        referencedContract.seal();
        Contract referencedContract2 = new Contract(key1);
        referencedContract2.seal();


        Contract newContract = new Contract(key2);
        Reference newContractRef = new Reference(newContract);
        newContractRef.setName("ref1");
        ArrayList<String> revokeCondtitions = new ArrayList<>();
        revokeCondtitions.add("ref.id=="+referencedContract.getId().toBase64String());
        newContractRef.setConditions(Binder.of("all_of",revokeCondtitions));
        newContract.addReference(newContractRef);
        newContract.getIssuer().addRequiredReference("ref1", Role.RequiredMode.ALL_OF);
        newContract.seal();


        Contract transactionRoot = new Contract(key3);
        transactionRoot.addNewItems(newContract);
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref1");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id=="+referencedContract2.getId().toBase64String());
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.getIssuer().addRequiredReference("ref1", Role.RequiredMode.ALL_OF);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract2);

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        assertFalse(transactionRoot.check());

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);
        assertTrue(transactionRoot.check());
    }

    @Test
    public void newItemDifferentNameValidRef() throws Exception {
        Contract referencedContract = new Contract(key1);
        referencedContract.seal();


        Contract newContract = new Contract(key2);
        Reference newReference = new Reference(newContract);
        newReference.setName("ref1");
        ArrayList<String> newConditions = new ArrayList<>();
        newConditions.add("ref.id=="+referencedContract.getId().toBase64String());
        newReference.setConditions(Binder.of("all_of",newConditions));
        newContract.addReference(newReference);
        newContract.getIssuer().addRequiredReference(newReference.getName(), Role.RequiredMode.ALL_OF);
        newContract.seal();

        Contract transactionRoot = new Contract(key3);
        transactionRoot.addNewItems(newContract);
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref2");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id=="+referencedContract.getId().toBase64String());
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        transactionRoot.check();
        assertTrue(transactionRoot.isOk());
    }
}