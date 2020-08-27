package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.db.DbPool;
import com.icodici.db.PooledDb;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.InnerContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.services.NSmartContract;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.Client;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;

import java.io.File;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

@Ignore //base class
public class BaseMainTest {
    protected static final String ROOT_PATH = "./src/test_contracts/";

    private void clearLedger(String url) throws Exception {
        Properties properties = new Properties();
        try (DbPool dbPool = new DbPool(url, properties, 64)) {
            try (PooledDb db = dbPool.db()) {
                try (PreparedStatement statement = db.statement("delete from items;")
                ) {
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = db.statement("delete from ledger;")
                ) {
                    statement.executeUpdate();
                }
            }
        }
    }

    @Before
    public void beforeMainTest() throws Exception {
        // clearLedgers
        List<String> dbUrls = new ArrayList<>();
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t1");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t2");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t3");
        dbUrls.add("jdbc:postgresql://localhost:5432/universa_node_t4");
        List<Ledger> ledgers = new ArrayList<>();
        dbUrls.stream().forEach(url -> {
            try {
                clearLedger(url);
                PostgresLedger ledger = new PostgresLedger(url);
                ledgers.add(ledger);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // add U issuer test key
        configForProvider.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
    }

    @After
    public void tearDown() throws Exception {
        LogPrinter.showDebug(false);
    }

    Main createMain(String name, boolean nolog) throws InterruptedException {
        return createMain(name, "", nolog);
    }

    Main createMain(String name, String postfix, boolean nolog) throws InterruptedException {
        String path = new File("src/test_node_config_v2" + postfix + "/" + name).getAbsolutePath();
        System.out.println(path);
        String[] args = new String[]{"--test", "--config", path, nolog ? "--nolog" : ""};

        List<Main> mm = new ArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                Main m = new Main(args);
                try {
                    m.config.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
                } catch (KeyAddress.IllegalAddressException e) {
                    e.printStackTrace();
                }

                try {
                    //m.config.getKeysWhiteList().add(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")));
                    m.config.getAddressesWhiteList().add(new KeyAddress(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")), 0, true));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //m.config.getKeysWhiteList().add(m.config.getUIssuerKey());
                m.waitReady();
                mm.add(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.setName("Node Server: " + name);
        thread.start();

        while (mm.size() == 0) {
            Thread.sleep(100);
        }
        return mm.get(0);
    }

    Main createMainFromDb(String dbUrl, boolean nolog) throws InterruptedException {
        String[] args = new String[]{"--test", "--database", dbUrl, nolog ? "--nolog" : ""};

        List<Main> mm = new ArrayList<>();

        Thread thread = new Thread(() -> {
            try {
                Main m = new Main(args);
                try {
                    m.config.addTransactionUnitsIssuerKeyData(new KeyAddress("Zau3tT8YtDkj3UDBSznrWHAjbhhU4SXsfQLWDFsv5vw24TLn6s"));
                } catch (KeyAddress.IllegalAddressException e) {
                    e.printStackTrace();
                }

                try {
                    //m.config.getKeysWhiteList().add(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")));
                    m.config.getAddressesWhiteList().add(new KeyAddress(new PublicKey(Do.read("./src/test_contracts/keys/u_key.public.unikey")), 0, true));
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //m.config.getKeysWhiteList().add(m.config.getUIssuerKey());
                m.waitReady();
                mm.add(m);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        thread.setName("Node Server: " + dbUrl);
        thread.start();

        while (mm.size() == 0) {
            Thread.sleep(100);
        }
        return mm.get(0);
    }

    TestSpace prepareTestSpace() throws Exception {
        return prepareTestSpace(TestKeys.privateKey(3));
    }

    TestSpace prepareTestSpace(PrivateKey key) throws Exception {
        TestSpace testSpace = new TestSpace();
        testSpace.nodes = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            testSpace.nodes.add(createMain("node" + (i + 1), false));
        testSpace.node = testSpace.nodes.get(0);
        assertEquals("http://localhost:8080", testSpace.node.myInfo.internalUrlString());
        assertEquals("http://localhost:8080", testSpace.node.myInfo.publicUrlString());
        testSpace.myKey = key;
        testSpace.client = new Client(testSpace.myKey, testSpace.node.myInfo, null);

        testSpace.clients = new ArrayList();
        for (int i = 0; i < 4; i++)
            testSpace.clients.add(new Client(testSpace.myKey, testSpace.nodes.get(i).myInfo, null));

        testSpace.nodes.forEach(n->n.node.getServiceContracts().values().forEach(b -> {
            try {
                Contract c = Contract.fromPackedTransaction(b);
                StateRecord record = n.node.getLedger().findOrCreate(c.getId());
                record.setState(ItemState.APPROVED);
                record.setExpiresAt(ZonedDateTime.now().plusMonths(1));
                record.save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));

        return testSpace;
    }

    class TestSpace {
        public List<Main> nodes = null;
        public Main node = null;
        PrivateKey myKey = null;
        Client client = null;
        Object uContractLock = new Object();
        Contract uContract = null;
        public ArrayList<Client> clients;

        public void shutdown() {
            nodes.forEach(n->n.shutdown());
        }

        public Set<PrivateKey> getUKeys() {
            return new HashSet<PrivateKey>(Do.listOf(myKey));
        }
    }


    protected ItemResult registerWithMinimumKeys(Contract contract, Collection<PrivateKey> signatures, TestSpace ts, int payingParcelAmount) throws Exception {
        for(PrivateKey key : signatures) {
            HashSet<PrivateKey> currentKeys = new HashSet<>(signatures);
            currentKeys.remove(key);
            NSmartContract.NodeInfoProvider nodeInfoProvider = null;
            if(contract instanceof NSmartContract) {
                nodeInfoProvider = ((NSmartContract)contract).getNodeInfoProvider();
            }
            Contract c  = Contract.fromPackedTransaction(contract.getPackedTransaction());
            if(c instanceof NSmartContract) {
                ((NSmartContract)c).setNodeInfoProvider(nodeInfoProvider);
            }

            c.getKeysToSignWith().addAll(currentKeys);
            c.seal();
            Contract u = getApprovedUContract(ts);

            Parcel parcel;
            if(payingParcelAmount > 0) {
                parcel = ContractsService.createPayingParcel(c.getTransactionPack(),u,1,payingParcelAmount,new HashSet<PrivateKey>(Do.listOf(ts.myKey)),false);
            } else {
                parcel = ContractsService.createParcel(c,u,1,new HashSet<PrivateKey>(Do.listOf(ts.myKey)));
            }
            assertEquals(ts.client.registerParcelWithState(parcel.pack(),8000).state, ItemState.DECLINED);
            synchronized (ts.uContractLock) {
                ts.uContract = parcel.getPaymentContract();
            }
            Thread.sleep(500);
        }

        HashSet<PrivateKey> currentKeys = new HashSet<>(signatures);
        contract.getKeysToSignWith().clear();
        contract.getKeysToSignWith().addAll(currentKeys);
        contract.seal();
        contract.getKeysToSignWith().clear();

        Contract u = getApprovedUContract(ts);

        Parcel parcel;
        if(payingParcelAmount > 0) {
            parcel = Parcel.of(contract,u,new HashSet<>(Do.listOf(ts.myKey)),payingParcelAmount);
        } else {
            parcel = Parcel.of(contract,u,new HashSet<>(Do.listOf(ts.myKey)));
        }
        ItemResult ir = ts.client.registerParcelWithState(parcel.pack(), 80000);
        if(ir.state != ItemState.APPROVED)
            System.out.println(ir);
        assertEquals(ir.state,ItemState.APPROVED);
        synchronized (ts.uContractLock) {
            if(payingParcelAmount > 0) {
                ts.uContract = (Contract) parcel.getPayloadContract().getNewItems().stream().filter(c -> c.isU(ts.node.config.getUIssuerKeys(), ts.node.config.getUIssuerName())).findFirst().get();
            } else {
                ts.uContract = parcel.getPaymentContract();
            }
        }
        return ir;
    }

    protected Contract getApprovedUContract(TestSpace testSpace) throws Exception {
        synchronized (testSpace.uContractLock) {
            if (testSpace.uContract == null) {

                Set<PublicKey> keys = new HashSet();
                keys.add(testSpace.myKey.getPublicKey());
                Contract stepaU = InnerContractsService.createFreshU(100000000, keys);
                stepaU.check();
                stepaU.traceErrors();
                System.out.println("register new U ");
                testSpace.node.node.registerItem(stepaU);
                testSpace.uContract = stepaU;
            }
            int needRecreateUContractNum = 0;
            for (Main m : testSpace.nodes) {
                try {
                    ItemResult itemResult = m.node.waitItem(testSpace.uContract.getId(), 15000);
                    //assertEquals(ItemState.APPROVED, itemResult.state);
                    if (itemResult.state != ItemState.APPROVED) {
                        System.out.println("U: node " + m.node + " result: " + itemResult);
                        needRecreateUContractNum++;
                    }
                } catch (TimeoutException e) {
                    System.out.println("ping ");
//                    System.out.println(n.ping());
////                    System.out.println(n.traceTasksPool());
//                    System.out.println(n.traceParcelProcessors());
//                    System.out.println(n.traceItemProcessors());
                    System.out.println("U: node " + m.node + " timeout: ");
                    needRecreateUContractNum++;
                }
            }
            int recreateBorder = testSpace.nodes.size() - testSpace.node.config.getPositiveConsensus() - 1;
            if (recreateBorder < 0)
                recreateBorder = 0;
            if (needRecreateUContractNum > recreateBorder) {
                testSpace.uContract = null;
                Thread.sleep(1000);
                return getApprovedUContract(testSpace);
            }
            return testSpace.uContract;
        }
    }

    protected Config configForProvider = new Config();


}
