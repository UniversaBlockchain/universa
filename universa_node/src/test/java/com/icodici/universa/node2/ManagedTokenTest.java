package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.HashId;
import com.icodici.universa.HashIdentifiable;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.helpers.ManagedToken;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.roles.*;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertEquals;

public class ManagedTokenTest extends BaseMainTest {

    public enum ContractEntityState {
        DRAFT,
        APPROVED,
        REVOKED
    }

    public enum TransactionEntityState {
        DRAFT,
        APPROVED,
        DECLINED
    }

    public static class ContractsStorage {
        public static class TransactionEntity {
            public HashId rootId;
            public byte[] transaction;
            public String type;
            public TransactionEntityState state;
            public Set<HashId> newItems;
            public Set<HashId> revokingItems;
        }

        public static class ContractEntity {
            public HashId id;
            public HashId parent;
            public HashId transactionRoot;
            public ContractEntityState state;
            public String type;
        }

        private Map<HashId,TransactionEntity> transactions = new HashMap<>();
        private Map<HashId,ContractEntity> contracts = new HashMap<>();

        Set<HashId> getNews(Contract contract) {
            Set<HashId> result = new HashSet<>();
            result.addAll(contract.getNewItems().stream().map(HashIdentifiable::getId).collect(Collectors.toSet()));
            for(Contract sub : contract.getNew()) {
                result.addAll(getNews(sub));
            }
            return result;
        }

        Set<HashId> getRevokes(Contract contract) {
            Set<HashId> result = new HashSet<>();
            result.addAll(contract.getRevokingItems().stream().map(HashIdentifiable::getId).collect(Collectors.toSet()));
            for(Contract sub : contract.getNew()) {
                result.addAll(getRevokes(sub));
            }
            return result;
        }

        public void storeDraft(TransactionPack transaction, String type) {

            Map<String, Contract> tags = transaction.getTags();


            TransactionEntity entity = new TransactionEntity();
            entity.rootId = transaction.getContract().getId();
            entity.transaction = transaction.pack();
            entity.state = TransactionEntityState.DRAFT;
            entity.type = type;
            entity.newItems = getNews(transaction.getContract());
            entity.revokingItems = getRevokes(transaction.getContract());
            transactions.put(entity.rootId,entity);

            for(HashId id : entity.newItems) {
                ContractEntity contractEntity = new ContractEntity();
                Contract c = transaction.findContract(x -> x.getId().equals(id));
                contractEntity.id = id;
                contractEntity.parent = c.getParent();
                contractEntity.transactionRoot = entity.rootId;
                contractEntity.state = ContractEntityState.DRAFT;
                contractEntity.type = null;
                for(String tag : tags.keySet()) {
                    if(tags.get(tag) == c) {
                        contractEntity.type = tag;
                    }
                }
                contracts.put(contractEntity.id,contractEntity);
            }
        }

        public void markDraftDeclined(HashId transactionRoot) {
            TransactionEntity entity = transactions.get(transactionRoot);
            entity.state = TransactionEntityState.DECLINED;
        }

        public void markDraftApproved(HashId transactionRoot) {
            TransactionEntity entity = transactions.get(transactionRoot);
            entity.state = TransactionEntityState.APPROVED;
            for (HashId id : entity.newItems) {
                contracts.get(id).state = ContractEntityState.APPROVED;
            }

            for (HashId id : entity.revokingItems) {
                contracts.get(id).state = ContractEntityState.REVOKED;
            }
        }
    }

    public static class MintingServer {
        public interface RootUpdateCallBack {
            byte[] getLatestRoot();
        }

        private ManagedToken.MintingProtocol protocol;
        private Client client;
        private ContractsStorage storage;
        private RootUpdateCallBack rootUpdate;

        protected MintingServer(Client client, ContractsStorage storage) {
            this.client = client;
            this.storage = storage;
        }

        public MintingServer(byte[] packed, Client client, RootUpdateCallBack rootUpdate, ContractsStorage storage) throws IOException {
            this(client,storage);
            protocol = new ManagedToken.MintingProtocol(packed);
            this.rootUpdate = rootUpdate;
        }

