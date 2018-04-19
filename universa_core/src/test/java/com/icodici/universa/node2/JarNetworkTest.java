package com.icodici.universa.node2;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ContractsService;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.StateRecord;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.network.Client;
import net.sergeych.utils.Base64;
import net.sergeych.utils.Bytes;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class JarNetworkTest {

    private static Client whiteClient = null;
    private static Client normalClient = null;
    private static Contract paymentContract = null;
    private static PrivateKey paymentContractPrivKey = null;



    @BeforeClass
    public static void beforeClass() throws Exception {
        String nodeUrl = "http://node-1-pro.universa.io:8080";
        PrivateKey clientKey = TestKeys.privateKey(0);
        whiteClient = new Client(nodeUrl, clientKey, null, false);
        normalClient = new Client(nodeUrl, new PrivateKey(2048), null, false);
        paymentContract = Contract.fromPackedTransaction(Base64.decodeLines(uno_flint004_rev1_bin_b64));
        paymentContractPrivKey = new PrivateKey(Base64.decodeLines(uno_flint004_privKey_b64));
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




    private static final String uno_flint004_rev1_bin_b64 = "JyNkYXRhxA0GHxtuZXcGQ3Jldm9raW5nHUNjb250cmFjdCdLYXBpX2xldmVsGDNfX3R5cGWDVW5pdmVyc2FDb250cmFjdFNkZWZpbml0aW9uLyNkYXRhF1Npc3N1ZXJOYW1luxdVbml2ZXJzYSBSZXNlcnZlIFN5c3RlbSNuYW1ls3RyYW5zYWN0aW9uIHVuaXRzIHBhY2tTcmVmZXJlbmNlcx1bcGVybWlzc2lvbnMnM0dzX2xYaTeFo2RlY3JlbWVudF9wZXJtaXNzaW9uS21pbl92YWx1ZQAjcm9sZR9bdGFyZ2V0X25hbWUrb3duZXJFQ1JvbGVMaW5rhTNvd25lcjJDbWF4X3N0ZXAKU2ZpZWxkX25hbWWLdHJhbnNhY3Rpb25fdW5pdHNFu0Bjb20uaWNvZGljaS51bml2ZXJzYS5jb250cmFjdC5wZXJtaXNzaW9ucy5DaGFuZ2VOdW1iZXJQZXJtaXNzaW9uM1pBOU50MjeFvRe9GAC9Gb0avR8KvSCzdGVzdF90cmFuc2FjdGlvbl91bml0c0W9IjNDYVZuOXIfvRknI2tleXMOFxtrZXkXRWNSU0FQdWJsaWNLZXkzcGFja2VkxAkBHggcAQABxAABxSSWfXW20wGsRn9khVZDtvcCtUqP/scN3oVPU3r0152Fu62pfx9Mjc1cmQnRYSkeZzWA50RYQTU3FlXC5iIN7w+Lm6TGPQxWe+uYGMgKLCbAmyMXPWupvzeB5SEMtylQ5ml12iwFQkamqOiFE2KWMYz/UGhW87/ELPckmpoanZUa8OGCACUfFGALAZV0G+rQ/8xiW3hkGHmOFP0kejZaCZGPO/XGVay+2q0V2cw6CHar+D9F9FomXYA4bAInlY3zOLHdG8ddUTzhHQWOKmzoF9eIW67U9rd9qIR04U9ls9wGLQchqlG/kxHHfR4Js86mwYNgUKW49fQRppig+SsrjUVLS2V5UmVjb3JkRVNTaW1wbGVSb2xlhTNpc3N1ZXI7YW5vbklkcx1Fq0NoYW5nZU93bmVyUGVybWlzc2lvboVjY2hhbmdlX293bmVyM2JNc09ZMi+FvRe9GAC9GR+9G70zRb0dhTtpc3N1ZXIyvSC9JUW9IlNjcmVhdGVkX2F0eQ8JIVWFvTMnvSkOF70sF0W9Lr0vxAkBHggcAQABxAABxSSWfXW20wGsRn9khVZDtvcCtUqP/scN3oVPU3r0152Fu62pfx9Mjc1cmQnRYSkeZzWA50RYQTU3FlXC5iIN7w+Lm6TGPQxWe+uYGMgKLCbAmyMXPWupvzeB5SEMtylQ5ml12iwFQkamqOiFE2KWMYz/UGhW87/ELPckmpoanZUa8OGCACUfFGALAZV0G+rQ/8xiW3hkGHmOFP0kejZaCZGPO/XGVay+2q0V2cw6CHar+D9F9FomXYA4bAInlY3zOLHdG8ddUTzhHQWOKmzoF9eIW67U9rd9qIR04U9ls9wGLQchqlG/kxHHfR4Js86mwYNgUKW49fQRppig+SsrjUW9MUW9MoW9M700HStzdGF0ZU+9HCe9KQ4XvSwXRb0uvS/ECQEeCBwBAAHEAAGrOi7YKiKv4jCJhXMUN7x7120EL0Q179+YC3kM6ojRavDNmnnGyHCa3HEh6TZim2/bdWsCJeU3k7dlCt09E6421ApyTSt+WDe7xFySu/rVQoVGuXOyw97Oiaq6/NfbzUismNMTrDgWYtGXCGLP4RrwG7wulb7fgwevuuNgTXtn4p01mlrWfGaPR8E+kS9XOXLPDx3OUXNYByYHX5GKOvdFNfOoFYlsf/xEM4Eqa1GsTixEcJ7+OZCn2loVEMxna1DxtD7rorx8tSTWfp6h4qwcmcgXY1RKvsZj0rrf4PwqUhYwkp5cfbE9dqHv525aoHO5k3EdDeRuqodcZOh2QEu9Rb0xRb0yhb0cvTQdM3BhcmVudAVTZXhwaXJlc19hdHkPcCVkhWUXvSEgvSXAECdLYnJhbmNoX2lkBTNvcmlnaW4FvTt5DwkhVYVTY3JlYXRlZF9ieR+9G70zRb0dhTtjcmVhdG9yQ3JldmlzaW9uCCN0eXBlU3VuaWNhcHN1bGU7dmVyc2lvbhhTc2lnbmF0dXJlcw7EkgEXI3NpZ27EAAE3KMYISMZ4FRmlkEPV4VmkSKDom2VNBEiClh9mNwnzF45IHStnS7LGy8i9ZMY5V6gMdbG0hvgrKxVZPTMYD2Yp9De7LKE+E3MlXg2GAY/YaXD5lDeYC+cECCbERlFOOhzg4lWzNnu7Qn+K2SVCvJ61K/dGHlO33vt9GueKO43rwgPg2TxBuaXca0z+dRVZX57l0A9WuwpND9uBx0enYtxazfjMHFpPyWPiCqmFjpRWBQ6hYVevypqKy9RqrrisnM9Cbrh1jU+ERd2wBFAlN4byF7FKRF5DJRt+CX9bdk7p6gkzG6A/YqVzf+gru+JrRsjgJJd/1Rw8u+rI60hkUHynI2V4dHO8gh8zc2hhNTEyvEBI0fCkiSs+6VfbX5k4qN/DBFfCQWiaygnGC3A6ikR/8aJ6GIQGwnG5wjr2CJd9wgurOfKMfmoVZi86sgAzKowXU2NyZWF0ZWRfYXR5DwkhVYUba2V5vCEHucc+TxvM9el1aV8pt25c2FqaAvPHuKqggaGHbxLHTJ0=";
    private static final String uno_flint004_privKey_b64 = "JgAcAQABvIDayzI6N7cLoXiAf826OwDmGbU/RYl0MrhCaRx1dExXJYAMOEnSbgIP5+VkCjbuJqLL8tAIGaatZIHmFBXDg3Ub75Y82spLZ+sCblnpO+lY3f4AXN9unXCiUa44W9ysYEOTiQYxCROohis5A33C/wVt+aMSq2TGMaQuIcTJKkuSnbyAyFhDk7PrnjW6WuFm615F/bIeNZssuUhmBs9zus/05mIIlzRX0tRv1xVNpsUXyKJ8I5MMIxRyIkvD2IOdjJ2CxGO36C2KIze6lZ6r1+hYWaUT10aH5ToxkRS8jZhPTrOshZ0n2kGrDlPLxU8hf3JHHPBMMNEvzmbn0pM8oSaiQ6E=";

}
