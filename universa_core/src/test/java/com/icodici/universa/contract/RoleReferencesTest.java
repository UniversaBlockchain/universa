package com.icodici.universa.contract;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.permissions.RevokePermission;
import com.icodici.universa.contract.permissions.SplitJoinPermission;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.TestKeys;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.util.ArrayList;

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
        revokeCondtitions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        rootConditions.add("ref.id==\""+referencedContract2.getId().toBase64String()+"\"");
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
        revokeCondtitions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        rootConditions.add("ref.id==\""+referencedContract2.getId().toBase64String()+"\"");
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
        revokeCondtitions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        rootConditions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        revokeCondtitions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        rootConditions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        revokeCondtitions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        rootConditions.add("ref.id==\""+referencedContract2.getId().toBase64String()+"\"");
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
        revokeCondtitions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        rootConditions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
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
        revokeCondtitions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
        newContractRef.setConditions(Binder.of("all_of",revokeCondtitions));
        newContract.addReference(newContractRef);
        newContract.getIssuer().addRequiredReference("ref1", Role.RequiredMode.ALL_OF);
        newContract.seal();


        Contract transactionRoot = new Contract(key3);
        transactionRoot.addNewItems(newContract);
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref1");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id==\""+referencedContract2.getId().toBase64String()+"\"");
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
        newConditions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
        newReference.setConditions(Binder.of("all_of",newConditions));
        newContract.addReference(newReference);
        newContract.getIssuer().addRequiredReference(newReference.getName(), Role.RequiredMode.ALL_OF);
        newContract.seal();

        Contract transactionRoot = new Contract(key3);
        transactionRoot.addNewItems(newContract);
        Reference rootReference = new Reference(transactionRoot);
        rootReference.setName("ref2");
        ArrayList<String> rootConditions = new ArrayList<>();
        rootConditions.add("ref.id==\""+referencedContract.getId().toBase64String()+"\"");
        rootReference.setConditions(Binder.of("all_of",rootConditions));
        transactionRoot.addReference(rootReference);
        transactionRoot.seal();
        transactionRoot.getTransactionPack().addReferencedItem(referencedContract);

        transactionRoot = Contract.fromPackedTransaction(transactionRoot.getPackedTransaction());
        transactionRoot.check();
        assertTrue(transactionRoot.isOk());
    }

    @Test
    public void compareRoleLinks() throws Exception {
        PrivateKey pk = new PrivateKey(2048);
        PrivateKey rpk = new PrivateKey(2048);
        Contract c1 = new Contract(pk);
        Contract c2 = new Contract(pk);

        SimpleRole sr1 = new SimpleRole("sr1", c1, Do.listOf(rpk));
        SimpleRole sr2 = new SimpleRole("sr2", c2, Do.listOf(rpk));
        c1.addRole(sr1);
        c2.addRole(sr2);

        RoleLink lr1 = new RoleLink("lr1", c1, "sr1");
        RoleLink lr2 = new RoleLink("lr2", c2, "sr2");
        RoleLink lr3 = new RoleLink("lr3", c2, "sr2");
        RoleLink lr4 = new RoleLink("lr4", c2, "sr2");
        lr3.addRequiredReference("ref2", Role.RequiredMode.ALL_OF);
        lr4.addRequiredReference("ref2", Role.RequiredMode.ALL_OF);
        c1.addRole(lr1);
        c2.addRole(lr2);
        c2.addRole(lr3);
        c2.addRole(lr4);

        Reference ref1 = new Reference(c1);
        ref1.setName("ref1");
        ref1.type = Reference.TYPE_EXISTING_DEFINITION;
        ref1.setConditions(Binder.of(all_of.name(), Do.listOf(
            "this.state.roles.sr1 == ref.state.roles.sr2",
            "this.state.roles.lr1 == ref.state.roles.lr2",
            "ref.state.roles.lr3 == ref.state.roles.lr4")));
        c1.addReference(ref1);

        Reference ref2 = new Reference(c2);
        ref2.setName("ref2");
        ref2.type = Reference.TYPE_EXISTING_DEFINITION;
        c2.addReference(ref2);

        c1.seal();
        c2.seal();

        TransactionPack tp = new TransactionPack();
        tp.setContract(c1);
        tp.addSubItem(c2);
        tp.addReferencedItem(c2);
        c2.setTransactionPack(tp);

        c1.check();
        assert(c1.isOk());
        assert(c1.getReferences().get("ref1").matchingItems.contains(c2));
    }
}