        public byte[] issueCoins(BigDecimal amount,KeyAddress owner, Set<PrivateKey> keys) throws IOException {
            ManagedToken token = new ManagedToken(amount,owner,protocol);
            TransactionPack transaction = token.getTransaction();
            transaction.getContract().addSignatureToSeal(keys);
            storage.storeDraft(transaction,"ISSUE_COIN_"+protocol.getCoinCode());

            ItemResult ir = client.register(transaction.pack(),8000);//15*60*1000);
            if(ir.state != ItemState.APPROVED) {
                storage.markDraftDeclined(transaction.getContract().getId());
                token = new ManagedToken(amount,owner,protocol);
                transaction = token.getTransaction();
                transaction.getContract().addSignatureToSeal(keys);
                ManagedToken.updateMintingRootReference(transaction,rootUpdate.getLatestRoot());
                storage.storeDraft(transaction,"ISSUE_COIN_"+protocol.getCoinCode());

                ir = client.register(transaction.pack(),15*60*1000);
                if(ir.state != ItemState.APPROVED) {
                    storage.markDraftDeclined(transaction.getContract().getId());
                    throw new IllegalStateException(ir.toString());
                }
            }
            storage.markDraftApproved(transaction.getContract().getId());
            protocol = new ManagedToken.MintingProtocol(transaction.pack());
            return transaction.getTags().get(ManagedToken.TP_TAG).getLastSealedBinary();
        }

        public void revokeCoins(byte[] packed, Set<PrivateKey> keys, RootUpdateCallBack callBack) throws IOException {
            ManagedToken token = new ManagedToken(packed,protocol);
            token.revoke();
            TransactionPack transaction = token.getTransaction();
            transaction.getContract().addSignatureToSeal(keys);
            storage.storeDraft(transaction,"REVOKE_COIN"+protocol.getCoinCode());
            ItemResult ir = client.register(transaction.pack(),15*60*1000);
            if(ir.state != ItemState.APPROVED) {
                storage.markDraftDeclined(transaction.getContract().getId());
                token = new ManagedToken(packed,protocol);
                token.revoke();
                transaction = token.getTransaction();
                transaction.getContract().addSignatureToSeal(keys);
                ManagedToken.updateMintingRootReference(transaction,rootUpdate.getLatestRoot());
                storage.storeDraft(transaction,"REVOKE_COIN"+protocol.getCoinCode());
                ir = client.register(transaction.pack(),15*60*1000);
                if(ir.state != ItemState.APPROVED) {
                    storage.markDraftDeclined(transaction.getContract().getId());
                    throw new IllegalStateException(ir.toString());
                }
            }
            protocol = new ManagedToken.MintingProtocol(transaction.pack());
            storage.markDraftApproved(transaction.getContract().getId());
        }
    }

    public static class RootServer {
        private ManagedToken.MintingRoot root;
        private Client client;
        private ContractsStorage storage;

        protected RootServer(ContractsStorage storage, Client client) {
            this.storage = storage;
            this.client = client;
        }

        public RootServer(Set<KeyAddress> multisigAddresses, int quorum, int strongQuorum,Client client, Set<PrivateKey> keys, ContractsStorage storage) throws ClientError {
            this(storage,client);
            root = new ManagedToken.MintingRoot(multisigAddresses,quorum,strongQuorum);
            TransactionPack transaction = root.getTransaction();
            transaction.getContract().addSignatureToSeal(keys);

            storage.storeDraft(transaction,"CREATE_ROOT");

            ItemResult ir = client.register(transaction.pack(),15*60*1000);
            if(ir.state != ItemState.APPROVED) {
                storage.markDraftDeclined(transaction.getContract().getId());
                throw new IllegalStateException(ir.toString());
            }
            storage.markDraftApproved(transaction.getContract().getId());
        }

        public RootServer(byte[] packed, Client client, ContractsStorage storage) throws IOException {
            this(storage,client);
            root = new ManagedToken.MintingRoot(packed);
        }


        public byte[] createProtocol(String coinCode, Set<PrivateKey> keys) throws IOException {
            ManagedToken.MintingProtocol mintingProtocol = new ManagedToken.MintingProtocol(coinCode,root);

            TransactionPack transaction = mintingProtocol.getTransaction();
            transaction.getContract().addSignatureToSeal(keys);

            storage.storeDraft(transaction,"CREATE_PROTOCOL_"+coinCode);
            ItemResult ir = client.register(transaction.pack(),15*60*1000);
            if(ir.state != ItemState.APPROVED) {
                throw new IllegalStateException(ir.toString());
            }
            storage.markDraftApproved(transaction.getContract().getId());
            root = new ManagedToken.MintingRoot(transaction.pack());
            return mintingProtocol.getTransaction().pack();
        }

        public byte[] getLatestTransaction() {
            return root.getLatestTransaction().pack();
        }
    }


