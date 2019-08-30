package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Decimal;
import com.icodici.universa.TestCase;
import com.icodici.universa.TestKeys;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Test;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

public class UnsMainTest extends BaseMainTest {
    @Test
    public void unsCase() throws Exception {
        TestSpace ts = prepareTestSpace();

        PrivateKey keyToRegister = new PrivateKey(2048);
        PrivateKey contractToRegisterIssuer = TestKeys.privateKey(1);
        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(2);
        ts.nodes.forEach(n->n.config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress()));

        Contract referencesContract = new Contract(contractToRegisterIssuer);
        referencesContract.seal();


        PrivateKey unsIssuer = TestKeys.privateKey(3);

        //create initial contract
        UnsContract uns = ContractsService.createUnsContract(new HashSet<>(Do.listOf(unsIssuer)), new HashSet<>(Do.listOf(unsIssuer.getPublicKey())), nodeInfoProvider);

        String name = "Universa"+ ZonedDateTime.now();
        String desc = "Universa keys and origins";
        uns.addName(name,name,desc);
        uns.addKey(keyToRegister.getPublicKey());
        uns.addData(Binder.of("host","192.168.1.1"));

        uns.seal();

        ZonedDateTime created = uns.getCreatedAt();

        registerWithMinimumKeys(referencesContract,Do.listOf(contractToRegisterIssuer),ts,0);

        //register and ensure every key is required and can't be skipped
        int paidU = 1470;
        registerWithMinimumKeys(uns,Do.listOf(unsIssuer,keyToRegister,authorizedNameServiceKey),ts,paidU);


        //query contract from the network
        uns = (UnsContract) Contract.fromPackedTransaction(ts.client.queryNameContract(name));
        assertEquals(uns.getAllData().get(0).getString("host"),"192.168.1.1");

        Binder res = ts.client.queryNameRecord(keyToRegister.getPublicKey().getLongAddress().toString());
        List<Binder> names = res.getListOrThrow("names");
        assertEquals(names.get(0).getString("name"),name);
        ZonedDateTime expires = created.plusSeconds((long) (3600*24*paidU*configForProvider.getServiceRate(NSmartContract.SmartContractType.UNS1.name()).doubleValue()));
        TestCase.assertAlmostSame(((ZonedDateTime)names.get(0).get("expiresAt")),expires);


        //create revision and change data associated
        uns = (UnsContract) uns.createRevision();
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.getAllData().get(0).put("host","192.168.1.2");
        uns.seal();

        //make sure no payment is required and no additional keys rather than
        //owner key is required to modify data record
        registerWithMinimumKeys(uns,Do.listOf(unsIssuer),ts,0);

        uns = (UnsContract) Contract.fromPackedTransaction(ts.client.queryNameContract(name));
        assertEquals(uns.getAllData().get(0).getString("host"),"192.168.1.2");

        uns = (UnsContract) uns.createRevision();
        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();

        int paidU2 = 1460;
        //top up balance
        registerWithMinimumKeys(uns,Do.listOf(unsIssuer),ts,paidU2);


        res = ts.client.queryNameRecord(keyToRegister.getPublicKey().getLongAddress().toString());
        names = res.getListOrThrow("names");
        assertEquals(names.get(0).getString("name"),name);
        expires = created.plusSeconds((long) (3600*24*(paidU+paidU2)*configForProvider.getServiceRate(NSmartContract.SmartContractType.UNS1.name()).doubleValue()));
        TestCase.assertAlmostSame(((ZonedDateTime)names.get(0).get("expiresAt")),expires);


