package com.icodici.universa.node2;



import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.*;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.DatagramAdapter;
import com.icodici.universa.node2.network.Network;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Bytes;
import org.junit.*;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


public class ResearchTest extends BaseNetworkTest {

    private static TestLocalNetwork network_s = null;
    private static List<TestLocalNetwork> networks = new ArrayList<>();
    private static Node node_s = null;
    private static List<Node> nodes_s = null;
    private static Map<NodeInfo,Node> nodesMap = new HashMap<>();
    private static Ledger ledger_s = null;
    private static NetConfig nc_s = null;
    private static Config config_s = null;


    private static final int NODES = 10;


    @BeforeClass
    public static void beforeClass() throws Exception {
        initTestSet();
    }

    private static void initTestSet() throws Exception {
        initTestSet(1, 1);
    }

    private static void initTestSet(int posCons, int negCons) throws Exception {
        nodesMap = new HashMap<>();
        networks = new ArrayList<>();

        config_s = new Config();
        config_s.setPositiveConsensus(7);
        config_s.setNegativeConsensus(4);
        config_s.setResyncBreakConsensus(2);

        Properties properties = new Properties();
        File file = new File(CONFIG_2_PATH + "config/config.yaml");

        Yaml yaml = new Yaml();
        Binder settings = new Binder();
        if (file.exists())
            settings = Binder.from(yaml.load(new FileReader(file)));

//        properties.setProperty("database", settings.getStringOrThrow("database"));

        /* test loading onfig should be in other place
        NetConfig ncNet = new NetConfig(CONFIG_2_PATH+"config/nodes");
        List<NodeConsumer> netNodes = ncNet.toList();
        */

        nc_s = new NetConfig();

        for (int i = 0; i < NODES; i++) {
            int offset = 7100 + 10 * i;
            NodeInfo info =
                    new NodeInfo(
                            getNodeKey(i).getPublicKey(),
                            i,
                            "testnode_" + i,
                            "localhost",
                            offset + 3,
                            offset,
                            offset + 2
                    );
            nc_s.addNode(info);
        }

        for (int i = 0; i < NODES; i++) {

            NodeInfo info = nc_s.getInfo(i);

            TestLocalNetwork ln = new TestLocalNetwork(nc_s, info, getNodeKey(i));
            ln.setNodes(nodesMap);
//            ledger = new SqliteLedger("jdbc:sqlite:testledger" + "_t" + i);
            Ledger ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING + "_t" + i, properties);
            Node n = new Node(config_s, info, ledger, ln);
            nodesMap.put(info, n);
            networks.add(ln);

            if (i == 0) {
                ledger_s = ledger;
                network_s = ln;
            }
        }
        node_s = nodesMap.values().iterator().next();
    }



    @AfterClass
    public static void afterClass() throws Exception {
        networks.forEach(n->n.shutDown());
        nodesMap.forEach((i,n)->n.getLedger().close());
    }



    @Before
    public void setUp() throws Exception {
        System.out.println("setup test");
        System.out.println("Switch on UDP network full mode");
        for (int i = 0; i < NODES; i++) {
            networks.get(i).setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            networks.get(i).setUDPAdapterLostPacketsPercentInTestMode(0);
        }
        for (TestLocalNetwork ln : networks) {
            ln.setUDPAdapterTestMode(DatagramAdapter.TestModes.NONE);
            ln.setUDPAdapterLostPacketsPercentInTestMode(0);
//            ln.setUDPAdapterVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
        }
        init(node_s, nodes_s, network_s, ledger_s, config_s);
    }