    @Test
    public void mainFlow() throws Exception {

        TestSpace ts = prepareTestSpace();
        for(Main n : ts.nodes) {
            n.config.getAddressesWhiteList().add(TestKeys.publicKey(1).getLongAddress());
        }

        Client client = new Client("test_node_config_v2", null, TestKeys.privateKey(1));
        for(int i = 0; i < client.size();i++) {
//            client.getClient(i).setVerboseLevel(2,2,0);
        }
        ContractsStorage storage = new ContractsStorage();

        Set<PrivateKey> keys = new HashSet<>();
        keys.add(TestKeys.privateKey(0));
        keys.add(TestKeys.privateKey(1));
        keys.add(TestKeys.privateKey(2));
        keys.add(TestKeys.privateKey(3));

        {
            System.out.println("CREATE ROOT");
            RootServer rootServer = new RootServer(keys.stream().map(k -> k.getPublicKey().getLongAddress()).collect(Collectors.toSet()), 2, 3, client, keys, storage);
            System.out.println("CREATE PROTOCOL USD");
            byte[] packedProtocolUSD = rootServer.createProtocol("USD", keys);
            MintingServer mintingServerUSD = new MintingServer(packedProtocolUSD, client, () -> rootServer.getLatestTransaction(), storage);
            System.out.println("MINT USD");
            byte[] packedUSD = mintingServerUSD.issueCoins(new BigDecimal(1000), TestKeys.publicKey(5).getLongAddress(), keys);
            System.out.println("CREATE PROTOCOL EUR");
            byte[] packedProtocolEUR = rootServer.createProtocol("EUR", keys);
            MintingServer mintingServerEUR = new MintingServer(packedProtocolEUR, client, () -> rootServer.getLatestTransaction(), storage);

            System.out.println("MINT EUR");
            byte[] packedEUR = mintingServerEUR.issueCoins(new BigDecimal(1000), TestKeys.publicKey(5).getLongAddress(), keys);


            System.out.println("MINT USD2");
            byte[] packedUSD2 = mintingServerUSD.issueCoins(new BigDecimal(1000), TestKeys.publicKey(5).getLongAddress(), keys);


        }

        //RESTART SERVERS
        {
            ContractsStorage.ContractEntity latestRoot = storage.contracts.values().stream().filter(c -> c.state == ContractEntityState.APPROVED && c.type.equals(ManagedToken.MintingRoot.TP_TAG)).findFirst().get();
            RootServer rootServer = new RootServer(storage.transactions.get(latestRoot.transactionRoot).transaction,client,storage);

            ContractsStorage.ContractEntity latestProtocol = storage.contracts.values().stream().filter(c -> c.state == ContractEntityState.APPROVED && c.type.equals(ManagedToken.MintingProtocol.TP_TAG)).findFirst().get();
            MintingServer  mintingServerSOME = new MintingServer(storage.transactions.get(latestProtocol.transactionRoot).transaction,client,() -> rootServer.getLatestTransaction(),storage);

            System.out.println("MINT SOMETHING (EUR/USD)");
            byte[] packedSOME = mintingServerSOME.issueCoins(new BigDecimal(1000), TestKeys.publicKey(6).getLongAddress(), keys);

            byte[] packedProtocolRUR = rootServer.createProtocol("RUR", keys);
            MintingServer mintingServerRUR = new MintingServer(packedProtocolRUR, client, () -> rootServer.getLatestTransaction(), storage);

            System.out.println("MINT RUR");
            byte[] packedRUR = mintingServerRUR.issueCoins(new BigDecimal(1000), TestKeys.publicKey(5).getLongAddress(), keys);

            System.out.println("MINT SOMETHING2 (EUR/USD)");
            byte[] packedSOME2 = mintingServerSOME.issueCoins(new BigDecimal(1000), TestKeys.publicKey(5).getLongAddress(), keys);

            System.out.println("REVOKE SOMETHING (EUR/USD)");
            Set<PrivateKey> owner = new HashSet<>();
            owner.add(TestKeys.privateKey(0));
            owner.add(TestKeys.privateKey(1));
            owner.add(TestKeys.privateKey(2));
            owner.add(TestKeys.privateKey(3));

            owner.add(TestKeys.privateKey(6));
            mintingServerSOME.revokeCoins(packedSOME,owner,() -> rootServer.getLatestTransaction());
        }

        System.out.println("========= TRANSACTIONS ==========");
        for (ContractsStorage.TransactionEntity x : storage.transactions.values()) {
            System.out.println(x.rootId + " " + x.state + " " + x.type);
        }
        System.out.println("========= CONTRACTS ==========");
        for (ContractsStorage.ContractEntity x : storage.contracts.values()) {
            System.out.println(x.id + "\t" + x.transactionRoot + "\t" + x.state + "\t" + x.type);
        }


        ts.shutdown();
    }

}
