package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.*;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.roles.SimpleRole;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.*;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.Client;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JarNetworkTest extends TestCase {

    private static final int NODES_COUNT = 8;
    private static Client whiteClient = null;
    private static Client normalClient = null;
    private static ArrayList<Client> normalClients = new ArrayList<>();
    private static Contract paymentContract = null;
    private static PrivateKey paymentContractPrivKey = null;
    private static Config config;
    private static Process tunnelProcess;
    private static PostgresLedger ledger;

    @BeforeClass
    public static void beforeClass() throws Exception {
        String nodeUrl = "http://node-1-pro.universa.io:8080";
        String dbUrl = "jdbc:postgresql://localhost:15432/universa_node?user=universa&password=fuSleaphs8";
        tunnelProcess = Runtime.getRuntime().exec("ssh -N -L 15432:127.0.0.1:5432 deploy@dd.node-1-pro.universa.io -p 54324");
        int attempts = 10;
        while(true) {
            Thread.sleep(500);
            try {
                ledger = new PostgresLedger(dbUrl);
                break;
            } catch (Exception e) {
                if(attempts-- <= 0) {
                    throw e;
                }
            }
        }

        PrivateKey clientKey = TestKeys.privateKey(0);
        whiteClient = new Client(nodeUrl, clientKey, null, false);
        normalClient = new Client(nodeUrl, new PrivateKey(2048), null, false);
        paymentContract = Contract.fromPackedTransaction(Base64.decodeLines(uno_flint004_rev1_bin_b64));
        paymentContractPrivKey = new PrivateKey(Base64.decodeLines(uno_flint004_privKey_b64));

        for(int i = 0; i < NODES_COUNT;i++) {
            normalClients.add(new Client("http://node-"+(i+1)+"-pro.universa.io:8080",new PrivateKey(2048),null));
        }
        config = new Config();
        config.setConsensusConfigUpdater((config, n) -> {
            // Until we fix the announcer
            int negative = (int) Math.ceil(n * 0.11);
            if (negative < 1)
                negative = 1;
            int positive = (int) Math.floor(n * 0.90);
            if( negative+positive == n)
                negative += 1;
            int resyncBreak = (int) Math.ceil(n * 0.2);
            if (resyncBreak < 1)
                resyncBreak = 1;
            if( resyncBreak+positive == n)
                resyncBreak += 1;

            config.setPositiveConsensus(positive);
            config.setNegativeConsensus(negative);
            config.setResyncBreakConsensus(resyncBreak);
        });
        config.updateConsensusConfig(NODES_COUNT);
    }

    @AfterClass
    public static void afterClass()  throws Exception {
        if(tunnelProcess != null)
            tunnelProcess.destroy();
    }


    @Test
    public void checkLedger() throws Exception {
        Map<ItemState, Integer> size = ledger.getLedgerSize(null);
        for (ItemState state : size.keySet()) {
            System.out.println(state + " : " + size.get(state));
        }
    }



    @Test
    public void registerSimpleContractWhite() throws Exception {
        Contract whiteContract = new Contract(TestKeys.privateKey(0));
        whiteContract.seal();

        System.out.println("whiteClient.register(whiteContract)...");
        ItemResult itemResult = whiteClient.register(whiteContract.getPackedTransaction(), 5000);
        System.out.println("whiteClient.register(whiteContract)... done! itemResult: " + itemResult.state);

        itemResult = whiteClient.getState(whiteContract.getId());
        System.out.println("whiteClient.getState(whiteContract): " + itemResult.state);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }

    @Test
    public void registerSimpleContractNormal() throws Exception {
        Contract simpleContract = new Contract(TestKeys.privateKey(0));
        simpleContract.seal();

        registerAndCheckApproved(simpleContract);
    }



    @Test
    public void registerManySimpleContractsWhite() throws Exception {
        int CONTRACTS_PER_THREAD = 20;
        int THREADS_COUNT = 4;
        AtomicLong totalCounter = new AtomicLong(0);
        Runnable r = () -> {
            try {
                Client cln = createWhiteClient();
                int nodeNumber = cln.getNodeNumber();
                System.out.println("nodeNumber: " + nodeNumber);
                for (int i = 0; i < CONTRACTS_PER_THREAD; ++i) {
                    Contract whiteContract = new Contract(TestKeys.privateKey(nodeNumber-1));
                    whiteContract.seal();
                    ItemResult itemResult = cln.register(whiteContract.getPackedTransaction(), 15000);
                    assertEquals(ItemState.APPROVED, itemResult.state);
                    totalCounter.incrementAndGet();
                }
            } catch (Exception e) {
                System.out.println("error: " + e.toString());
            }
        };
        List<Thread> threadList = new ArrayList<>();
        for (int i = 0; i < THREADS_COUNT; ++i) {
            Thread t = new Thread(r);
            t.start();
            threadList.add(t);
        }
        Thread heartbeat = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(1000);
                    System.out.println("totalCounter: " + totalCounter.get());
                }
            } catch (Exception e) {
                System.out.println("totalCounter: " + totalCounter.get());
            }
        });
        heartbeat.start();
        for (Thread t : threadList)
            t.join();
        heartbeat.interrupt();
        heartbeat.join();
    }



    @Test
    public void checkPaymentContract() throws Exception {
        // to register manually, execute from deploy project:
        // bin/sql_all pro "insert into ledger(hash,state,created_at, expires_at, locked_by_id) values(decode('9186C0A9E9471E4559E74B5DAC3DBBB8445807DF80CAE4CE06FDB6588FAEBA1CE004AD378BEF3C445DECF3375E3CA5FD16227DBE5831A21207BB1BD21C85F30D0CED014E152F77E62082E0442FBD9FD2458C20778F7501B5D425AF9984062E54','hex'),'4','1520977039','1552513039','0');"
        // to erase all ledgers, execute:
        // bin/sql_all pro "truncate table ledger"
        // (after erasing ledgers, items still stay in cache -> need to restart (or redeploy) nodes)

        Contract contract = paymentContract;
        contract.check();
        System.out.println("uno bin: " + Base64.encodeString(contract.getPackedTransaction()));
        System.out.println("uno hashId: " + Bytes.toHex(contract.getId().getDigest()).replace(" ", ""));
        System.out.println("approved ord: " + ItemState.APPROVED.ordinal());
        System.out.println("getCreatedAt: " + StateRecord.unixTime(contract.getCreatedAt()));
        System.out.println("getExpiresAt: " + StateRecord.unixTime(contract.getExpiresAt()));

        ItemResult itemResult = normalClient.getState(contract.getId());
        System.out.println("getState... done! itemResult: " + itemResult.state);
    }


    @Ignore
    @Test
    public void registerSimpleContractWithPayment() throws Exception {
        Contract contractToRegister = new Contract(TestKeys.privateKey(10));
        contractToRegister.seal();
        ItemResult itemResult = normalClient.register(contractToRegister.getPackedTransaction(), 5000);
        System.out.println("register... done! itemResult: " + itemResult.state);
        assertEquals(ItemState.UNDEFINED, itemResult.state);

        Parcel parcel = ContractsService.createParcel(contractToRegister, paymentContract, 1, Stream.of(paymentContractPrivKey).collect(Collectors.toSet()), true);
        normalClient.registerParcel(parcel.pack(), 5000);
        itemResult = normalClient.getState(parcel.getPaymentContract().getId());
        if (itemResult.state == ItemState.APPROVED)
            paymentContract = parcel.getPaymentContract();
        System.out.println("registerParcel... done!");
        System.out.println("parcel.paymentContract.itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
        itemResult = normalClient.getState(contractToRegister.getId());
        System.out.println("contractToRegister.itemResult: " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);
    }


    @Ignore
    @Test
    public void registerSeveralSimpleContractWithPayment() throws Exception {
        for (int i = 0; i < 20; ++i) {
            System.out.println("\ni = " + i);
            Contract contractToRegister = new Contract(TestKeys.privateKey(10));
            contractToRegister.seal();
            ItemResult itemResult = normalClient.register(contractToRegister.getPackedTransaction(), 5000);
            System.out.println("register... done! itemResult: " + itemResult.state);
            assertEquals(ItemState.UNDEFINED, itemResult.state);

            Parcel parcel = ContractsService.createParcel(contractToRegister, paymentContract, 1, Stream.of(paymentContractPrivKey).collect(Collectors.toSet()), true);
            normalClient.registerParcel(parcel.pack(), 5000);
            itemResult = normalClient.getState(parcel.getPaymentContract().getId());
            if (itemResult.state == ItemState.APPROVED)
                paymentContract = parcel.getPaymentContract();
            System.out.println("registerParcel... done!");
            System.out.println("parcel.paymentContract.itemResult: " + itemResult);
            assertEquals(ItemState.APPROVED, itemResult.state);
            itemResult = normalClient.getState(contractToRegister.getId());
            System.out.println("contractToRegister.itemResult: " + itemResult);
            assertEquals(ItemState.APPROVED, itemResult.state);
        }
    }



    private Client createWhiteClient() {
        try {
            int nodeNumber = ThreadLocalRandom.current().nextInt(1, 11);
            String nodeUrl = "http://node-" + nodeNumber + "-pro.universa.io:8080";
            PrivateKey clientKey = TestKeys.privateKey(nodeNumber-1);
            return new Client(nodeUrl, clientKey, null, false);
        } catch (Exception e) {
            System.out.println("createWhiteClient exception: " + e.toString());
            return null;
        }
    }

    protected static final String ROOT_PATH = "./src/test_contracts/";

    Contract tuContract;
    Object tuContractLock = new Object();

    protected Contract getApprovedTUContract() throws Exception {
        synchronized (tuContractLock) {
            if (tuContract == null) {
                PrivateKey ownerKey = new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey"));
                Set<PublicKey> keys = new HashSet();
                keys.add(ownerKey.getPublicKey());
                Contract stepaTU = InnerContractsService.createFreshTU(100000000, keys);
                stepaTU.check();
                stepaTU.traceErrors();
                System.out.println("register new TU ");
                whiteClient.register(stepaTU.getPackedTransaction(),15000);
                tuContract = stepaTU;
            }
            int needRecreateTuContractNum = 0;
            for (Client client : normalClients) {
                int attempts = 10;
                ItemResult itemResult = client.getState(tuContract.getId());
                while(itemResult.state.isPending() && attempts-- > 0) {
                    itemResult = client.getState(tuContract.getId());
                    Thread.sleep(500);
                }
                if (itemResult.state != ItemState.APPROVED) {
                    System.out.println("TU: node " + client.getNodeNumber() + " result: " + itemResult);
                    needRecreateTuContractNum ++;
                }
            }

            int recreateBorder = NODES_COUNT - config.getPositiveConsensus() - 1;
            if(recreateBorder < 0)
                recreateBorder = 0;
            if (needRecreateTuContractNum > recreateBorder) {
                tuContract = null;
                Thread.sleep(1000);
                return getApprovedTUContract();
            }
            return tuContract;
        }
    }


    public synchronized Parcel createParcelWithClassTU(Contract c, Set<PrivateKey> keys) throws Exception {
        Contract tu = getApprovedTUContract();
        Parcel parcel =  ContractsService.createParcel(c, tu, 150, keys);
        return parcel;
    }

    protected synchronized Parcel registerWithNewParcel(Contract c) throws Exception {
        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel parcel = createParcelWithClassTU(c, stepaPrivateKeys);
        System.out.println("register  parcel: " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());

        normalClient.registerParcel(parcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }
        return parcel;
    }

    protected synchronized Parcel registerWithNewParcel(TransactionPack tp) throws Exception {

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Contract tu = getApprovedTUContract();
        // stepaPrivateKeys - is also U keys
        Parcel parcel =  ContractsService.createParcel(tp, tu, 150, stepaPrivateKeys);
        System.out.println("-------------");
        normalClient.registerParcel(parcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = parcel.getPaymentContract();
        }

        return parcel;
    }

    private synchronized void registerAndCheckApproved(Contract c) throws Exception {
        Parcel parcel = registerWithNewParcel(c);
        waitAndCheckApproved(parcel);
    }

    private synchronized void registerAndCheckApproved(TransactionPack tp) throws Exception {
        Parcel parcel = registerWithNewParcel(tp);
        waitAndCheckApproved(parcel);
    }

    private synchronized void waitAndCheckApproved(Parcel parcel) throws Exception {

        waitAndCheckState(parcel, ItemState.APPROVED);
    }

    private synchronized void registerAndCheckDeclined(Contract c) throws Exception {
        Parcel parcel = registerWithNewParcel(c);
        waitAndCheckDeclined(parcel);
    }

    private synchronized void registerAndCheckDeclined(TransactionPack tp) throws Exception {
        Parcel parcel = registerWithNewParcel(tp);
        waitAndCheckDeclined(parcel);
    }

    private synchronized void waitAndCheckDeclined(Parcel parcel) throws Exception {

        waitAndCheckState(parcel, ItemState.DECLINED);
    }

    private synchronized void waitAndCheckState(Parcel parcel, ItemState waitState) throws Exception {
        int attemps = 30;
        ItemResult itemResult;
        do {
            itemResult = normalClient.getState(parcel.getPaymentContract().getId());
            if(!itemResult.state.isPending())
                break;
            attemps--;
            if(attemps <= 0)
                fail("timeout1, parcel " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            Thread.sleep(500);
        } while(true);
        assertEquals(ItemState.APPROVED, itemResult.state);

        attemps = 30;
        do {
            itemResult = normalClient.getState(parcel.getPayloadContract().getId());
            if(!itemResult.state.isPending())
                break;
            attemps--;
            if(attemps <= 0)
                fail("timeout2, parcel " + parcel.getId() + " " + parcel.getPaymentContract().getId() + " " + parcel.getPayloadContract().getId());
            Thread.sleep(500);
        } while(true);

        assertEquals(waitState, itemResult.state);
    }

    /*
    @Test
    public void registerSlotContract() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        Contract simpleContract = new Contract(key);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.putTrackingContract(simpleContract);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.get("definition.extended_type"));
        assertEquals(100 * Config.kilobytesAndDaysPerU, slotContract.getPrepaidKilobytesForDays(), 0.01);

//        for(Node n : nodes) {
//            n.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
//        }
        normalClient.registerParcel(payingParcel.pack(),8000);
        ZonedDateTime timeReg1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        slotContract.traceErrors();
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        ItemResult itemResult = normalClient.getState(slotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(simpleContract.getId(), slotContract.getTrackingContract().getId());
        assertEquals(simpleContract.getId(), ((SlotContract) payingParcel.getPayload().getContract()).getTrackingContract().getId());


        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        ZonedDateTime now;

        double days = (double) 100 * Config.kilobytesAndDaysPerU * 1024 / simpleContract.getPackedTransaction().length;
        double hours = days * 24;
        long seconds = (long) (days * 24 * 3600);
        ZonedDateTime calculateExpires = timeReg1.plusSeconds(seconds);

        Set<ContractStorageSubscription> foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println(foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we store environment

        byte[] ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNotNull(ebytes);
        Binder binder = Boss.unpack(ebytes);
        assertNotNull(binder);


        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(key);
        refilledSlotContract.setNodeConfig(config);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract.get("definition.extended_type"));
        assertEquals((100 + 300) * Config.kilobytesAndDaysPerU, refilledSlotContract.getPrepaidKilobytesForDays(), 0.01);

        normalClient.registerParcel(payingParcel.pack(),8000);
        ZonedDateTime timeReg2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(refilledSlotContract.getNew().get(0).getId()).state);

        itemResult = normalClient.getState(refilledSlotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        long spentSeconds = (timeReg2.toEpochSecond() - timeReg1.toEpochSecond());
        double spentDays = (double) spentSeconds / (3600 * 24);
        double spentKDs = spentDays * (simpleContract.getPackedTransaction().length / 1024);

        days = (double) (100 + 300 - spentKDs) * Config.kilobytesAndDaysPerU * 1024 / simpleContract.getPackedTransaction().length;
        hours = days * 24;
        seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println(foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 3);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, normalClient.getState(slotContract.getId()).state);
        ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract.getId());
        assertNotNull(ebytes);
        binder = Boss.unpack(ebytes);
        assertNotNull(binder);


        // refill slot contract with U again (means add storing days). the oldest revision should removed

        SlotContract refilledSlotContract2 = (SlotContract) refilledSlotContract.createRevision(key);
        refilledSlotContract2.setNodeConfig(config);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract2.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract2.check();
        refilledSlotContract2.traceErrors();
        assertTrue(refilledSlotContract2.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract2.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract2.get("definition.extended_type"));
        assertEquals((100 + 300 + 300) * Config.kilobytesAndDaysPerU, refilledSlotContract2.getPrepaidKilobytesForDays(), 0.01);

        normalClient.registerParcel(payingParcel.pack(),8000);
        ZonedDateTime timeReg3 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(refilledSlotContract2.getNew().get(0).getId()).state);

        itemResult = normalClient.getState(refilledSlotContract2.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        spentSeconds = (timeReg3.toEpochSecond() - timeReg1.toEpochSecond());
        spentDays = (double) spentSeconds / (3600 * 24);
        spentKDs = spentDays * (simpleContract.getPackedTransaction().length / 1024);

        days = (double) (100 + 300 + 300 - spentKDs) * Config.kilobytesAndDaysPerU * 1024 / simpleContract.getPackedTransaction().length;
        hours = days * 24;
        seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println(foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, normalClient.getState(slotContract.getId()).state);
        ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract2.getId());
        assertNotNull(ebytes);
        binder = Boss.unpack(ebytes);
        assertNotNull(binder);


        // revoke slot contract, means remove stored contract from storage

        Contract revokingSlotContract = ContractsService.createRevocation(refilledSlotContract2, key);

        registerAndCheckApproved(revokingSlotContract);

        itemResult = normalClient.getState(refilledSlotContract2.getId());
        assertEquals(ItemState.REVOKED, itemResult.state);

        // check if we remove stored contract from storage

        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);

        // check if we remove subscriptions

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        assertNull(foundCssSet);

        // check if we remove environment

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract2.getId());
        assertNull(ebytes);
    }

    @Test
    public void slotContractNoPayment() throws Exception {
        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        Contract simpleContract = new Contract(key);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());


        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.putTrackingContract(simpleContract);


        normalClient.register(slotContract.getTransactionPack().pack());

        ItemResult rr;
        do {
            rr = normalClient.getState(slotContract.getId());
        } while (rr.state.isPending());

        assertEquals(rr.state, ItemState.UNDEFINED);


        registerAndCheckDeclined(slotContract);


        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 1, stepaPrivateKeys, false);

        normalClient.registerParcel(payingParcel.pack(),8000);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.DECLINED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.UNDEFINED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

    }

    @Test
    public void registerSlotContractWithStoringRevisions() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.setKeepRevisions(2);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.get("definition.extended_type"));
        assertEquals(100 * Config.kilobytesAndDaysPerU, slotContract.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + slotContract.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double)simpleContract.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double)100 * Config.kilobytesAndDaysPerU * 1024 / simpleContract.getPackedTransaction().length) + " days");

        normalClient.registerParcel(payingParcel.pack(),8000);
        ZonedDateTime timeReg1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        ItemResult itemResult = normalClient.getState(slotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(simpleContract.getId(), slotContract.getTrackingContract().getId());
        assertEquals(simpleContract.getId(), ((SlotContract) payingParcel.getPayload().getContract()).getTrackingContract().getId());

        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        double spentKDs = 0;
        ZonedDateTime calculateExpires;

        Set<ContractStorageSubscription> foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                double days = (double) 100 * Config.kilobytesAndDaysPerU * 1024 / simpleContract.getPackedTransaction().length;
                double hours = days * 24;
                long seconds = (long) (days * 24 * 3600);
                calculateExpires = timeReg1.plusSeconds(seconds);

                System.out.println("days " + days);
                System.out.println("hours " + hours);
                System.out.println("seconds " + seconds);
                System.out.println("reg time " + timeReg1);
                System.out.println("expected " + calculateExpires);
                System.out.println("found " + foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 3);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we store environment

        byte[] ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNotNull(ebytes);
        Binder binder = Boss.unpack(ebytes);
        assertNotNull(binder);

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeConfig(config);
        assertEquals(refilledSlotContract.getKeepRevisions(), 2);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        // note, that spent time is set while slot.seal() and seal calls from ContractsService.createPayingParcel
        // so sleep should be before seal for test calculations
        Thread.sleep(10000);

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract.get("definition.extended_type"));
        assertEquals((100 + 300) * Config.kilobytesAndDaysPerU, refilledSlotContract.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + refilledSlotContract.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double)simpleContract.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double)simpleContract2.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> Summ: " + ((double)(simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length) / 1024) + " Kb");
        System.out.println(">> " +  ((double)(100 + 300) * Config.kilobytesAndDaysPerU * 1024 / (simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length)) + " days");

        normalClient.registerParcel(payingParcel.pack(),8000);
        ZonedDateTime timeReg2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        refilledSlotContract.traceErrors();
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(refilledSlotContract.getNew().get(0).getId()).state);

        itemResult = normalClient.getState(refilledSlotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        long spentSeconds = (timeReg2.toEpochSecond() - timeReg1.toEpochSecond());
        double spentDays = (double) spentSeconds / (3600 * 24);
        spentKDs = spentDays * (simpleContract.getPackedTransaction().length / 1024);
//        calculateExpires = timeReg2.plusSeconds(((100 + 300) * Config.kilobytesAndDaysPerU * 1024 * 24 * 3600 - spentBs) /
//                (simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length));

        int totalLength = simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length;
        double days = (double) (100 + 300 - spentKDs) * Config.kilobytesAndDaysPerU * 1024 / totalLength;
        double hours = days * 24;
        long seconds = (long) (days * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds);


        System.out.println("spentSeconds " + spentSeconds);
        System.out.println("spentDays " + spentDays);
        System.out.println("spentKDs " + spentKDs * 1000000);
        System.out.println("days " + days);
        System.out.println("hours " + hours);
        System.out.println("seconds " + seconds);
        System.out.println("reg time " + timeReg1);
        System.out.println("totalLength " + totalLength);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println("expected:" + calculateExpires);
                System.out.println("found: " + foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract2.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println(foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, normalClient.getState(slotContract.getId()).state);
        ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract.getId());
        assertNotNull(ebytes);
        binder = Boss.unpack(ebytes);
        assertNotNull(binder);

        // create additional revision of stored contract

        Contract simpleContract3 = restoredContract.createRevision(key);
        simpleContract3.setOwnerKey(key.getPublicKey());
        simpleContract3.seal();
        simpleContract3.check();
        simpleContract3.traceErrors();
        assertTrue(simpleContract3.isOk());

        registerAndCheckApproved(simpleContract3);

        // refill slot contract with U again (means add storing days). the oldest revision should removed

        SlotContract refilledSlotContract2 = (SlotContract) refilledSlotContract.createRevision(key);
        refilledSlotContract2.putTrackingContract(simpleContract3);
        refilledSlotContract2.setNodeConfig(config);
        assertEquals(refilledSlotContract2.getKeepRevisions(), 2);
        refilledSlotContract2.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        // note, that spent time is set while slot.seal() and seal calls from ContractsService.createPayingParcel
        // so sleep should be before seal for test calculations
        Thread.sleep(10000);

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract2.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract2.check();
        refilledSlotContract2.traceErrors();
        assertTrue(refilledSlotContract2.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract2.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract2.get("definition.extended_type"));
        assertEquals((100 + 300 + 300) * Config.kilobytesAndDaysPerU, refilledSlotContract2.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + refilledSlotContract2.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double)simpleContract2.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double)simpleContract3.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> Summ: " + ((double)(simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length) / 1024) + " Kb");
        System.out.println(">> " + ((double)(100 + 300 + 300) * Config.kilobytesAndDaysPerU * 1024 / (simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length)) + " days");

        normalClient.registerParcel(payingParcel.pack(),8000);
        ZonedDateTime timeReg3 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(refilledSlotContract2.getNew().get(0).getId()).state);

        itemResult = normalClient.getState(refilledSlotContract2.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        assertNull(foundCssSet);

        // check revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());


//        spentKDs += (timeReg3.toEpochSecond() - timeReg2.toEpochSecond()) * (simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length);
//        calculateExpires = timeReg2.plusSeconds(((100 + 300 + 300) * Config.kilobytesAndDaysPerU * 1024 * 24 * 3600 - (long)spentKDs) /
//                (simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length));
        long spentSeconds2 = (timeReg3.toEpochSecond() - timeReg2.toEpochSecond());
        double spentDays2 = (double) spentSeconds2 / (3600 * 24);
        spentKDs += spentDays2 * ((simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length) / 1024);

        int totalLength2 = simpleContract2.getPackedTransaction().length + simpleContract3.getPackedTransaction().length;
        double days2 = (double) (100 + 300 + 300 - spentKDs) * Config.kilobytesAndDaysPerU * 1024 / totalLength2;
        double hours2 = days2 * 24;
        long seconds2 = (long) (days2 * 24 * 3600);
        calculateExpires = timeReg3.plusSeconds(seconds2);


        System.out.println("spentSeconds " + spentSeconds2);
        System.out.println("spentDays " + spentDays2);
        System.out.println("spentKDs " + spentKDs * 1000000);
        System.out.println("days " + days2);
        System.out.println("hours " + hours2);
        System.out.println("seconds " + seconds2);
        System.out.println("reg time " + timeReg3);
        System.out.println("totalLength " + totalLength2);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract2.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println(foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check additional revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract3.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract3.getId(), restoredContract.getId());

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract3.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println(foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, normalClient.getState(slotContract.getId()).state);
        ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract2.getId());
        assertNotNull(ebytes);
        binder = Boss.unpack(ebytes);
        assertNotNull(binder);

        // check reducing number of keep revisions

        SlotContract refilledSlotContract3 = (SlotContract) refilledSlotContract2.createRevision(key);
        refilledSlotContract3.setKeepRevisions(1);
        refilledSlotContract3.setNodeConfig(config);
        refilledSlotContract3.seal();

        // payment contract
        paymentContract = getApprovedTUContract();

        // note, that spent time is set while slot.seal() and seal calls from ContractsService.createPayingParcel
        // so sleep should be before seal for test calculations
        Thread.sleep(10000);

        payingParcel = ContractsService.createPayingParcel(refilledSlotContract3.getTransactionPack(), paymentContract, 1, 300, stepaPrivateKeys, false);

        refilledSlotContract3.check();
        refilledSlotContract3.traceErrors();
        assertTrue(refilledSlotContract3.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract3.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), refilledSlotContract3.get("definition.extended_type"));
        assertEquals((100 + 300 + 300 + 300) * Config.kilobytesAndDaysPerU, refilledSlotContract3.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + refilledSlotContract3.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double)simpleContract3.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double)(100 + 300 + 300 + 300) * Config.kilobytesAndDaysPerU * 1024 / simpleContract3.getPackedTransaction().length) + " days");

        normalClient.registerParcel(payingParcel.pack(),8000);
        ZonedDateTime timeReg4 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(refilledSlotContract3.getNew().get(0).getId()).state);

        itemResult = normalClient.getState(refilledSlotContract3.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        assertNull(foundCssSet);

        // check revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNull(restoredPackedData);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract2.getId());
        assertNull(foundCssSet);

        // check additional (last) revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract3.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract3.getId(), restoredContract.getId());

//        spentKDs += (timeReg4.toEpochSecond() - timeReg3.toEpochSecond()) * (simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length);
//        calculateExpires = timeReg3.plusSeconds(((100 + 300 + 300 + 300) * Config.kilobytesAndDaysPerU * 1024 * 24 * 3600 - (long) spentKDs) / simpleContract3.getPackedTransaction().length);

        long spentSeconds3 = (timeReg4.toEpochSecond() - timeReg3.toEpochSecond());
        double spentDays3 = (double) spentSeconds3 / (3600 * 24);
        spentKDs += spentDays3 * ((simpleContract2.getPackedTransaction().length + simpleContract3.getPackedTransaction().length) / 1024);

        int totalLength3 = simpleContract3.getPackedTransaction().length;
        double days3 = (double) (100 + 300 + 300 + 300 - spentKDs) * Config.kilobytesAndDaysPerU * 1024 / totalLength3;
        double hours3 = days3 * 24;
        long seconds3 = (long) (days3 * 24 * 3600);
        calculateExpires = timeReg4.plusSeconds(seconds3);


        System.out.println("spentSeconds " + spentSeconds3);
        System.out.println("spentDays " + spentDays3);
        System.out.println("spentKDs " + spentKDs * 1000000);
        System.out.println("days " + days3);
        System.out.println("hours " + hours3);
        System.out.println("seconds " + seconds3);
        System.out.println("reg time " + timeReg3);
        System.out.println("totalLength " + totalLength3);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract3.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println(foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we updated environment and subscriptions (remove old, create new)

        assertEquals(ItemState.REVOKED, normalClient.getState(slotContract.getId()).state);
        ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract2.getId());
        assertNull(ebytes);

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract3.getId());
        assertNotNull(ebytes);
        binder = Boss.unpack(ebytes);
        assertNotNull(binder);

        // revoke slot contract, means remove stored contract from storage

        Contract revokingSlotContract = ContractsService.createRevocation(refilledSlotContract3, key);

        registerAndCheckApproved(revokingSlotContract);

        itemResult = normalClient.getState(refilledSlotContract3.getId());
        assertEquals(ItemState.REVOKED, itemResult.state);

        // check if we remove stored contract from storage

        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNull(restoredPackedData);
        restoredPackedData = ledger.getContractInStorage(simpleContract3.getId());
        assertNull(restoredPackedData);

        // check if we remove subscriptions

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        assertNull(foundCssSet);
        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract2.getId());
        assertNull(foundCssSet);
        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract3.getId());
        assertNull(foundCssSet);

        // check if we remove environment

        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract3.getId());
        assertNull(ebytes);
    }


    @Test
    public void registerSlotContractWithUpdateStoringRevisions() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.setKeepRevisions(1);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.getDefinition().getExtendedType());
        assertEquals(NSmartContract.SmartContractType.SLOT1.name(), slotContract.get("definition.extended_type"));
        assertEquals(100 * Config.kilobytesAndDaysPerU, slotContract.getPrepaidKilobytesForDays(), 0.01);
        System.out.println(">> " + slotContract.getPrepaidKilobytesForDays() + " KD");
        System.out.println(">> " + ((double) simpleContract.getPackedTransaction().length / 1024) + " Kb");
        System.out.println(">> " + ((double) 100 * Config.kilobytesAndDaysPerU * 1024 / simpleContract.getPackedTransaction().length) + " days");

        normalClient.registerParcel(payingParcel.pack(),8000);
        ZonedDateTime timeReg1 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        ItemResult itemResult = normalClient.getState(slotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        assertEquals(simpleContract.getId(), slotContract.getTrackingContract().getId());
        assertEquals(simpleContract.getId(), ((SlotContract) payingParcel.getPayload().getContract()).getTrackingContract().getId());

        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        double spentKDs = 0;
        ZonedDateTime calculateExpires;

        Set<ContractStorageSubscription> foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        if (foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                double days = (double) 100 * Config.kilobytesAndDaysPerU * 1024 / simpleContract.getPackedTransaction().length;
                double hours = days * 24;
                long seconds = (long) (days * 24 * 3600);
                calculateExpires = timeReg1.plusSeconds(seconds);

                System.out.println("days " + days);
                System.out.println("hours " + hours);
                System.out.println("seconds " + seconds);
                System.out.println("reg time " + timeReg1);
                System.out.println("expected " + calculateExpires);
                System.out.println("found " + foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }

        // check if we store environment

        byte[] ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNotNull(ebytes);
        Binder binder = Boss.unpack(ebytes);
        assertNotNull(binder);

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        ZonedDateTime timeReg2 = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        registerAndCheckApproved(simpleContract2);

        System.err.println("check " + simpleContract.getId());
        // check root stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        assertNull(foundCssSet);

        // check revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());


//        spentKDs += (timeReg3.toEpochSecond() - timeReg2.toEpochSecond()) * (simpleContract.getPackedTransaction().length + simpleContract2.getPackedTransaction().length);
//        calculateExpires = timeReg2.plusSeconds(((100 + 300 + 300) * Config.kilobytesAndDaysPerU * 1024 * 24 * 3600 - (long)spentKDs) /
//                (simpleContract3.getPackedTransaction().length + simpleContract2.getPackedTransaction().length));
        long spentSeconds2 = (timeReg2.toEpochSecond() - timeReg1.toEpochSecond());
        double spentDays2 = (double) spentSeconds2 / (3600 * 24);
        spentKDs += spentDays2 * ((simpleContract.getPackedTransaction().length) / 1024);

        int totalLength2 = simpleContract2.getPackedTransaction().length;
        double days2 = (double) (100 - spentKDs) * Config.kilobytesAndDaysPerU * 1024 / totalLength2;
        double hours2 = days2 * 24;
        long seconds2 = (long) (days2 * 24 * 3600);
        calculateExpires = timeReg2.plusSeconds(seconds2);


        System.out.println("spentSeconds " + spentSeconds2);
        System.out.println("spentDays " + spentDays2);
        System.out.println("spentKDs " + spentKDs * 1000000);
        System.out.println("days " + days2);
        System.out.println("hours " + hours2);
        System.out.println("seconds " + seconds2);
        System.out.println("reg time " + timeReg2);
        System.out.println("totalLength " + totalLength2);

        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract2.getId());
        if(foundCssSet != null) {
            for (ContractStorageSubscription foundCss : foundCssSet) {
                System.out.println(foundCss.expiresAt());
                assertAlmostSame(calculateExpires, foundCss.expiresAt(), 6);
            }
        } else {
            fail("ContractStorageSubscription was not found");
        }
    }

    @Test
    public void registerSlotContractCheckSetKeepRevisions() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.setKeepRevisions(5);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        TransactionPack tp_before = slotContract.getTransactionPack();
        TransactionPack tp_after = TransactionPack.unpack(tp_before.pack());

        assertEquals(((SlotContract)tp_after.getContract()).getKeepRevisions(), 5);

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeConfig(config);
        refilledSlotContract.seal();

        // check saving keepRevisions
        assertEquals(refilledSlotContract.getKeepRevisions(), 5);

        tp_before = refilledSlotContract.getTransactionPack();
        tp_after = TransactionPack.unpack(tp_before.pack());

        assertEquals(((SlotContract)tp_after.getContract()).getKeepRevisions(), 5);
    }

    @Test
    public void registerSlotContractRevision() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        ItemResult itemResult = normalClient.getState(slotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.setKeepRevisions(2);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeConfig(config);
        refilledSlotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        // revision should be created without additional payments (only setKeepRevisions and putTrackingContract)
        payingParcel = ContractsService.createPayingParcel(refilledSlotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(refilledSlotContract.getNew().get(0).getId()).state);

        itemResult = normalClient.getState(refilledSlotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // check revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());
    }

    @Test
    public void registerSlotContractCheckAddNewStoringRevision() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));
        Parcel payingParcel = ContractsService.createPayingParcel(slotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        ItemResult itemResult = normalClient.getState(slotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create other stored contract

        Contract otherContract = new Contract(key);
        otherContract.seal();
        otherContract.check();
        otherContract.traceErrors();
        assertTrue(otherContract.isOk());

        registerAndCheckApproved(otherContract);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract newSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        newSlotContract.setKeepRevisions(2);
        newSlotContract.putTrackingContract(otherContract);
        newSlotContract.setNodeConfig(config);
        newSlotContract.seal();

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(newSlotContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);

        // imitating check process on the node
        newSlotContract.beforeUpdate(new NMutableEnvironment(newSlotContract));
        newSlotContract.check();
        newSlotContract.traceErrors();

        // check error of adding other contract (not revision of old tracking contract)
        assertFalse(newSlotContract.isOk());

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.DECLINED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.UNDEFINED, normalClient.getState(newSlotContract.getNew().get(0).getId()).state);
    }

    @Test
    public void registerSlotContractInNewItem() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        Contract baseContract = new Contract(key);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();
        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        baseContract.addNewItems(slotContract);

        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        assertTrue(baseContract.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Parcel payingParcel = ContractsService.createPayingParcel(baseContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract.getNew())
            if (!c.equals(slotContract)) {
                baseContract.getNewItems().remove(c);
                slotContract.addNewItems(c);
                slotContract.seal();
                baseContract.seal();
                break;
            }

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = slotContract.getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        ItemResult itemResult = normalClient.getState(slotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));

        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Contract baseContract2 = new Contract(key);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.setKeepRevisions(2);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeConfig(config);
        refilledSlotContract.seal();
        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        baseContract2.addNewItems(refilledSlotContract);

        baseContract2.seal();
        baseContract2.check();
        baseContract2.traceErrors();
        assertTrue(baseContract2.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(baseContract2.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract2.getNew())
            if (!c.equals(refilledSlotContract)) {
                baseContract2.getNewItems().remove(c);
                refilledSlotContract.addNewItems(c);
                refilledSlotContract.seal();
                baseContract2.seal();
                break;
            }

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = refilledSlotContract.getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(refilledSlotContract.getNew().get(0).getId()).state);

        itemResult = normalClient.getState(refilledSlotContract.getId());
        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));

        // check root stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // check revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());

        // revoke slot contract, means remove stored contract from storage

        Contract revokingSlotContract = ContractsService.createRevocation(refilledSlotContract, key);

        registerAndCheckApproved(revokingSlotContract);

        itemResult = normalClient.getState(refilledSlotContract.getId());
        assertEquals(ItemState.REVOKED, itemResult.state);

        // check if we remove stored contract from storage

        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNull(restoredPackedData);
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNull(restoredPackedData);
        restoredPackedData = ledger.getContractInStorage(slotContract.getId());
        assertNull(restoredPackedData);
        restoredPackedData = ledger.getContractInStorage(refilledSlotContract.getId());
        assertNull(restoredPackedData);

        // check if we remove subscriptions

        Set<ContractStorageSubscription> foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract.getId());
        assertNull(foundCssSet);
        foundCssSet = ledger.getStorageSubscriptionsForContractId(simpleContract2.getId());
        assertNull(foundCssSet);
        foundCssSet = ledger.getStorageSubscriptionsForContractId(slotContract.getId());
        assertNull(foundCssSet);
        foundCssSet = ledger.getStorageSubscriptionsForContractId(refilledSlotContract.getId());
        assertNull(foundCssSet);

        // check if we remove environment

        byte[] ebytes = ledger.getEnvironmentFromStorage(simpleContract.getId());
        assertNull(ebytes);
        ebytes = ledger.getEnvironmentFromStorage(simpleContract2.getId());
        assertNull(ebytes);
        ebytes = ledger.getEnvironmentFromStorage(slotContract.getId());
        assertNull(ebytes);
        ebytes = ledger.getEnvironmentFromStorage(refilledSlotContract.getId());
        assertNull(ebytes);
    }

    @Test
    public void registerSlotContractInNewItemBadCreate() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        Contract baseContract = new Contract(key);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();
        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        baseContract.addNewItems(slotContract);

        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        assertTrue(baseContract.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        // provoke error FAILED_CHECK, "Payment for slot contract is below minimum level of " + nodeConfig.getMinSlotPayment() + "U");
        Parcel payingParcel = ContractsService.createPayingParcel(baseContract.getTransactionPack(), paymentContract, 1, config.getMinSlotPayment() - 1, stepaPrivateKeys, false);
        for (Contract c: baseContract.getNew())
            if (!c.equals(slotContract)) {
                baseContract.getNewItems().remove(c);
                slotContract.addNewItems(c);
                slotContract.seal();
                baseContract.seal();
                break;
            }

        normalClient.registerParcel(payingParcel.pack(),8000);
        payingParcel.getPayload().getContract().traceErrors();

        // check declined payload contract
        assertEquals(ItemState.DECLINED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
    }

    @Test
    public void registerSlotContractInNewItemBadUpdate() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        Contract baseContract = new Contract(key);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();
        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        baseContract.addNewItems(slotContract);

        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        assertTrue(baseContract.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Parcel payingParcel = ContractsService.createPayingParcel(baseContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract.getNew())
            if (!c.equals(slotContract)) {
                baseContract.getNewItems().remove(c);
                slotContract.addNewItems(c);
                slotContract.seal();
                baseContract.seal();
                break;
            }

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = slotContract.getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Contract baseContract2 = new Contract(key);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        // provoke error FAILED_CHECK, "Creator of Slot-contract must has allowed keys for owner of tracking contract");
        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(key);
        refilledSlotContract.setKeepRevisions(2);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeConfig(config);
        refilledSlotContract.seal();
        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        baseContract2.addNewItems(refilledSlotContract);

        baseContract2.seal();
        baseContract2.check();
        baseContract2.traceErrors();
        assertTrue(baseContract2.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(baseContract2.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract2.getNew())
            if (!c.equals(refilledSlotContract)) {
                baseContract2.getNewItems().remove(c);
                refilledSlotContract.addNewItems(c);
                refilledSlotContract.seal();
                baseContract2.seal();
                break;
            }

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = refilledSlotContract.getNew().get(0);
        }
        payingParcel.getPayload().getContract().traceErrors();

        // check declined payload contract
        assertEquals(ItemState.DECLINED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
    }

    @Ignore
    @Test
    public void registerSlotContractInNewItemBadRevoke() throws Exception {

        final PrivateKey key = new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey"));
        Set<PrivateKey> slotIssuerPrivateKeys = new HashSet<>();
        slotIssuerPrivateKeys.add(key);
        Set<PublicKey> slotIssuerPublicKeys = new HashSet<>();
        slotIssuerPublicKeys.add(key.getPublicKey());

        // contract for storing

        SimpleRole ownerRole = new SimpleRole("owner", slotIssuerPublicKeys);
        ChangeOwnerPermission changeOwnerPerm = new ChangeOwnerPermission(ownerRole);

        Contract simpleContract = new Contract(key);
        simpleContract.addPermission(changeOwnerPerm);
        simpleContract.seal();
        simpleContract.check();
        simpleContract.traceErrors();
        assertTrue(simpleContract.isOk());

        registerAndCheckApproved(simpleContract);

        Contract baseContract = new Contract(key);

        // slot contract that storing

        SlotContract slotContract = ContractsService.createSlotContract(slotIssuerPrivateKeys, slotIssuerPublicKeys);
        slotContract.setNodeConfig(config);
        slotContract.putTrackingContract(simpleContract);
        slotContract.seal();
        slotContract.check();
        slotContract.traceErrors();
        assertTrue(slotContract.isOk());

        baseContract.addNewItems(slotContract);

        baseContract.seal();
        baseContract.check();
        baseContract.traceErrors();
        assertTrue(baseContract.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        Contract paymentContract = getApprovedTUContract();

        Set<PrivateKey> stepaPrivateKeys = new HashSet<>();
        stepaPrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "keys/stepan_mamontov.private.unikey")));

        Parcel payingParcel = ContractsService.createPayingParcel(baseContract.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract.getNew())
            if (!c.equals(slotContract)) {
                baseContract.getNewItems().remove(c);
                slotContract.addNewItems(c);
                slotContract.seal();
                baseContract.seal();
                break;
            }

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = slotContract.getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(slotContract.getNew().get(0).getId()).state);

        // check if we store same contract as want

        byte[] restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        Contract restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // create revision of stored contract

        Contract simpleContract2 = restoredContract.createRevision(key);
        simpleContract2.setOwnerKey(stepaPrivateKeys.iterator().next().getPublicKey());
        simpleContract2.seal();
        simpleContract2.check();
        simpleContract2.traceErrors();
        assertTrue(simpleContract2.isOk());

        registerAndCheckApproved(simpleContract2);

        Contract baseContract2 = new Contract(key);

        Set<PrivateKey> keysSlotRevisions = new HashSet<>();
        keysSlotRevisions.add(key);
        keysSlotRevisions.add(stepaPrivateKeys.iterator().next());

        // refill slot contract with U (means add storing days).

        SlotContract refilledSlotContract = (SlotContract) slotContract.createRevision(keysSlotRevisions);
        refilledSlotContract.setKeepRevisions(2);
        refilledSlotContract.putTrackingContract(simpleContract2);
        refilledSlotContract.setNodeConfig(config);
        refilledSlotContract.seal();
        refilledSlotContract.check();
        refilledSlotContract.traceErrors();
        assertTrue(refilledSlotContract.isOk());

        baseContract2.addNewItems(refilledSlotContract);

        baseContract2.seal();
        baseContract2.check();
        baseContract2.traceErrors();
        assertTrue(baseContract2.isOk());

        // payment contract
        // will create two revisions in the createPayingParcel, first is pay for register, second is pay for storing

        paymentContract = getApprovedTUContract();

        payingParcel = ContractsService.createPayingParcel(baseContract2.getTransactionPack(), paymentContract, 1, 100, stepaPrivateKeys, false);
        for (Contract c: baseContract2.getNew())
            if (!c.equals(refilledSlotContract)) {
                baseContract2.getNewItems().remove(c);
                refilledSlotContract.addNewItems(c);
                refilledSlotContract.seal();
                baseContract2.seal();
                break;
            }

        normalClient.registerParcel(payingParcel.pack(),8000);
        synchronized (tuContractLock) {
            tuContract = refilledSlotContract.getNew().get(0);
        }
        // check payment and payload contracts
        assertEquals(ItemState.REVOKED, normalClient.getState(payingParcel.getPayment().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(payingParcel.getPayload().getContract().getId()).state);
        assertEquals(ItemState.APPROVED, normalClient.getState(refilledSlotContract.getNew().get(0).getId()).state);

        // check root stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract.getId(), restoredContract.getId());

        // check revision of stored contract
        restoredPackedData = ledger.getContractInStorage(simpleContract2.getId());
        assertNotNull(restoredPackedData);
        restoredContract = Contract.fromPackedTransaction(restoredPackedData);
        assertNotNull(restoredContract);
        assertEquals(simpleContract2.getId(), restoredContract.getId());

        // provoke error FAILED_CHECK, "Creator of Slot-contract must has allowed keys for owner of tracking contract");
        refilledSlotContract.setCreator(Do.list(slotIssuerPublicKeys));

        // revoke slot contract, means remove stored contract from storage

        Contract revokingSlotContract = ContractsService.createRevocation(refilledSlotContract, key);

        registerAndCheckDeclined(revokingSlotContract);
    }*/


    /*@Test
    public void goodNSmartContractFromDSLWithSending() throws Exception {
        Contract smartContract = NSmartContract.fromDslFile(ROOT_PATH + "NotaryNSmartDSLTemplate.yml");
        smartContract.addSignerKeyFromFile(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey");
        smartContract.seal();
        smartContract.check();
        smartContract.traceErrors();
        assertTrue(smartContract.isOk());

        Contract gotContract = imitateSendingTransactionToPartner(smartContract);

        assertTrue(gotContract instanceof NSmartContract);
        assertTrue(gotContract instanceof NContract);

        registerAndCheckApproved(gotContract);

        ItemResult itemResult = normalClient.getState(gotContract.getId(), 8000);
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onCreatedResult").getString("status", null));
//        assertEquals("ok", itemResult.extraDataBinder.getBinder("onUpdateResult").getString("status", null));
    }*/

    private static final String uno_flint004_rev1_bin_b64 = "JyNkYXRhxA0GHxtuZXcGQ3Jldm9raW5nHUNjb250cmFjdCdLYXBpX2xldmVsGDNfX3R5cGWDVW5pdmVyc2FDb250cmFjdFNkZWZpbml0aW9uLyNkYXRhF1Npc3N1ZXJOYW1luxdVbml2ZXJzYSBSZXNlcnZlIFN5c3RlbSNuYW1ls3RyYW5zYWN0aW9uIHVuaXRzIHBhY2tTcmVmZXJlbmNlcx1bcGVybWlzc2lvbnMnM0dzX2xYaTeFo2RlY3JlbWVudF9wZXJtaXNzaW9uS21pbl92YWx1ZQAjcm9sZR9bdGFyZ2V0X25hbWUrb3duZXJFQ1JvbGVMaW5rhTNvd25lcjJDbWF4X3N0ZXAKU2ZpZWxkX25hbWWLdHJhbnNhY3Rpb25fdW5pdHNFu0Bjb20uaWNvZGljaS51bml2ZXJzYS5jb250cmFjdC5wZXJtaXNzaW9ucy5DaGFuZ2VOdW1iZXJQZXJtaXNzaW9uM1pBOU50MjeFvRe9GAC9Gb0avR8KvSCzdGVzdF90cmFuc2FjdGlvbl91bml0c0W9IjNDYVZuOXIfvRknI2tleXMOFxtrZXkXRWNSU0FQdWJsaWNLZXkzcGFja2VkxAkBHggcAQABxAABxSSWfXW20wGsRn9khVZDtvcCtUqP/scN3oVPU3r0152Fu62pfx9Mjc1cmQnRYSkeZzWA50RYQTU3FlXC5iIN7w+Lm6TGPQxWe+uYGMgKLCbAmyMXPWupvzeB5SEMtylQ5ml12iwFQkamqOiFE2KWMYz/UGhW87/ELPckmpoanZUa8OGCACUfFGALAZV0G+rQ/8xiW3hkGHmOFP0kejZaCZGPO/XGVay+2q0V2cw6CHar+D9F9FomXYA4bAInlY3zOLHdG8ddUTzhHQWOKmzoF9eIW67U9rd9qIR04U9ls9wGLQchqlG/kxHHfR4Js86mwYNgUKW49fQRppig+SsrjUVLS2V5UmVjb3JkRVNTaW1wbGVSb2xlhTNpc3N1ZXI7YW5vbklkcx1Fq0NoYW5nZU93bmVyUGVybWlzc2lvboVjY2hhbmdlX293bmVyM2JNc09ZMi+FvRe9GAC9GR+9G70zRb0dhTtpc3N1ZXIyvSC9JUW9IlNjcmVhdGVkX2F0eQ8JIVWFvTMnvSkOF70sF0W9Lr0vxAkBHggcAQABxAABxSSWfXW20wGsRn9khVZDtvcCtUqP/scN3oVPU3r0152Fu62pfx9Mjc1cmQnRYSkeZzWA50RYQTU3FlXC5iIN7w+Lm6TGPQxWe+uYGMgKLCbAmyMXPWupvzeB5SEMtylQ5ml12iwFQkamqOiFE2KWMYz/UGhW87/ELPckmpoanZUa8OGCACUfFGALAZV0G+rQ/8xiW3hkGHmOFP0kejZaCZGPO/XGVay+2q0V2cw6CHar+D9F9FomXYA4bAInlY3zOLHdG8ddUTzhHQWOKmzoF9eIW67U9rd9qIR04U9ls9wGLQchqlG/kxHHfR4Js86mwYNgUKW49fQRppig+SsrjUW9MUW9MoW9M700HStzdGF0ZU+9HCe9KQ4XvSwXRb0uvS/ECQEeCBwBAAHEAAGrOi7YKiKv4jCJhXMUN7x7120EL0Q179+YC3kM6ojRavDNmnnGyHCa3HEh6TZim2/bdWsCJeU3k7dlCt09E6421ApyTSt+WDe7xFySu/rVQoVGuXOyw97Oiaq6/NfbzUismNMTrDgWYtGXCGLP4RrwG7wulb7fgwevuuNgTXtn4p01mlrWfGaPR8E+kS9XOXLPDx3OUXNYByYHX5GKOvdFNfOoFYlsf/xEM4Eqa1GsTixEcJ7+OZCn2loVEMxna1DxtD7rorx8tSTWfp6h4qwcmcgXY1RKvsZj0rrf4PwqUhYwkp5cfbE9dqHv525aoHO5k3EdDeRuqodcZOh2QEu9Rb0xRb0yhb0cvTQdM3BhcmVudAVTZXhwaXJlc19hdHkPcCVkhWUXvSEgvSXAECdLYnJhbmNoX2lkBTNvcmlnaW4FvTt5DwkhVYVTY3JlYXRlZF9ieR+9G70zRb0dhTtjcmVhdG9yQ3JldmlzaW9uCCN0eXBlU3VuaWNhcHN1bGU7dmVyc2lvbhhTc2lnbmF0dXJlcw7EkgEXI3NpZ27EAAE3KMYISMZ4FRmlkEPV4VmkSKDom2VNBEiClh9mNwnzF45IHStnS7LGy8i9ZMY5V6gMdbG0hvgrKxVZPTMYD2Yp9De7LKE+E3MlXg2GAY/YaXD5lDeYC+cECCbERlFOOhzg4lWzNnu7Qn+K2SVCvJ61K/dGHlO33vt9GueKO43rwgPg2TxBuaXca0z+dRVZX57l0A9WuwpND9uBx0enYtxazfjMHFpPyWPiCqmFjpRWBQ6hYVevypqKy9RqrrisnM9Cbrh1jU+ERd2wBFAlN4byF7FKRF5DJRt+CX9bdk7p6gkzG6A/YqVzf+gru+JrRsjgJJd/1Rw8u+rI60hkUHynI2V4dHO8gh8zc2hhNTEyvEBI0fCkiSs+6VfbX5k4qN/DBFfCQWiaygnGC3A6ikR/8aJ6GIQGwnG5wjr2CJd9wgurOfKMfmoVZi86sgAzKowXU2NyZWF0ZWRfYXR5DwkhVYUba2V5vCEHucc+TxvM9el1aV8pt25c2FqaAvPHuKqggaGHbxLHTJ0=";
    private static final String uno_flint004_privKey_b64 = "JgAcAQABvIDayzI6N7cLoXiAf826OwDmGbU/RYl0MrhCaRx1dExXJYAMOEnSbgIP5+VkCjbuJqLL8tAIGaatZIHmFBXDg3Ub75Y82spLZ+sCblnpO+lY3f4AXN9unXCiUa44W9ysYEOTiQYxCROohis5A33C/wVt+aMSq2TGMaQuIcTJKkuSnbyAyFhDk7PrnjW6WuFm615F/bIeNZssuUhmBs9zus/05mIIlzRX0tRv1xVNpsUXyKJ8I5MMIxRyIkvD2IOdjJ2CxGO36C2KIze6lZ6r1+hYWaUT10aH5ToxkRS8jZhPTrOshZ0n2kGrDlPLxU8hf3JHHPBMMNEvzmbn0pM8oSaiQ6E=";

}