/////////////////////////////////////////////

    @Test
    public void test1() throws Exception {
        Config config = new Config();

        config.setPositiveConsensus(1);
        config.setNegativeConsensus(1);

        NodeInfo nodeInfo = new NodeInfo(getNodePublicKey(0),1,"node1","localhost",7101,7102,7104);
        NetConfig netConfig = new NetConfig(asList(nodeInfo));
        Network network = new TestSingleNetwork(netConfig);

        Properties properties = new Properties();

        File file = new File(CONFIG_2_PATH + "config/config.yaml");
        if (file.exists())
            properties.load(new FileReader(file));

        Ledger ledger = new PostgresLedger(PostgresLedgerTest.CONNECTION_STRING, properties);
        Node node = new Node(config, nodeInfo, ledger, network);
        System.out.println(node.toString());
    }



    @Test
    public void quantiserTest() throws Exception {
        Quantiser quantiser = new Quantiser();
        quantiser.reset(10);
        quantiser.addWorkCost(Quantiser.QuantiserProcesses.PRICE_APPLICABLE_PERM);
        quantiser.addWorkCost(Quantiser.QuantiserProcesses.PRICE_CHECK_4096_SIG);
        try {
            quantiser.addWorkCost(Quantiser.QuantiserProcesses.PRICE_REGISTER_VERSION);
            assertFalse(true); // must throw QuantiserException
        } catch (Quantiser.QuantiserException e) {
            return;
        }
    }



    @Test
    public void quantiserInContract() throws Exception {
        Contract c = Contract.fromDslFile(ROOT_PATH + "simple_root_contract.yml");
        c.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        c.check();
        c.traceErrors();
        assertTrue(c.check());
        c.seal();
        System.out.println(c.getProcessedCost());
    }



    private static Map<String, PrivateKey> keyFiles;
    private static List<String> keyFileNames = new ArrayList<>();


    private static void report(String message) {
        System.out.println(message);
    }


    private static void addError(String code, String object, String message) {
        System.out.println(code + " - " + object + " - " + message);
    }


    public static synchronized Map<String, PrivateKey> keysMap() throws IOException {
        if (keyFiles == null) {
            keyFiles = new HashMap<>();
            for (String fileName : keyFileNames) {
//                PrivateKey pk = new PrivateKey(Do.read(fileName));
//                keyFiles.put(fileName, pk);
                try {
                    PrivateKey pk = PrivateKey.fromPath(Paths.get(fileName));
                    keyFiles.put(fileName, pk);
                } catch (IOException e) {
                    addError(Errors.NOT_FOUND.name(), fileName.toString(), "failed to load key file: " + e.getMessage());
                }
            }
        }
        return keyFiles;
    }


    private static void addErrors(List<ErrorRecord> errors) {
        errors.forEach(e -> addError(e.getError().name(), e.getObjectName(), e.getMessage()));
    }


    public static void saveContract(Contract contract, String fileName, Boolean fromPackedTransaction) throws IOException {
        if (fileName == null) {
            fileName = "Universa_" + DateTimeFormatter.ofPattern("yyyy-MM-ddTHH:mm:ss").format(contract.getCreatedAt()) + ".unicon";
        }

        keysMap().values().forEach(k -> contract.addSignerKey(k));
        if (keysMap().values().size() > 0) {
            contract.seal();
        }

        byte[] data;
        if (fromPackedTransaction) {
//            contract.seal();
            data = contract.getPackedTransaction();
        } else {
            data = contract.getLastSealedBinary();
        }
        int count = contract.getKeysToSignWith().size();
        if (count > 0)
            report("Contract is sealed with " + count + " key(s)");
        report("Contract is saved to: " + fileName);
        report("Sealed contract size: " + data.length);
        try (FileOutputStream fs = new FileOutputStream(fileName)) {
            fs.write(data);
            fs.close();
        }
        try {
            if (contract.check()) {
                report("Sealed contract has no errors");
            } else
                addErrors(contract.getErrors());
        } catch (Quantiser.QuantiserException e) {
            addError("QUANTIZER_COST_LIMIT", contract.toString(), e.getMessage());
        }
    }


    public static Contract loadContract(String fileName, Boolean fromPackedTransaction) throws IOException {
        Contract contract = null;

        File pathFile = new File(fileName);
        if (pathFile.exists()) {
//            reporter.verbose("Loading contract from: " + fileName);
            Path path = Paths.get(fileName);
            byte[] data = Files.readAllBytes(path);

            try {
                if (fromPackedTransaction) {
                    contract = Contract.fromPackedTransaction(data);
                } else {
                    contract = new Contract(data);
                }
            } catch (Quantiser.QuantiserException e) {
                addError("QUANTIZER_COST_LIMIT", fileName, e.toString());
            }
        } else {
            addError(Errors.NOT_FOUND.name(), fileName, "Path " + fileName + " does not exist");
//            usage("Path " + fileName + " does not exist");
        }

        return contract;
    }


    private void registerAndCheckApproved(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }

    private void registerAndCheckDeclined(Contract c) throws TimeoutException, InterruptedException {
        node.registerItem(c);
        ItemResult itemResult = node.waitItem(c.getId(), 5000);
        assertEquals(ItemState.DECLINED, itemResult.state);
    }


    @Test
    public void contractSerializeTest() throws Exception {
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        lamborghini.addSignerKey(manufacturePrivateKey);
        lamborghini.seal();
        System.out.println("Lamborghini ownership contract is valid: " + lamborghini.check());
        lamborghini.traceErrors();
        Role stepanMamontovRole = lamborghini.getOwner();

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        delorean.addNewItems(lamborghini);
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        delorean.traceErrors();
        registerAndCheckApproved(delorean);
        Role martyMcflyRole = delorean.getOwner();
        System.out.println("Lamborghini ownership is belongs to Stepa: " + delorean.getNew().iterator().next().getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));

        final String FILENAME = "/tmp/delorean.contract";

        System.out.println("serialize...");
        saveContract(delorean, FILENAME, true);
        System.out.println("deserialize...");
        Contract loadedContract = loadContract(FILENAME, true);
        System.out.println("verify loadedContract...");
        System.out.println("Lamborghini ownership contract is valid: " + delorean.getNew().iterator().next().check());
        System.out.println("DeLorean ownership contract is valid: " + delorean.check());
        System.out.println("Lamborghini ownership is belongs to Stepa: " + delorean.getNew().iterator().next().getOwner().getKeys().containsAll(stepanMamontovRole.getKeys()));
        System.out.println("DeLorean ownership is belongs to Marty: " + delorean.getOwner().getKeys().containsAll(martyMcflyRole.getKeys()));
        registerAndCheckApproved(loadedContract);
    }


    @Test
    public void contractSwapTest() throws Exception {
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey alicePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "/keys/marty_mcfly.private.unikey"));
        PrivateKey bobPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "/keys/stepan_mamontov.private.unikey"));
        System.out.println("manufacture fingerprint(): " + Bytes.toHex(manufacturePrivateKey.getPublicKey().fingerprint()));
        System.out.println("alice fingerprint(): " + Bytes.toHex(alicePrivateKey.getPublicKey().fingerprint()));
        System.out.println("bob fingerprint(): " + Bytes.toHex(bobPrivateKey.getPublicKey().fingerprint()));

        Function<PublicKey, String> finger2name = pub -> {
            if (pub.equals(alicePrivateKey.getPublicKey()))
                return "Alice";
            if (pub.equals(bobPrivateKey.getPublicKey()))
                return "Bob";
            if (pub.equals(manufacturePrivateKey.getPublicKey()))
                return "manufacture";
            return "unknown";
        };

        Contract k0 = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");
        k0.addSignerKey(manufacturePrivateKey);
        k0.seal();
        System.out.println("k0.check(): " + k0.check());
        k0.traceErrors();
        System.out.println("k0 seal fingerprint(): " + finger2name.apply(k0.getSealedByKeys().iterator().next()));
        System.out.println("k0 owner fingerprint(): " + finger2name.apply(k0.getOwner().getKeys().iterator().next()));

        Contract l0 = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        l0.addSignerKey(manufacturePrivateKey);
        l0.seal();
        System.out.println("l0.check(): " + l0.check());
        l0.traceErrors();
        System.out.println("l0 seal fingerprint(): " + finger2name.apply(l0.getSealedByKeys().iterator().next()));
        System.out.println("l0 owner fingerprint(): " + finger2name.apply(l0.getOwner().getKeys().iterator().next()));

        Reference k1transactionRef = new Reference();
        k1transactionRef.type = Reference.TYPE_TRANSACTIONAL;
        k1transactionRef.transactional_id = HashId.createRandom().toBase64String();
        k1transactionRef.origin = l0.getOrigin();
        k1transactionRef.signed_by.add(new SimpleRole("owner", new KeyRecord(alicePrivateKey.getPublicKey())));
        k1transactionRef.signed_by.add(new SimpleRole("crator", new KeyRecord(bobPrivateKey.getPublicKey())));

        Reference l1transactionRef = new Reference();
        l1transactionRef.type = Reference.TYPE_TRANSACTIONAL;
        l1transactionRef.transactional_id = HashId.createRandom().toBase64String();
        l1transactionRef.origin = k0.getOrigin();
        l1transactionRef.signed_by.add(new SimpleRole("owner", new KeyRecord(bobPrivateKey.getPublicKey())));
        l1transactionRef.signed_by.add(new SimpleRole("crator", new KeyRecord(alicePrivateKey.getPublicKey())));

        Contract.Transactional tr_k = k0.createTransactionalSection();
        tr_k.setId(l1transactionRef.transactional_id);

        Contract k1 = k0.createRevision(tr_k);
        k1.setOwnerKey(alicePrivateKey.getPublicKey());
        k1.getReferencedItems().add(k1transactionRef);
        System.out.println("k1 owner fingerprint(): " + finger2name.apply(k1.getOwner().getKeys().iterator().next()));
        System.out.println("k1 reference: " + k1.getReferencedItems().iterator().next());

        Contract.Transactional tr_l = l0.createTransactionalSection();
        tr_l.setId(k1transactionRef.transactional_id);

        Contract l1 = l0.createRevision(tr_l);
        l1.setOwnerKey(bobPrivateKey.getPublicKey());
        l1.getReferencedItems().add(l1transactionRef);
        System.out.println("l1 owner fingerprint(): " + finger2name.apply(l1.getOwner().getKeys().iterator().next()));
        System.out.println("l1 reference: " + l1.getReferencedItems().iterator().next());

        k1.addSignerKey(alicePrivateKey);
        k1.seal();
        k1.addSignerKey(bobPrivateKey);
        k1.seal();
        l1.getReferencedItems().iterator().next().contract_id = k1.getId();
        l1.addSignerKey(alicePrivateKey);
        l1.seal();
        l1.addSignerKey(bobPrivateKey);
        l1.seal();

