package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.HashId;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.roles.*;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.DeferredResult;
import net.sergeych.tools.Do;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class VotingTest extends BaseMainTest {


    @Test
    public void persistentVoting() throws Exception {

        TestSpace ts = prepareTestSpace();
        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));


        Contract contract = new Contract(TestKeys.privateKey(1));
        QuorumVoteRole quorumVoteRole = new QuorumVoteRole("issuer",contract,"this.state.data.list","90%");
        contract.addRole(quorumVoteRole);

        List<KeyAddress> addresses = new ArrayList<>();
        for(int i = 0; i < 20; i++) {
            addresses.add(TestKeys.publicKey(i).getLongAddress());
        }
        contract.getStateData().put("list",addresses);

        contract.seal();



        Contract u = Parcel.createPayment(getApprovedUContract(ts),Do.listOf(ts.myKey),1,false);

        HashId opId = client.initiateVote(u.getPackedTransaction(),contract,"creator",Do.listOf(contract.getId()));
        while(client.getPaidOperationProcessingState(opId).isProcessing()) {
            Thread.sleep(100);
        }

        if(client.getState(u.getId()).state == ItemState.APPROVED) {
            synchronized (ts.uContractLock) {
                ts.uContract = u;
            }
        } else {
            throw new IllegalArgumentException("wrong u");
        }




        ts.shutdown();
        ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));




        for(int j = 0; j < 19; j++) {
            Client keyClient = new Client("test_node_config_v2", null, TestKeys.privateKey(j));
            u = Parcel.createPayment(getApprovedUContract(ts),Do.listOf(ts.myKey),1,false);
            opId = keyClient.voteForContract(u.getPackedTransaction(),contract.getId(),contract.getId());
            while(keyClient.getPaidOperationProcessingState(opId).isProcessing()) {
                Thread.sleep(100);
            }

            if(keyClient.getState(u.getId()).state == ItemState.APPROVED) {
                synchronized (ts.uContractLock) {
                    ts.uContract = u;
                }
            } else {
                throw new IllegalArgumentException("wrong u");
            }
        }


        assertEquals(client.register(contract.getPackedTransaction(),100000).state, ItemState.APPROVED);

        ts.shutdown();
    }


    @Test
    public void persistentVotingFailed() throws Exception {

        TestSpace ts = prepareTestSpace();
        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));


        Contract contract = new Contract(TestKeys.privateKey(1));
        QuorumVoteRole quorumVoteRole = new QuorumVoteRole("issuer",contract,"this.state.data.list","99%");
        contract.addRole(quorumVoteRole);

        List<KeyAddress> addresses = new ArrayList<>();
        for(int i = 0; i < 20; i++) {
            addresses.add(TestKeys.publicKey(i).getLongAddress());
        }
        contract.getStateData().put("list",addresses);

        contract.seal();



        Contract u = Parcel.createPayment(getApprovedUContract(ts),Do.listOf(ts.myKey),1,false);

        HashId opId = client.initiateVote(u.getPackedTransaction(),contract,"creator",Do.listOf(contract.getId()));
        while(client.getPaidOperationProcessingState(opId).isProcessing()) {
            Thread.sleep(100);
        }

        if(client.getState(u.getId()).state == ItemState.APPROVED) {
            synchronized (ts.uContractLock) {
                ts.uContract = u;
            }
        } else {
            throw new IllegalArgumentException("wrong u");
        }




        ts.shutdown();
        ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));




        for(int j = 0; j < 18; j++) {
            Client keyClient = new Client("test_node_config_v2", null, TestKeys.privateKey(j));
            u = Parcel.createPayment(getApprovedUContract(ts),Do.listOf(ts.myKey),1,false);
            opId = keyClient.voteForContract(u.getPackedTransaction(),contract.getId(),contract.getId());
            while(keyClient.getPaidOperationProcessingState(opId).isProcessing()) {
                Thread.sleep(100);
            }

            if(keyClient.getState(u.getId()).state == ItemState.APPROVED) {
                synchronized (ts.uContractLock) {
                    ts.uContract = u;
                }
            } else {
                throw new IllegalArgumentException("wrong u");
            }
        }


        assertEquals(client.register(contract.getPackedTransaction(),100000).state, ItemState.DECLINED);

        ts.shutdown();
    }

    @Test
    public void persistentVotingChangeOwnerReferenced() throws Exception {

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));
        for(int i = 0; i < client.size(); i++) {
            client.getClient(i);
        }

        Contract referenced = new Contract(TestKeys.privateKey(1));
        QuorumVoteRole quorumVoteRole = new QuorumVoteRole("change_owner",referenced,"this.state.data.list","90%");
        referenced.addRole(quorumVoteRole);

        List<KeyAddress> addresses = new ArrayList<>();
        for(int i = 0; i < 20; i++) {
            addresses.add(TestKeys.publicKey(i).getLongAddress());
        }
        referenced.getStateData().put("list",addresses);
        referenced.seal();

        assertEquals(client.register(referenced.getPackedTransaction(),10000).state,ItemState.APPROVED);


        Contract updated = new Contract(TestKeys.privateKey(1));

        Reference reference = new Reference(updated);
        reference.name = "change_owner";
        reference.setConditions(Binder.of("all_of",Do.listOf(
                "ref.id==this.state.data.referenced",
                "this can_play ref.state.roles.change_owner")));
        updated.addReference(reference);

        updated.getStateData().put("referenced",referenced.getId().toBase64String());

        SimpleRole owner = new SimpleRole("owner",updated);
        owner.addRequiredReference("change_owner", Role.RequiredMode.ALL_OF);
        updated.addRole(owner);
        updated.seal();

        assertEquals(client.register(updated.getPackedTransaction(),10000).state,ItemState.APPROVED);


        Contract revision1 = updated.createRevision(TestKeys.privateKey(4));
        revision1.setOwnerKey(TestKeys.publicKey(2).getLongAddress());
        revision1.seal();
        revision1.getTransactionPack().addReferencedItem(referenced);

        Contract revision2 = updated.createRevision(TestKeys.privateKey(4));
        revision2.setOwnerKey(TestKeys.publicKey(3).getLongAddress());
        revision2.seal();
        revision2.getTransactionPack().addReferencedItem(referenced);




        Contract u = Parcel.createPayment(getApprovedUContract(ts),Do.listOf(ts.myKey),1,false);

        HashId opId = client.initiateVote(u.getPackedTransaction(),referenced, "change_owner", Do.listOf(revision1.getId(), revision2.getId()));
        while(client.getPaidOperationProcessingState(opId).isProcessing()) {
            Thread.sleep(100);
        }

        if(client.getState(u.getId()).state == ItemState.APPROVED) {
            synchronized (ts.uContractLock) {
                ts.uContract = u;
            }
        } else {
            throw new IllegalArgumentException("wrong u");
        }




        ts.shutdown();
        ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));



        for(int j = 0; j < 19; j++) {
            Client keyClient = new Client("test_node_config_v2", null, TestKeys.privateKey(j));
            u = Parcel.createPayment(getApprovedUContract(ts),Do.listOf(ts.myKey),1,false);

            opId = keyClient.voteForContract(u.getPackedTransaction(),referenced.getId(),revision2.getId());
            while(keyClient.getPaidOperationProcessingState(opId).isProcessing()) {
                Thread.sleep(100);
            }
            ItemResult ir = client.getState(u.getId());
            System.out.println(opId + " " + ir);
            if(ir.state == ItemState.APPROVED) {
                synchronized (ts.uContractLock) {
                    ts.uContract = u;
                }
            } else {
                throw new IllegalArgumentException("wrong u");
            }


        }


        assertEquals(client.register(revision1.getPackedTransaction(),100000).state, ItemState.DECLINED);
        assertEquals(client.register(revision2.getPackedTransaction(),100000).state, ItemState.APPROVED);

        ts.shutdown();
    }

    @Test
    public void persistentVotingChangeOwner() throws Exception {

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));
        for(int i = 0; i < client.size(); i++) {
            client.getClient(i);
        }

        Contract contract = new Contract(TestKeys.privateKey(1));
        QuorumVoteRole quorumVoteRole = new QuorumVoteRole("change_owner",contract,"this.state.data.list","90%");
        contract.addRole(quorumVoteRole);

        List<KeyAddress> addresses = new ArrayList<>();
        for(int i = 0; i < 20; i++) {
            addresses.add(TestKeys.publicKey(i).getLongAddress());
        }
        contract.getStateData().put("list",addresses);

        contract.addPermission(new ChangeOwnerPermission(new RoleLink("@chown","change_owner")));
        contract.seal();

        assertEquals(client.register(contract.getPackedTransaction(),10000).state,ItemState.APPROVED);


        Contract revision1 = contract.createRevision(TestKeys.privateKey(4));
        revision1.setOwnerKey(TestKeys.publicKey(2).getLongAddress());
        revision1.seal();

        Contract revision2 = contract.createRevision(TestKeys.privateKey(4));
        revision2.setOwnerKey(TestKeys.publicKey(3).getLongAddress());
        revision2.seal();


        Map<Integer,ZonedDateTime> expires = new ConcurrentHashMap<>();


        Contract u = Parcel.createPayment(getApprovedUContract(ts), Do.listOf(ts.myKey), 1, false);

        HashId opId = client.initiateVote(u.getPackedTransaction(), contract,"change_owner",Do.listOf(revision1.getId(),revision2.getId()));
        while(client.getPaidOperationProcessingState(opId).isProcessing()) {
            Thread.sleep(100);
        }

        if(client.getState(u.getId()).state == ItemState.APPROVED) {
            synchronized (ts.uContractLock) {
                ts.uContract = u;
            }
        } else {
            throw new IllegalArgumentException("wrong u");
        }
        for(int j = 0; j < 19; j++) {
            u = Parcel.createPayment(getApprovedUContract(ts), Do.listOf(ts.myKey), 1, false);

            Client keyClient = new Client("test_node_config_v2", null, TestKeys.privateKey(j));
            opId = keyClient.voteForContract(u.getPackedTransaction(),contract.getId(),revision2.getId());
            while(keyClient.getPaidOperationProcessingState(opId).isProcessing()) {
                Thread.sleep(100);
            }

            if(keyClient.getState(u.getId()).state == ItemState.APPROVED) {
                synchronized (ts.uContractLock) {
                    ts.uContract = u;
                }
            } else {
                throw new IllegalArgumentException("wrong u");
            }
        }



        assertEquals(client.register(revision1.getPackedTransaction(),100000).state, ItemState.DECLINED);
        assertEquals(client.register(revision2.getPackedTransaction(),100000).state, ItemState.APPROVED);

        ts.shutdown();
    }


    @Test
    public void detachedContractsVoting() throws Exception {

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));

        int N = 3;
        int M = 5;

        Contract contract = new Contract(TestKeys.privateKey(1));
        QuorumVoteRole quorumVoteRole = new QuorumVoteRole("issuer",contract,"refSupplied.state.data.list",""+(N*M));
        contract.addRole(quorumVoteRole);

        SimpleRole dummy = new SimpleRole("dummy",contract);
        dummy.addRequiredReference("refSupplied", Role.RequiredMode.ALL_OF);
        contract.addRole(dummy);


        Reference refSupplied = new Reference(contract);
        refSupplied.name = "refSupplied";
        refSupplied.type = Reference.TYPE_EXISTING_DEFINITION;
        refSupplied.setConditions(Binder.of("all_of",Do.listOf("ref.issuer==this.definition.data.issuer")));
        contract.addReference(refSupplied);
        contract.getDefinition().getData().set("issuer",TestKeys.publicKey(2).getLongAddress().toString());
        contract.seal();


        Contract u = Parcel.createPayment(getApprovedUContract(ts), Do.listOf(ts.myKey), 1, false);

        HashId opId = client.initiateVote(u.getPackedTransaction(),contract,"creator",Do.listOf(contract.getId()));

        while(client.getPaidOperationProcessingState(opId).isProcessing()) {
            Thread.sleep(100);
        }

        if(client.getState(u.getId()).state == ItemState.APPROVED) {
            synchronized (ts.uContractLock) {
                ts.uContract = u;
            }
        } else {
            throw new IllegalArgumentException("wrong u");
        }



        for(int i = 0; i < M; i++) {

            Contract supplied = new Contract(TestKeys.privateKey(2));
            supplied.setIssuerKeys(TestKeys.publicKey(2).getLongAddress());
            List<KeyAddress> kas = new ArrayList<>();
            for(int j = 0; j < N;j++) {
                kas.add(TestKeys.publicKey(i*N+j).getLongAddress());
            }
            supplied.getStateData().put("list",kas);
            supplied.seal();

            for(int j = 0; j < N; j++) {
                Client c = new Client("test_node_config_v2", null, TestKeys.privateKey(i*N+j));
                u = Parcel.createPayment(getApprovedUContract(ts), Do.listOf(ts.myKey), 1, false);

                opId = c.voteForContract(u.getPackedTransaction(),contract.getId(),contract.getId(), Do.listOf(supplied.getLastSealedBinary()));
                while(c.getPaidOperationProcessingState(opId).isProcessing()) {
                    Thread.sleep(100);
                }

                if(c.getState(u.getId()).state == ItemState.APPROVED) {
                    synchronized (ts.uContractLock) {
                        ts.uContract = u;
                    }
                } else {
                    throw new IllegalArgumentException("wrong u");
                }
            }
        }


        ItemResult ir = client.register(contract.getPackedTransaction(), 100000);
        System.out.println(ir);
        assertEquals(ir.state, ItemState.APPROVED);

        ts.shutdown();

    }

    @Test
    public void detachedContractsVotingAuthorityLevels() throws Exception {

        TestSpace ts = prepareTestSpace();
        ts.nodes.forEach(n->n.config.setIsFreeRegistrationsAllowedFromYaml(true));

        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));

        int N = 3;
        int M = 3;
        int K = 3;

        Contract contract = new Contract(TestKeys.privateKey(TestKeys.binaryKeys.length-1));
        QuorumVoteRole quorumVoteRole = new QuorumVoteRole("issuer",contract,"refSupplied.state.data.list",""+(N*M*K));
        contract.addRole(quorumVoteRole);
        Reference refSupplied = new Reference(contract);
        refSupplied.name = "refSupplied";
        refSupplied.type = Reference.TYPE_EXISTING_DEFINITION;
        refSupplied.setConditions(Binder.of("all_of",Do.listOf("ref can_play ref2ndLevelAuth.state.roles.granted_auth")));
        contract.addReference(refSupplied);

        Reference ref2ndLevelAuth = new Reference(contract);
        ref2ndLevelAuth.name = "ref2ndLevelAuth";
        ref2ndLevelAuth.type = Reference.TYPE_EXISTING_DEFINITION;
        ref2ndLevelAuth.setConditions(Binder.of("all_of",Do.listOf("ref can_play refRootAuth.state.roles.granted_auth")));
        contract.addReference(ref2ndLevelAuth);

        Reference refRootAuth = new Reference(contract);
        refRootAuth.name = "refRootAuth";
        refRootAuth.type = Reference.TYPE_EXISTING_DEFINITION;
        refRootAuth.setConditions(Binder.of("all_of",Do.listOf("ref.issuer==this.definition.data.root_authority")));
        contract.addReference(refRootAuth);

        SimpleRole role = new SimpleRole("dummy",contract);
        role.addRequiredReference("refSupplied", Role.RequiredMode.ALL_OF);
        role.addRequiredReference("ref2ndLevelAuth", Role.RequiredMode.ALL_OF);
        role.addRequiredReference("refRootAuth", Role.RequiredMode.ALL_OF);

        contract.addRole(role);


        final int BASE = 0;


        contract.getDefinition().getData().put("root_authority",TestKeys.publicKey(BASE).getLongAddress().toString());

        contract.seal();

        Contract rootAuthorityContract = new Contract(TestKeys.privateKey(BASE));
        List<Contract> scndLvlAuthContracts = new ArrayList<>();
        rootAuthorityContract.setIssuerKeys(TestKeys.publicKey(BASE).getLongAddress());
        ListRole grantedAuth = new ListRole("granted_auth",rootAuthorityContract);
        grantedAuth.setMode(ListRole.Mode.ANY);
        for(int i = 0; i < K; i++) {
            SimpleRole simpleRole = new SimpleRole("@auth"+i,rootAuthorityContract,Do.listOf(TestKeys.publicKey(BASE+1+i).getLongAddress()));
            grantedAuth.addRole(simpleRole);
            Contract scndLvlAuth = new Contract(TestKeys.privateKey(BASE+1+i));
            ListRole grantedAuth2nd = new ListRole("granted_auth",scndLvlAuth);
            grantedAuth2nd.setMode(ListRole.Mode.ANY);
            scndLvlAuth.addRole(grantedAuth2nd);
            for(int j = 0; j < M; j++) {
                simpleRole = new SimpleRole("@auth"+i,scndLvlAuth,Do.listOf(TestKeys.publicKey(BASE+1+K+i*K+j).getLongAddress()));
                grantedAuth2nd.addRole(simpleRole);
            }
            scndLvlAuth.seal();
            scndLvlAuthContracts.add(scndLvlAuth);
        }
        rootAuthorityContract.addRole(grantedAuth);
        rootAuthorityContract.seal();



        ItemResult ir = client.register(rootAuthorityContract.getPackedTransaction(), 100000);
        assertEquals(ir.state, ItemState.APPROVED);

        AtomicInteger readyCounter0 = new AtomicInteger();
        AsyncEvent readyEvent0 = new AsyncEvent();

        scndLvlAuthContracts.forEach(c -> {
            Do.inParallel(() -> {
                ItemResult ir2 = null;
                try {
                    ir2 = client.register(c.getPackedTransaction(), 100000);
                } catch (ClientError clientError) {
                    clientError.printStackTrace();
                }
                assertEquals(ir2.state, ItemState.APPROVED);
                if(readyCounter0.incrementAndGet() == scndLvlAuthContracts.size()) {
                    readyEvent0.fire();
                }
            });
        });

        readyEvent0.await();



        Contract u = Parcel.createPayment(getApprovedUContract(ts), Do.listOf(ts.myKey), 1, false);

        HashId opId = client.initiateVote(u.getPackedTransaction(),contract,"creator",Do.listOf(contract.getId()));

        while(client.getPaidOperationProcessingState(opId).isProcessing()) {
            Thread.sleep(100);
        }

        if(client.getState(u.getId()).state == ItemState.APPROVED) {
            synchronized (ts.uContractLock) {
                ts.uContract = u;
            }
        } else {
            throw new IllegalArgumentException("wrong u");
        }




        for(int i = 0; i < K; i++) {
            for(int j = 0; j < M; j++) {

                Contract supplied = new Contract(TestKeys.privateKey(BASE+1+K+i*K+j));
                List<KeyAddress> kas = new ArrayList<>();
                for (int k = 0; k < N; k++) {
                    kas.add(TestKeys.publicKey((i*K + j) * M + k).getLongAddress());
                }
                supplied.getStateData().put("list", kas);
                supplied.seal();
                ir = client.register(supplied.getPackedTransaction(), 100000);
                assertEquals(ir.state, ItemState.APPROVED);


                for (int k = 0; k < N; k++) {
                    Client c = new Client("test_node_config_v2", null, TestKeys.privateKey((i*K + j) * M + k));
                    u = Parcel.createPayment(getApprovedUContract(ts), Do.listOf(ts.myKey), 1, false);
                    opId = c.voteForContract(u.getPackedTransaction(), contract.getId(),contract.getId(), Do.listOf(rootAuthorityContract.getLastSealedBinary(), scndLvlAuthContracts.get(i).getLastSealedBinary(), supplied.getLastSealedBinary()));

                    while(c.getPaidOperationProcessingState(opId).isProcessing()) {
                        Thread.sleep(100);
                    }

                    if(c.getState(u.getId()).state == ItemState.APPROVED) {
                        synchronized (ts.uContractLock) {
                            ts.uContract = u;
                        }
                    } else {
                        throw new IllegalArgumentException("wrong u");
                    }

                    System.out.println("VOTE " +((i*K + j) * M + k));
                }
            }
        }

        ir = client.register(contract.getPackedTransaction(), 100000);
        System.out.println(ir);
        assertEquals(ir.state, ItemState.APPROVED);

        ts.shutdown();

    }
}