        ts.shutdown();
    }

    @Test
    public void testUnsApi() throws Exception {

        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));
        Set<PublicKey> manufacturePublicKeys = new HashSet<>();
        manufacturePublicKeys.add(manufacturePrivateKeys.iterator().next().getPublicKey());

        TestSpace testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        testSpace.nodes.forEach(m -> {
            m.config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress());
            m.config.setIsFreeRegistrationsAllowedFromYaml(true);
        });

        Decimal namesAndDaysPerU = testSpace.client.unsRate();
        System.out.println("unsRate: " + namesAndDaysPerU);
        assertEquals(testSpace.node.config.getServiceRate("UNS1").doubleValue(), namesAndDaysPerU.doubleValue(), 0.000001);

        Contract simpleContract = new Contract(manufacturePrivateKeys.iterator().next());
        simpleContract.seal();
        ItemResult itemResult = testSpace.client.register(simpleContract.getPackedTransaction(), 5000);
        assertEquals(ItemState.APPROVED, itemResult.state);

        String unsTestName = "testContractName" + Instant.now().getEpochSecond();

        // check uns contract with origin record
        UnsContract unsContract = ContractsService.createUnsContractForRegisterContractName(manufacturePrivateKeys,
                manufacturePublicKeys, nodeInfoProvider, unsTestName, "test contract name", "http://test.com", simpleContract);
        unsContract.getName(unsTestName).setUnsReducedName(unsTestName);
        unsContract.addSignerKey(authorizedNameServiceKey);
        unsContract.seal();
        unsContract.check();
        unsContract.traceErrors();

        Contract paymentContract = getApprovedUContract(testSpace);

        Parcel payingParcel = ContractsService.createPayingParcel(unsContract.getTransactionPack(), paymentContract, 1, 2000, manufacturePrivateKeys, false);

        Binder nameInfo = testSpace.client.queryNameRecord(simpleContract.getId());
        String name = nameInfo.getString("name", null);
        System.out.println("name info is null: " + (name == null));
        assertNull(name);

        byte[] unsContractBytes = testSpace.client.queryNameContract(unsTestName);
        System.out.println("unsContractBytes: " + unsContractBytes);
        assertEquals(false, Arrays.equals(unsContract.getPackedTransaction(), unsContractBytes));

        testSpace.client.registerParcelWithState(payingParcel.pack(), 8000);
        itemResult = testSpace.client.getState(unsContract.getId());
        System.out.println("Uns : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        nameInfo = testSpace.client.queryNameRecord(simpleContract.getId());
        assertNotNull(nameInfo);
        System.out.println("name info size: " + nameInfo.size());
        System.out.println("Name: " + nameInfo.getString("name", ""));
        System.out.println("Description: " + nameInfo.getString("description", ""));
        System.out.println("URL: " + nameInfo.getString("url", ""));
        assertEquals(unsTestName, ((Binder)nameInfo.getListOrThrow("names").get(0)).getString("name", ""));

        unsContractBytes = testSpace.client.queryNameContract(unsTestName);
        System.out.println("unsContractBytes: " + unsContractBytes);
        assertEquals(true, Arrays.equals(unsContract.getPackedTransaction(), unsContractBytes));

        // check uns contract with address record
        unsTestName = "testAddressContractName" + Instant.now().getEpochSecond();
        PrivateKey randomPrivKey = new PrivateKey(2048);

        UnsContract unsContract2 = ContractsService.createUnsContractForRegisterKeyName(manufacturePrivateKeys,
                manufacturePublicKeys, nodeInfoProvider, unsTestName, "test address name", "http://test.com", randomPrivKey.getPublicKey());
        unsContract2.getName(unsTestName).setUnsReducedName(unsTestName);
        unsContract2.addSignerKey(authorizedNameServiceKey);
        unsContract2.addSignerKey(randomPrivKey);
        unsContract2.seal();
        unsContract2.check();
        unsContract2.traceErrors();

        paymentContract = getApprovedUContract(testSpace);

        payingParcel = ContractsService.createPayingParcel(unsContract2.getTransactionPack(), paymentContract, 1, 2000, manufacturePrivateKeys, false);

        KeyAddress keyAddr = new KeyAddress(randomPrivKey.getPublicKey(), 0, true);
        nameInfo = testSpace.client.queryNameRecord(keyAddr.toString());
        name = nameInfo.getString("name", null);
        System.out.println("name info is null: " + (name == null));
        assertNull(name);

        unsContractBytes = testSpace.client.queryNameContract(unsTestName);
        System.out.println("unsContractBytes: " + unsContractBytes);
        assertEquals(false, Arrays.equals(unsContract2.getPackedTransaction(), unsContractBytes));

        testSpace.client.registerParcelWithState(payingParcel.pack(), 8000);
        itemResult = testSpace.client.getState(unsContract2.getId());
        System.out.println("Uns : " + itemResult);
        assertEquals(ItemState.APPROVED, itemResult.state);

        nameInfo = testSpace.client.queryNameRecord(keyAddr.toString());
        assertNotNull(nameInfo);
        System.out.println("name info size: " + nameInfo.size());
        System.out.println("Name: " + nameInfo.getString("name", ""));
        System.out.println("Description: " + nameInfo.getString("description", ""));
        System.out.println("URL: " + nameInfo.getString("url", ""));
        assertEquals(unsTestName, ((Binder)nameInfo.getListOrThrow("names").get(0)).getString("name", ""));

        unsContractBytes = testSpace.client.queryNameContract(unsTestName);
        System.out.println("unsContractBytes: " + unsContractBytes);
        assertEquals(true, Arrays.equals(unsContract2.getPackedTransaction(), unsContractBytes));

        testSpace.nodes.forEach(x -> x.shutdown());

    }

    @Test
    public void checkUnsNodeMissedRevocation() throws Exception {


        PrivateKey randomPrivKey1 = new PrivateKey(2048);
        PrivateKey randomPrivKey2 = new PrivateKey(2048);
        PrivateKey randomPrivKey3 = new PrivateKey(2048);
        PrivateKey randomPrivKey4 = new PrivateKey(2048);


        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));

        TestSpace testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        testSpace.nodes.forEach(m -> m.config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress()));

        String name = "test" + Instant.now().getEpochSecond();


        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.addSignerKey(authorizedNameServiceKey);

        uns.addName(name, name, "test description");
        uns.addKey(randomPrivKey1.getPublicKey());

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey1);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();


        UnsContract uns2 = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns2.addSignerKey(authorizedNameServiceKey);

        uns2.addName(name, name, "test description");
        uns2.addKey(randomPrivKey2.getPublicKey());

        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();
        uns2.addSignatureToSeal(randomPrivKey2);
        uns2.addSignatureToSeal(TestKeys.privateKey(8));
        uns2.check();
        uns2.traceErrors();

        UnsContract uns3 = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns3.addSignerKey(authorizedNameServiceKey);

        uns3.addName(name, name, "test description");
        uns3.addKey(randomPrivKey3.getPublicKey());

        uns3.setNodeInfoProvider(nodeInfoProvider);
        uns3.seal();
        uns3.addSignatureToSeal(randomPrivKey3);
        uns3.addSignatureToSeal(TestKeys.privateKey(8));
        uns3.check();
        uns3.traceErrors();

        //REGISTER UNS1
        Contract paymentContract = getApprovedUContract(testSpace);


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameEntries(name).size(), 1);


        //REVOKE UNS1
        Contract revokingContract = new Contract(manufacturePrivateKeys.iterator().next());
        revokingContract.addRevokingItems(uns);
        revokingContract.seal();

        paymentContract = getApprovedUContract(testSpace);
        Parcel parcel = ContractsService.createParcel(revokingContract.getTransactionPack(), paymentContract, 1, manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ItemResult ir = testSpace.node.node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(uns.getId(), 8000).state);

        assertNull(testSpace.node.node.getLedger().getNameRecord(name));

        //REGISTER UNS2
        paymentContract = getApprovedUContract(testSpace);
        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns2.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameEntries(name).size(), 1);

        //SHUTDOWN LAST NODE
        testSpace.nodes.remove(testSpace.nodes.size() - 1).shutdown();
        Thread.sleep(4000);

        //REVOKE UNS2
        revokingContract = new Contract(manufacturePrivateKeys.iterator().next());
        revokingContract.addRevokingItems(uns2);
        revokingContract.seal();

        paymentContract = getApprovedUContract(testSpace);
        parcel = ContractsService.createParcel(revokingContract.getTransactionPack(), paymentContract, 1, manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ir = testSpace.node.node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(uns2.getId(), 8000).state);


        assertNull(testSpace.node.node.getLedger().getNameRecord(name));
        //RECREATE NODES
        testSpace.nodes.forEach(m -> m.shutdown());
        Thread.sleep(4000);
        testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());
        testSpace.nodes.forEach(m -> m.config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress()));

        assertNull(testSpace.node.node.getLedger().getNameRecord(name));
        //LAST NODE MISSED UNS2 REVOKE
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name));

        //REGISTER UNS3
        paymentContract = getApprovedUContract(testSpace);

        payingParcel = ContractsService.createPayingParcel(uns3.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns3.getNew().get(0).getId(), 8000).state);

        NNameRecord nrm = testSpace.node.node.getLedger().getNameRecord(name);
        NNameRecord nrmLast = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name);

        List<NNameRecordEntry> nrmEntries = testSpace.node.node.getLedger().getNameEntries(name);
        List<NNameRecordEntry> nrmEntriesLast = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameEntries(name);

        assertEquals(nrmEntries.size(), 1);
        assertEquals(nrmEntriesLast.size(), 1);
        assertNotEquals(nrmEntries.iterator().next().getShortAddress(), nrmEntriesLast.iterator().next().getShortAddress());
        assertNotEquals(nrmEntries.iterator().next().getLongAddress(), nrmEntriesLast.iterator().next().getLongAddress());

        Thread.sleep(4000);

        nrmLast = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name);
        nrmEntriesLast = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameEntries(name);

        assertEquals(nrmEntries.size(), 1);
        assertEquals(nrmEntriesLast.size(), 1);
        assertEquals(nrmEntries.iterator().next().getShortAddress(), nrmEntriesLast.iterator().next().getShortAddress());
        assertEquals(nrmEntries.iterator().next().getLongAddress(), nrmEntriesLast.iterator().next().getLongAddress());

        testSpace.nodes.forEach(m -> m.shutdown());

    }


    @Test
    public void checkUnsNodeMissedRevision() throws Exception {


        PrivateKey randomPrivKey1 = new PrivateKey(2048);
        PrivateKey randomPrivKey2 = new PrivateKey(2048);
        PrivateKey randomPrivKey3 = new PrivateKey(2048);
        PrivateKey randomPrivKey4 = new PrivateKey(2048);


        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));

        TestSpace testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        testSpace.nodes.forEach(m -> m.config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress()));

        String name = "test" + Instant.now().getEpochSecond();


        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.addSignerKey(authorizedNameServiceKey);



        uns.addName(name,name,"test description");
        uns.addKey(randomPrivKey1.getPublicKey());

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey1);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();


        UnsContract uns2 = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns2.addSignerKey(authorizedNameServiceKey);

        uns2.addName(name,name,"test description");
        uns2.addKey(randomPrivKey2.getPublicKey());


        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();
        uns2.addSignatureToSeal(randomPrivKey2);
        uns2.addSignatureToSeal(TestKeys.privateKey(8));
        uns2.check();
        uns2.traceErrors();

        UnsContract uns3 = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns3.addSignerKey(authorizedNameServiceKey);

        uns3.addName(name,name,"test description");
        uns3.addKey(randomPrivKey3.getPublicKey());


        uns3.setNodeInfoProvider(nodeInfoProvider);
        uns3.seal();
        uns3.addSignatureToSeal(randomPrivKey3);
        uns3.addSignatureToSeal(TestKeys.privateKey(8));
        uns3.check();
        uns3.traceErrors();

        //REGISTER UNS1
        Contract paymentContract = getApprovedUContract(testSpace);


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameEntries(name).size(), 1);


        //REVOKE UNS1
        Contract revokingContract = new Contract(manufacturePrivateKeys.iterator().next());
        revokingContract.addRevokingItems(uns);
        revokingContract.seal();

        paymentContract = getApprovedUContract(testSpace);
        Parcel parcel = ContractsService.createParcel(revokingContract.getTransactionPack(), paymentContract, 1, manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ItemResult ir = testSpace.node.node.waitItem(parcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(uns.getId(), 8000).state);

        assertNull(testSpace.node.node.getLedger().getNameRecord(name));

        //REGISTER UNS2
        paymentContract = getApprovedUContract(testSpace);
        payingParcel = ContractsService.createPayingParcel(uns2.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns2.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameEntries(name).size(), 1);

        //SHUTDOWN LAST NODE
        testSpace.nodes.remove(testSpace.nodes.size() - 1).shutdown();
        Thread.sleep(4000);

        //UPDATE UNS2

        Set<PrivateKey> keys = new HashSet<>();
        keys.add(TestKeys.privateKey(2));
        keys.add(randomPrivKey4);
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(authorizedNameServiceKey);

        uns2 = (UnsContract) uns2.createRevision(keys);
        uns2.removeName(name);
        uns2.addName(name + "2",name + "2", "test description");
        uns2.addKey(randomPrivKey4.getPublicKey());


        uns2.setNodeInfoProvider(nodeInfoProvider);
        uns2.seal();

        parcel = ContractsService.createParcel(uns2, getApprovedUContract(testSpace), 1, manufacturePrivateKeys);
        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ir = testSpace.node.node.waitItem(uns2.getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);

        assertNull(testSpace.node.node.getLedger().getNameRecord(name));

        //RECREATE NODES
        testSpace.nodes.forEach(m -> m.shutdown());
        Thread.sleep(4000);
        testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());
        testSpace.nodes.forEach(m -> m.config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress()));

        assertNull(testSpace.node.node.getLedger().getNameRecord(name));
        //LAST NODE MISSED UNS2 REVISION
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name));

        //REGISTER UNS3
        paymentContract = getApprovedUContract(testSpace);

        payingParcel = ContractsService.createPayingParcel(uns3.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        ir = testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns3.getNew().get(0).getId(), 8000).state);

        NNameRecord nrm = testSpace.node.node.getLedger().getNameRecord(name);
        NNameRecord nrmLast = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name);
        List<NNameRecordEntry> nrmEntries = testSpace.node.node.getLedger().getNameEntries(name);
        List<NNameRecordEntry> nrmEntriesLast = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameEntries(name);


        assertEquals(nrmEntries.size(), 1);
        assertEquals(nrmEntriesLast.size(), 1);
        assertNotEquals(nrmEntries.iterator().next().getShortAddress(), nrmEntriesLast.iterator().next().getShortAddress());
        assertNotEquals(nrmEntries.iterator().next().getLongAddress(), nrmEntriesLast.iterator().next().getLongAddress());

        Thread.sleep(4000);

        nrmLast = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name);
        nrmEntriesLast = testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameEntries(name);

        assertEquals(nrmEntries.size(), 1);
        assertEquals(nrmEntriesLast.size(), 1);
        assertEquals(nrmEntries.iterator().next().getShortAddress(), nrmEntriesLast.iterator().next().getShortAddress());
        assertEquals(nrmEntries.iterator().next().getLongAddress(), nrmEntriesLast.iterator().next().getLongAddress());

        testSpace.nodes.forEach(m -> m.shutdown());

    }


    @Test
    public void checkUnsNodeMissedSelfRevision() throws Exception {


        PrivateKey randomPrivKey1 = new PrivateKey(2048);
        PrivateKey randomPrivKey2 = new PrivateKey(2048);
        PrivateKey randomPrivKey3 = new PrivateKey(2048);


        Set<PrivateKey> manufacturePrivateKeys = new HashSet<>();
        manufacturePrivateKeys.add(new PrivateKey(Do.read(ROOT_PATH + "_xer0yfe2nn1xthc.private.unikey")));

        TestSpace testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());

        PrivateKey authorizedNameServiceKey = TestKeys.privateKey(3);
        testSpace.nodes.forEach(m -> m.config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress()));

        String name = "test" + Instant.now().getEpochSecond();
        String name2 = "test2" + Instant.now().getEpochSecond();


        UnsContract uns = new UnsContract(manufacturePrivateKeys.iterator().next());
        uns.addSignerKey(authorizedNameServiceKey);

        uns.addName(name,name,"test description");
        uns.addKey(randomPrivKey1.getPublicKey());

        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();
        uns.addSignatureToSeal(randomPrivKey1);
        uns.addSignatureToSeal(TestKeys.privateKey(8));
        uns.check();
        uns.traceErrors();


        //REGISTER UNS1
        Contract paymentContract = getApprovedUContract(testSpace);


        Parcel payingParcel = ContractsService.createPayingParcel(uns.getTransactionPack(), paymentContract, 1, nodeInfoProvider.getMinPayment("UNS1"), manufacturePrivateKeys, false);

        testSpace.node.node.registerParcel(payingParcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = payingParcel.getPayloadContract().getNew().get(0);
        }
        // wait parcel
        testSpace.node.node.waitParcel(payingParcel.getId(), 8000);
        // check payment and payload contracts
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(payingParcel.getPayload().getContract().getId(), 8000).state);
        assertEquals(ItemState.REVOKED, testSpace.node.node.waitItem(payingParcel.getPayment().getContract().getId(), 8000).state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(uns.getNew().get(0).getId(), 8000).state);

        assertEquals(testSpace.node.node.getLedger().getNameEntries(name).size(), 1);


        //SHUTDOWN LAST NODE
        testSpace.nodes.remove(testSpace.nodes.size() - 1).shutdown();
        Thread.sleep(4000);

        //UPDATE UNS

        Set<PrivateKey> keys = new HashSet<>();
        keys.add(TestKeys.privateKey(2));
        keys.add(randomPrivKey2);
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(authorizedNameServiceKey);

        uns = (UnsContract) uns.createRevision(keys);
        uns.removeName(name);
        uns.removeKey(randomPrivKey1.getPublicKey());
        uns.addName(name2,name2,"test description");
        uns.addKey(randomPrivKey2.getPublicKey());


        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();

        Parcel parcel = ContractsService.createParcel(uns, getApprovedUContract(testSpace), 1, manufacturePrivateKeys);
        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ItemResult ir = testSpace.node.node.waitItem(uns.getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);

        assertNull(testSpace.node.node.getLedger().getNameRecord(name));

        //RECREATE NODES
        testSpace.nodes.forEach(m -> m.shutdown());
        Thread.sleep(4000);
        testSpace = prepareTestSpace(manufacturePrivateKeys.iterator().next());
        testSpace.nodes.forEach(m -> m.config.setAuthorizedNameServiceCenterAddress(authorizedNameServiceKey.getPublicKey().getLongAddress()));

        assertNull(testSpace.node.node.getLedger().getNameRecord(name));
        assertNotNull(testSpace.node.node.getLedger().getNameRecord(name2));
        //LAST NODE MISSED UNS REVISION
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name));
        assertNull(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name2));

        //REGISTER UNS


        keys = new HashSet<>();
        keys.add(TestKeys.privateKey(2));
        keys.add(randomPrivKey3);
        keys.add(manufacturePrivateKeys.iterator().next());
        keys.add(authorizedNameServiceKey);

        uns = (UnsContract) uns.createRevision(keys);
        uns.removeName(name2);
        uns.removeKey(randomPrivKey2.getPublicKey());
        uns.addName(name,name,"test description");
        uns.addKey(randomPrivKey3.getPublicKey());


        uns.setNodeInfoProvider(nodeInfoProvider);
        uns.seal();

        parcel = ContractsService.createParcel(uns, getApprovedUContract(testSpace), 1, manufacturePrivateKeys);
        testSpace.node.node.registerParcel(parcel);
        synchronized (testSpace.uContractLock) {
            testSpace.uContract = parcel.getPaymentContract();
        }
        // wait parcel
        testSpace.node.node.waitParcel(parcel.getId(), 8000);

        ir = testSpace.node.node.waitItem(uns.getId(), 8000);
        assertEquals(ItemState.APPROVED, ir.state);
        assertEquals(ItemState.APPROVED, testSpace.node.node.waitItem(parcel.getPayment().getContract().getId(), 8000).state);


        KeyAddress long1 = randomPrivKey1.getPublicKey().getLongAddress();
        KeyAddress long3 = randomPrivKey3.getPublicKey().getLongAddress();


        assertNull(testSpace.node.node.getLedger().getNameRecord(name2));
        assertNotNull(testSpace.node.node.getLedger().getNameRecord(name));
        assertEquals(testSpace.node.node.getLedger().getNameEntries(name).iterator().next().getLongAddress(), long3.toString());

        //LAST NODE MISSED UNS REVISION
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name));
        assertNull(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name2));
        assertEquals(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameEntries(name).iterator().next().getLongAddress(), long1.toString());

        Thread.sleep(4000);
        assertNotNull(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name));
        assertNull(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameRecord(name2));
        assertEquals(testSpace.nodes.get(testSpace.nodes.size() - 1).node.getLedger().getNameEntries(name).iterator().next().getLongAddress(), long3.toString());


        testSpace.nodes.forEach(m -> m.shutdown());

    }

    @Test
    public void environmentSerializationTest() throws Exception {
        UnsName unsName = new UnsName();
        unsName.setUnsName("test");
        unsName.setUnsReducedName("test");

        PrivateKey privateKey = new PrivateKey(2048);
        Contract contract = new Contract(privateKey);
        contract.seal();

        NSmartContract smartContract = new NSmartContract(privateKey);
        smartContract.seal();

        UnsRecord record1 = new UnsRecord(contract.getId());
        UnsRecord record2 = new UnsRecord(privateKey.getPublicKey());
        UnsRecord record3 = new UnsRecord(Binder.of("asd",123));

        ArrayList<UnsRecord> list = Do.listOf(record1, record2, record3);

        ZonedDateTime now = ZonedDateTime.now();
        NNameRecord nnr = new NNameRecord(unsName, now);

        Config.forceInit(NMutableEnvironment.class);

        NNameRecord nnr2 = Boss.load(Boss.pack(nnr));

        ArrayList<UnsRecord> list2 = Boss.load(Boss.pack(list));

        for(int i = 0; i < list2.size();i++) {
            assertEquals(list.get(i),list2.get(i));
        }


        assertEquals(nnr2.getName(), unsName.getUnsName());
        assertEquals(nnr2.getNameReduced(), unsName.getUnsReducedName());
        assertEquals(nnr2.getDescription(), unsName.getUnsDescription());
        assertEquals(nnr.expiresAt().toEpochSecond(), nnr2.expiresAt().toEpochSecond());

        NContractSubscription sub = Boss.load(Boss.pack(new NContractSubscription(contract.getOrigin(), true, now)));
        assertTrue(sub.getOrigin().equals(contract.getOrigin()));
        assertTrue(sub.getHashId().equals(contract.getOrigin()));
        assertTrue(sub.isChainSubscription());
        assertEquals(sub.expiresAt().toEpochSecond(), now.toEpochSecond());

        sub = Boss.load(Boss.pack(new NContractSubscription(contract.getId(), false, now)));
        assertTrue(sub.getContractId().equals(contract.getId()));
        assertTrue(sub.getHashId().equals(contract.getId()));
        assertFalse(sub.isChainSubscription());
        assertEquals(sub.expiresAt().toEpochSecond(), now.toEpochSecond());

        NContractStorage storage = Boss.load(Boss.pack(new NContractStorage(contract.getPackedTransaction(), now)));
        assertTrue(storage.getContract().getId().equals(contract.getId()));
        assertEquals(storage.expiresAt().toEpochSecond(), now.toEpochSecond());

        NFollowerService followerService = Boss.load(Boss.pack(new NFollowerService(null, now, now, 1, 14.87, 3)));
        assertEquals(followerService.expiresAt().toEpochSecond(), now.toEpochSecond());
        assertEquals(followerService.mutedAt().toEpochSecond(), now.toEpochSecond());
        assertTrue(followerService.getCallbacksSpent() == 14.87);
        assertTrue(followerService.getStartedCallbacks() == 3);

        Binder kvStore = new Binder();
        kvStore.put("test", "test1");
        NImmutableEnvironment environment = new NImmutableEnvironment(
                smartContract, kvStore, Do.listOf(sub), Do.listOf(storage), Do.listOf(nnr2),Do.listOf(), followerService, null);

        environment = Boss.load(Boss.pack(environment));
        assertEquals(environment.get("test", null), "test1");
    }
    
}