//        k1.seal();
//        l1.seal();

        Contract transaction = new Contract();
        transaction.addNewItems(k1, l1);
        transaction.addRevokingItems(k0, l0);
        System.out.println("checkTransaction: " + transaction.check());
        transaction.traceErrors();





        Binder bb = l1.serialize(new BiSerializer());
        Contract l1d = new Contract();
        l1d.deserialize(bb, new BiDeserializer());
        System.out.println("l1d owner fingerprint(): " + finger2name.apply(l1d.getOwner().getKeys().iterator().next()));
        System.out.println("l1 reference count: " + l1.getReferencedItems().size());
        System.out.println("l1d reference count: " + l1d.getReferencedItems().size());
    }


    @Test
    public void swapContractsViaTransactionAllGood() throws Exception {
        super.swapContractsViaTransactionAllGood();
    }



    public static String md5Custom(String st) {
        MessageDigest messageDigest = null;
        byte[] digest = new byte[0];

        try {
            messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.reset();
            messageDigest.update(st.getBytes());
            digest = messageDigest.digest();
        } catch (NoSuchAlgorithmException e) {
            // тут можно обработать ошибку
            // возникает она если в передаваемый алгоритм в getInstance(,,,) не существует
            e.printStackTrace();
        }

        BigInteger bigInt = new BigInteger(1, digest);
        String md5Hex = bigInt.toString(16);

        while( md5Hex.length() < 32 ){
            md5Hex = "0" + md5Hex;
        }

        return md5Hex;
    }

    @Test
    public void contractSerializeTest2() throws Exception {
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();
        Binder data1 = delorean.serialize(new BiSerializer());
        byte[] byte1 = Boss.pack(data1);
        System.out.println("data1: " + md5Custom(Bytes.toHex(byte1)));
        delorean = new Contract();
        delorean.deserialize(data1, new BiDeserializer());
        //delorean.seal();
        Binder data2 = delorean.serialize(new BiSerializer());
        byte[] byte2 = Boss.pack(data2);
        System.out.println("data2: " + md5Custom(Bytes.toHex(byte2)));
        delorean = new Contract();
        delorean.deserialize(data2, new BiDeserializer());
        //delorean.seal();
        Binder data3 = delorean.serialize(new BiSerializer());
        byte[] byte3 = Boss.pack(data3);
        System.out.println("data3: " + md5Custom(Bytes.toHex(byte3)));
    }


    @Test
    public void contractMultiSign() throws Exception {
        PrivateKey manufacturePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        PrivateKey martyPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "/keys/marty_mcfly.private.unikey"));
        PrivateKey stepaPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "/keys/stepan_mamontov.private.unikey"));

        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        //delorean.addSignerKey(manufacturePrivateKey);
        delorean.seal();

        System.out.println(delorean.findSignatureInSeal(manufacturePrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(martyPrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(stepaPrivateKey.getPublicKey()));
        System.out.println("sealedBinary: " + md5Custom(Bytes.toHex(delorean.getLastSealedBinary())));
        System.out.println("theContract: " + md5Custom(Bytes.toHex(delorean.extractTheContract())));
        System.out.println("hashId: " + delorean.getId().toBase64String());
        System.out.println("addSignatureToSeal...");
        delorean.addSignatureToSeal(manufacturePrivateKey);
        System.out.println(delorean.findSignatureInSeal(manufacturePrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(martyPrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(stepaPrivateKey.getPublicKey()));
        System.out.println("sealedBinary: " + md5Custom(Bytes.toHex(delorean.getLastSealedBinary())));
        System.out.println("theContract: " + md5Custom(Bytes.toHex(delorean.extractTheContract())));
        System.out.println("hashId: " + delorean.getId().toBase64String());
        System.out.println("addSignatureToSeal...");
        delorean.addSignatureToSeal(stepaPrivateKey);
        System.out.println(delorean.findSignatureInSeal(manufacturePrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(martyPrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(stepaPrivateKey.getPublicKey()));
        System.out.println("sealedBinary: " + md5Custom(Bytes.toHex(delorean.getLastSealedBinary())));
        System.out.println("theContract: " + md5Custom(Bytes.toHex(delorean.extractTheContract())));
        System.out.println("hashId: " + delorean.getId().toBase64String());
        System.out.println("addSignatureToSeal...");
        delorean.addSignatureToSeal(martyPrivateKey);
        System.out.println(delorean.findSignatureInSeal(manufacturePrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(martyPrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(stepaPrivateKey.getPublicKey()));
        System.out.println("sealedBinary: " + md5Custom(Bytes.toHex(delorean.getLastSealedBinary())));
        System.out.println("theContract: " + md5Custom(Bytes.toHex(delorean.extractTheContract())));
        System.out.println("hashId: " + delorean.getId().toBase64String());
        System.out.println("recreate contract...");
        delorean = new Contract(delorean.getLastSealedBinary());
        System.out.println(delorean.findSignatureInSeal(manufacturePrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(martyPrivateKey.getPublicKey()));
        System.out.println(delorean.findSignatureInSeal(stepaPrivateKey.getPublicKey()));
        System.out.println("sealedBinary: " + md5Custom(Bytes.toHex(delorean.getLastSealedBinary())));
        System.out.println("theContract: " + md5Custom(Bytes.toHex(delorean.extractTheContract())));
        System.out.println("hashId: " + delorean.getId().toBase64String());
    }



    @Test
    public void referenceModelTest() throws Exception {
        PrivateKey alicePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "/keys/marty_mcfly.private.unikey"));
        PrivateKey bobPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "/keys/stepan_mamontov.private.unikey"));
        Reference rm = new Reference();
        rm.name = "name123";
        rm.type = Reference.TYPE_TRANSACTIONAL;
        rm.transactional_id = HashId.createRandom().toBase64String();
        rm.contract_id = HashId.createRandom();
        rm.required = false;
        rm.origin = HashId.createRandom();
        rm.signed_by.add(new SimpleRole("owner", new KeyRecord(alicePrivateKey.getPublicKey())));
        rm.signed_by.add(new SimpleRole("crator", new KeyRecord(bobPrivateKey.getPublicKey())));
        System.out.println("before serialize: " + rm);
        Binder serializedData = rm.serialize(new BiSerializer());
        Reference rm2 = new Reference();
        rm2.deserialize(serializedData, new BiDeserializer());
        System.out.println("after deserialize: " + rm2);
        assertTrue(rm.equals(rm2));
    }


    @Test
    public void referenceModelTest2() throws Exception {
        PrivateKey alicePrivateKey = new PrivateKey(Do.read(ROOT_PATH + "/keys/marty_mcfly.private.unikey"));
        PrivateKey bobPrivateKey = new PrivateKey(Do.read(ROOT_PATH + "/keys/stepan_mamontov.private.unikey"));
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Reference rm = new Reference();
        rm.name = "name123";
        rm.type = Reference.TYPE_TRANSACTIONAL;
        rm.transactional_id = HashId.createRandom().toBase64String();
        rm.contract_id = HashId.createRandom();
        rm.required = false;
        rm.origin = HashId.createRandom();
        rm.signed_by.add(new SimpleRole("owner", new KeyRecord(alicePrivateKey.getPublicKey())));
        rm.signed_by.add(new SimpleRole("crator", new KeyRecord(bobPrivateKey.getPublicKey())));
        delorean.getDefinition().getReferences().add(rm);
        delorean.seal();
        System.out.println("before serialize: " + delorean.getDefinition().getReferences().iterator().next());
        byte[] serializedData = delorean.getLastSealedBinary();
        delorean = new Contract(serializedData);
        System.out.println("after deserialize: " + delorean.getDefinition().getReferences().iterator().next());
        Reference rm2 = delorean.getDefinition().getReferences().iterator().next();
        assertTrue(rm.equals(rm2));
    }



    @Test
    public void referenceModelTest_nulls() throws Exception {
        Reference rm = new Reference();
        rm.name = "name123";
        rm.type = Reference.TYPE_TRANSACTIONAL;
        rm.transactional_id = "";
        rm.contract_id = null;
        rm.required = false;
        rm.origin = null;
        System.out.println("before serialize: " + rm);
        Binder serializedData = rm.serialize(new BiSerializer());
        Reference rm2 = new Reference();
        rm2.deserialize(serializedData, new BiDeserializer());
        System.out.println("after deserialize: " + rm2);
        assertTrue(rm.equals(rm2));
    }



    @Test
    public void looseTransactionalOnTransfer() throws Exception {
        Set<PrivateKey> martyPrivateKeys = new HashSet<>();
        Set<PublicKey> martyPublicKeys = new HashSet<>();
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        Set<PublicKey> stepaPublicKeys = new HashSet<>();
        Contract delorean = Contract.fromDslFile(ROOT_PATH + "DeLoreanOwnership.yml");
        Contract lamborghini = Contract.fromDslFile(ROOT_PATH + "LamborghiniOwnership.yml");

        prepareContractsForSwap(martyPrivateKeys, martyPublicKeys, stepaPrivateKeys, stepaPublicKeys, delorean, lamborghini);

        // register swapped contracts using ContractsService
        System.out.println("--- register swapped contracts using ContractsService ---");

        Contract swapContract;

        // first Marty create transaction, add both contracts and swap owners, sign own new contract
        swapContract = ContractsService.startSwap(delorean, lamborghini, martyPrivateKeys, stepaPublicKeys);
        swapContract.getNew().get(0).getDefinition().getReferences().add(new Reference());
        swapContract.getNew().get(1).getDefinition().getReferences().add(new Reference());
        swapContract.getNew().get(0).getTransactional().getReferences().get(0).contract_id = HashId.createRandom();
        System.out.println("before imitateSending:");
        System.out.println("  swapContract.getNew().size(): " + swapContract.getNew().size());
//        System.out.println(swapContract.getNew().get(0).getDefinition().getReferences().get(0).contract_id);
//        System.out.println(swapContract.getNew().get(1).getDefinition().getReferences().get(0).contract_id);
        System.out.println("  contract0, definition references count: " + swapContract.getNew().get(0).getDefinition().getReferences().size());
        System.out.println("  contract1, definition references count: " + swapContract.getNew().get(1).getDefinition().getReferences().size());
        System.out.println("  contract0, transactional references count: " + swapContract.getNew().get(0).getTransactional().getReferences().size());
        System.out.println("  contract1, transactional references count: " + swapContract.getNew().get(1).getTransactional().getReferences().size());
        System.out.println("  contract0, transactional.reference.contract_id: " + swapContract.getNew().get(0).getTransactional().getReferences().get(0).contract_id);
        System.out.println("  contract1, transactional.reference.contract_id: " + swapContract.getNew().get(1).getTransactional().getReferences().get(0).contract_id);

        // then Marty send new revisions to Stepa
        // and Stepa sign own new contract, Marty's new contract
        swapContract = imitateSendingTransactionToPartner(swapContract);

        System.out.println("after imitateSending:");
        System.out.println("  swapContract.getNew().size(): " + swapContract.getNew().size());
//        System.out.println(swapContract.getNew().get(0).getDefinition().getReferences().get(0).contract_id);
//        System.out.println(swapContract.getNew().get(1).getDefinition().getReferences().get(0).contract_id);
        System.out.println("  contract0, definition references count: " + swapContract.getNew().get(0).getDefinition().getReferences().size());
        System.out.println("  contract1, definition references count: " + swapContract.getNew().get(1).getDefinition().getReferences().size());
        System.out.println("  contract0, transactional references count: " + swapContract.getNew().get(0).getTransactional().getReferences().size());
        System.out.println("  contract1, transactional references count: " + swapContract.getNew().get(1).getTransactional().getReferences().size());
        System.out.println("  contract0, transactional.reference.contract_id: " + swapContract.getNew().get(0).getTransactional().getReferences().get(0).contract_id);
        System.out.println("  contract1, transactional.reference.contract_id: " + swapContract.getNew().get(1).getTransactional().getReferences().get(0).contract_id);
    }



//    protected static final String ROOT_PATH = "./src/test_contracts/";
//    protected static final String CONFIG_2_PATH = "./src/test_config_2/";
/////////////////////////////////////////////////
}
