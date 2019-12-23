package com.icodici.universa.node;

import com.icodici.crypto.PrivateKey;
import com.icodici.db.PooledDb;
import com.icodici.universa.HashId;
import com.icodici.universa.TestCase;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.TestKeys;
import com.icodici.universa.node2.ItemLock;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.NodeStats;
import com.icodici.universa.node2.VoteInfo;
import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import net.sergeych.utils.Ut;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static org.junit.Assert.*;

public class PostgresLedgerTest extends TestCase {
    public static final String CONNECTION_STRING = "jdbc:postgresql://localhost:5432/universa_node";
    private PostgresLedger ledger;

    @Before
    public void setUp() throws Exception {
//        new File("testledger").delete();
//        Class.forName("org.postgresql.Driver");
        ledger = new PostgresLedger(CONNECTION_STRING);
        ledger.enableCache(false);
    }

    @Test
    public void create() throws Exception {
//        System.out.println("" + ledger.getDb().getIntParam("version"));
        HashId id = HashId.createRandom();
        assertNull(ledger.getRecord(id));
        StateRecord r = ledger.findOrCreate(id);
        System.out.println(r);
        System.out.println(ledger.countRecords());
    }

    @Test
    public void checkNegativeBytesInId() throws Exception {
        HashId id = HashId.withDigest(Do.randomNegativeBytes(64));
        StateRecord r1 = ledger.findOrCreate(id);
        r1.setState(ItemState.DECLINED);
        r1.save();
        StateRecord r2 = ledger.getRecord(id);
        assertNotNull(r2);
        assertNotSame(r1, r2);
        assertEquals(r1.getState(), r2.getState());

        ledger.enableCache(true);
        StateRecord r3 = ledger.getRecord(id);
        StateRecord r4 = ledger.getRecord(id);
        assertEquals(r3.toString(), r4.toString());
        // why?
        assertSame(r3, r4);
    }

    //    @Test
    public void ledgerBenchmark() throws Exception {
        ExecutorService es = Executors.newCachedThreadPool();
        List<Future<?>> ff = new ArrayList<>();
        int nMax = 32;
        int nIds = 4000;
        long t = StopWatch.measure(true, () -> {
            for (int n = 0; n < nMax; n++) {
                final int x = n;
                ff.add(es.submit(() -> {
                           HashId ids[] = new HashId[nIds];
                           for (int i = 0; i < ids.length; i++) ids[i] = HashId.createRandom();
                           System.out.println(x);
                           StopWatch.measure(true, () -> {
                               for (HashId i : ids) {
                                   try {
                                       ledger.findOrCreate(i);
                                   } catch (Exception e) {
                                       e.printStackTrace();
                                       fail(e.getMessage());
                                   }
                               }
                           });
                           System.out.println("end-" + x);
                           return null;
                       })
                );
            }
            ff.forEach(f -> {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            });
            System.out.println("total");
        });
        System.out.println("TPS: " + (nMax * nIds * 1000 / t));
        System.out.println("" + ledger.getDb().queryOne("SELECT count(*) from ledger"));
    }

    @Test
    public void createOutputLockRecord() throws Exception {
        ledger.enableCache(true);
        StateRecord owner = ledger.findOrCreate(HashId.createRandom());
        StateRecord other = ledger.findOrCreate(HashId.createRandom());

        HashId id = HashId.createRandom();
        StateRecord r1 = owner.createOutputLockRecord(id);
        r1.reload();
        assertEquals(id, r1.getId());
        assertEquals(ItemState.LOCKED_FOR_CREATION, r1.getState());
        assertEquals(owner.getRecordId(), r1.getLockedByRecordId());
        StateRecord r2 = owner.createOutputLockRecord(id);
        assertNull(r2);
        assertNull(owner.createOutputLockRecord(other.getId()));
        // And hacked low level operation must fail too
        assertNull(ledger.createOutputLockRecord(owner.getRecordId(), other.getId()));
    }

    @Test
    public void findOrCreateAndGet() throws Exception {
        // Atomic new record creation
        HashId id = HashId.createRandom();
        StateRecord r = ledger.findOrCreate(id);
        assertNotNull(r);
        assertEquals(id, r.getId());
        assertEquals(ItemState.PENDING, r.getState());
        assertAlmostSame(ZonedDateTime.now(), r.getCreatedAt());

        // returning existing record
        StateRecord r1 = ledger.findOrCreate(id);
        assertSameRecords(r, r1);

        StateRecord r2 = ledger.getRecord(id);
        assertSameRecords(r, r2);

        StateRecord r3 = ledger.getRecord(HashId.createRandom());
        assert (r3 == null);
    }


    @Test
    public void saveAndTransaction() throws Exception {
        StateRecord r1 = ledger.findOrCreate(HashId.createRandom());
        StateRecord r2 = ledger.findOrCreate(HashId.createRandom());
        int x = ledger.transaction(() -> {
            r1.setState(ItemState.APPROVED);
            r2.setState(ItemState.DECLINED);
            r1.save();
            r2.save();
            return 5;
        });
        assertEquals(5, x);
        r1.reload();
        StateRecord r3 = ledger.getRecord(r1.getId());
        assertEquals(ItemState.APPROVED, r1.getState());
        assertEquals(ItemState.APPROVED, r3.getState());
        r2.reload();
        assertEquals(ItemState.DECLINED, r2.getState());
        Object y = ledger.transaction(() -> {
            r1.setState(ItemState.REVOKED);
            r2.setState(ItemState.DISCARDED);
            r1.save();
            r2.save();
            throw new Ledger.Rollback();
        });
        assert (y == null);
        r1.reload();
        assertEquals(ItemState.APPROVED, r1.getState());
        r2.reload();
        assertEquals(ItemState.DECLINED, r2.getState());
    }

    @Test
    public void approve() throws Exception {
        StateRecord r1 = ledger.findOrCreate(HashId.createRandom());
        assertFalse(r1.isApproved());
        r1.approve();
        assertEquals(ItemState.APPROVED, r1.getState());
        assert (r1.isApproved());
        r1.reload();
        assert (r1.isApproved());
        assertThrows(IllegalStateException.class, () -> {
            r1.approve();
            return null;
        });
    }

    @Test
    public void lockForRevoking() throws Exception {
        ledger.enableCache(true);
        StateRecord existing = ledger.findOrCreate(HashId.createRandom());
        existing.approve();

        StateRecord existing2 = ledger.findOrCreate(HashId.createRandom());
        existing2.approve();

        StateRecord r = ledger.findOrCreate(HashId.createRandom());
        StateRecord r1 = r.lockToRevoke(existing.getId());

        existing.reload();
        r.reload();

        assertSameRecords(existing, r1);
        assertEquals(ItemState.LOCKED, existing.getState());
        assertEquals(r.getRecordId(), existing.getLockedByRecordId());

        // we lock again the same record it should fail:
        StateRecord r2 = r.lockToRevoke(existing.getId());
        assertNull(r2);
        assertEquals(ItemState.LOCKED, existing.getState());
        assertEquals(r.getRecordId(), existing.getLockedByRecordId());

        StateRecord r3 = r.lockToRevoke(existing2.getId());
        existing2.reload();
        assertSameRecords(existing2, r3);
        assertEquals(ItemState.LOCKED, existing2.getState());
        assertEquals(r.getRecordId(), existing2.getLockedByRecordId());
    }


    @Test
    public void lockForCreationRevoked() throws Exception {
        ledger.enableCache(true);

        StateRecord r = ledger.findOrCreate(HashId.createRandom());
        StateRecord r1 = r.createOutputLockRecord(HashId.createRandom());
        assertEquals(ItemState.LOCKED_FOR_CREATION, r1.getState());
        assertEquals(r.getRecordId(), r1.getLockedByRecordId());
        StateRecord r2 = r.lockToRevoke(r1.getId());
        assertEquals(ItemState.LOCKED_FOR_CREATION_REVOKED, r2.getState());
        r1.reload();
        assertSameRecords(r2, r1);
        r.reload();
        assertEquals(r.getRecordId(), r1.getLockedByRecordId());

    }

    @Test
    public void checkLockOwner() throws Exception {
        ledger.enableCache(true);
        StateRecord existing = ledger.findOrCreate(HashId.createRandom());
        existing.approve();

        StateRecord r = ledger.findOrCreate(HashId.createRandom());
        StateRecord r1 = r.lockToRevoke(existing.getId());

        existing.reload();
        r.reload();

        assertSameRecords(existing, r1);
        assertEquals(ItemState.LOCKED, existing.getState());
        assertEquals(r.getRecordId(), existing.getLockedByRecordId());

        StateRecord currentOwner = ledger.getLockOwnerOf(existing);

        System.out.println("existing: " + existing.getId());
        System.out.println("locker: " + r.getId());
        System.out.println("locked: " + r1.getId());
        System.out.println("currentOwner: " + currentOwner.getId());
        assertSameRecords(r, currentOwner);
    }

    @Test
    public void revoke() throws Exception {
        StateRecord r1 = ledger.findOrCreate(HashId.createRandom());
        assertFalse(r1.isApproved());
        assertTrue(r1.isPending());
        assertFalse(r1.isArchived());
        r1.approve();
        r1.reload();
        assertTrue(r1.isApproved());
        assertFalse(r1.isPending());
        assertFalse(r1.isArchived());
        r1.setState(ItemState.LOCKED);
        r1.revoke();
        assertFalse(r1.isPending());
        assertFalse(r1.isApproved());
        assertTrue(r1.isArchived());
    }

    @Test
    public void destroy() throws Exception {
        StateRecord r1 = ledger.findOrCreate(HashId.createRandom());
        r1.destroy();
        assertNull(ledger.getRecord(r1.getId()));
    }

    @Test
    public void recordExpiration() throws Exception {
        // todo: expired can't be get - it should be dropped by the database
        HashId hashId = HashId.createRandom();
        StateRecord r = ledger.findOrCreate(hashId);
        assertNotNull(r.getExpiresAt());
        assert(r.getExpiresAt().isAfter(ZonedDateTime.now()));
        long recordId = r.getRecordId();

        ZonedDateTime inFuture = ZonedDateTime.now().plusHours(2);
        r.setExpiresAt(inFuture);

        StateRecord r1 = ledger.getRecord(hashId);
        assertNotEquals(r1.getExpiresAt(), inFuture);

        r.save();
        r1 = ledger.getRecord(hashId);
        assertAlmostSame(r.getExpiresAt(), r1.getExpiresAt());

        r.setExpiresAt(ZonedDateTime.now().minusHours(1));
        r.save();

        r1 = ledger.getRecord(hashId);
        assertNull(r1);


    }

//    @Test
    public void saveOneRecordManyTimes() throws Exception {
        HashId hashId = HashId.createRandom();
        StateRecord r = ledger.findOrCreate(hashId);
        StateRecord r1 = ledger.findOrCreate(HashId.createRandom());
        StateRecord r2 = ledger.findOrCreate(HashId.createRandom());
        class TestRunnable implements Runnable {

            @Override
            public void run() {
                ledger.findOrCreate(HashId.createRandom());
                ledger.getRecord(r.getId());
                r.setState(ItemState.APPROVED);
                r.save();
                ledger.findOrCreate(HashId.createRandom());
            }
        }
        class TransactionRunnable implements Runnable {

            @Override
            public void run() {
                ledger.findOrCreate(HashId.createRandom());
                ledger.transaction(() -> {
                    r1.setState(ItemState.REVOKED);
                    r2.setState(ItemState.DISCARDED);
                    r1.save();
                    r2.save();

                    return true;
                });
                ledger.findOrCreate(HashId.createRandom());
            }
        }

        List<Thread> threadsList = new ArrayList<>();
        List<Runnable> runnableList = new ArrayList<>();
        for(int j = 0; j < 700;j++) {

            if(new Random().nextBoolean()) {
                TestRunnable runnableSingle = new TestRunnable();
                runnableList.add(runnableSingle);
                threadsList.add(
                        new Thread(() -> {
                            runnableSingle.run();

                        }));
            } else {
                TransactionRunnable transactionRunnable = new TransactionRunnable();
                runnableList.add(transactionRunnable);
                threadsList.add(
                        new Thread(() -> {
                            transactionRunnable.run();

                        }));
            }
        }

        for (Thread th : threadsList) {
            th.start();
        }
    }
    @Test
    public void moveToTestnet() throws Exception {

        HashId hashId = HashId.createRandom();
        StateRecord r = ledger.findOrCreate(hashId);
        r.save();

        PreparedStatement ps =  ledger.getDb().statement("select count(*) from ledger_testrecords where hash = ?",hashId.getDigest());
        ResultSet rs = ps.executeQuery();
        assertTrue(rs.next());
        assertEquals(rs.getInt(1), 0);

        r.markTestRecord();

        ps =  ledger.getDb().statement("select count(*) from ledger_testrecords where hash = ?",hashId.getDigest());
        rs = ps.executeQuery();
        assertTrue(rs.next());
        assertEquals(rs.getInt(1), 1);

        r.markTestRecord();

        ps =  ledger.getDb().statement("select count(*) from ledger_testrecords where hash = ?",hashId.getDigest());
        rs = ps.executeQuery();
        assertTrue(rs.next());
        assertEquals(rs.getInt(1), 1);


    }

    @Ignore("Stress test")
    @Test(timeout = 30000)
    public void ledgerDeadlock() throws Exception {
        List<Contract> origins = new ArrayList<>();
        List<Contract> newRevisions = new ArrayList<>();
        List<Contract> newContracts = new ArrayList<>();
        PrivateKey myKey = TestKeys.privateKey(4);

        //up this value to more effect
        final int N = 700;
        for(int i = 0; i < N; i++) {
            Contract origin = new Contract(myKey);
            origin.seal();
            origins.add(origin);

            Contract newRevision = origin.createRevision(myKey);

            if(i < N/2) {
                //ACCEPTED
                newRevision.setOwnerKeys(TestKeys.privateKey(1).getPublicKey());
            } else {
                //DECLINED
                //State is equal
            }

            Contract newContract = new Contract(myKey);
            newRevision.addNewItems(newContract);
            newRevision.seal();

            newContracts.add(newContract);
            newRevisions.add(newRevision);

            System.out.println("item# "+ newRevision.getId().toBase64String().substring(0,6));
            int finalI = i;

            StateRecord originRecord = ledger.findOrCreate(origin.getId());
            originRecord.setExpiresAt(origin.getExpiresAt());
            originRecord.setCreatedAt(origin.getCreatedAt());

            StateRecord newRevisionRecord = ledger.findOrCreate(newRevision.getId());
            newRevisionRecord.setExpiresAt(newRevision.getExpiresAt());
            newRevisionRecord.setCreatedAt(newRevision.getCreatedAt());

            StateRecord newContractRecord = ledger.findOrCreate(newContract.getId());
            newContractRecord.setExpiresAt(newContract.getExpiresAt());
            newContractRecord.setCreatedAt(newContract.getCreatedAt());

            if(new Random().nextBoolean()) {
                if(finalI < N/2) {
                    originRecord.setState(ItemState.REVOKED);
                    newContractRecord.setState(ItemState.APPROVED);
                    newRevisionRecord.setState(ItemState.APPROVED);
                } else {
                    originRecord.setState(ItemState.APPROVED);
                    newContractRecord.setState(ItemState.UNDEFINED);
                    newRevisionRecord.setState(ItemState.DECLINED);
                }
            } else {
                originRecord.setState(ItemState.LOCKED);
                originRecord.setLockedByRecordId(newRevisionRecord.getRecordId());
                newContractRecord.setState(ItemState.LOCKED_FOR_CREATION);
                newContractRecord.setLockedByRecordId(newRevisionRecord.getRecordId());
                newRevisionRecord.setState(finalI < N/2 ? ItemState.PENDING_POSITIVE : ItemState.PENDING_NEGATIVE);
            }

            originRecord.save();
            ledger.putItem(originRecord,origin, Instant.now().plusSeconds(3600*24));
            newRevisionRecord.save();
            ledger.putItem(newRevisionRecord,newRevision, Instant.now().plusSeconds(3600*24));
            if(newContractRecord.getState() == ItemState.UNDEFINED) {
                newContractRecord.destroy();
            } else {
                newContractRecord.save();
            }
        }


        Map<HashId,StateRecord> recordsToSanitate = ledger.findUnfinished();
        Map<HashId,StateRecord> finished = new HashMap<>();
        Map<HashId,StateRecord> failed = new HashMap<>();
        ItemLock itemLock = new ItemLock();
        Map<HashId,StateRecord> nullContarcts = new HashMap<>();
        System.out.println(">> " + recordsToSanitate.size());


        class TransactionRunnable implements Runnable {

            StateRecord sr;

            @Override
            public void run() {
                try (PooledDb db = (PooledDb) ledger.getDb()) {
                    db.update("update ledger set state=?, expires_at=?, locked_by_id=? where id=?",
                            ItemState.APPROVED.ordinal(),
                            Ut.unixTime(sr.getExpiresAt()),
                            0,
                            sr.getRecordId()
                    );
                } catch (SQLException se) {
                    se.printStackTrace();
                    throw new Ledger.Failure("StateRecord save failed:" + se);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                    try {
                        ledger.transaction(() -> {

                            try (PooledDb db = (PooledDb) ledger.getDb()) {
                                db.update("update ledger set state=?, expires_at=?, locked_by_id=? where id=?",
                                        ItemState.APPROVED.ordinal(),
                                        Ut.unixTime(sr.getExpiresAt()),
                                        0,
                                        sr.getRecordId()
                                );
                            } catch (SQLException se) {
                                se.printStackTrace();
//                                throw new Ledger.Failure("StateRecord save failed:" + se);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            return null;
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                finished.put(sr.getId(), sr);
            }
        }

        List<Thread> threadsList = new ArrayList<>();
        List<Runnable> runnableList = new ArrayList<>();
        for (StateRecord sr : recordsToSanitate.values()) {

                TransactionRunnable transactionRunnable = new TransactionRunnable();
                runnableList.add(transactionRunnable);
                threadsList.add(
                        new Thread(() -> {
                            transactionRunnable.sr = sr;
                            transactionRunnable.run();

                        }));
        }

        int numStarted = 0;
        for (Thread th : threadsList) {
            numStarted++;
            th.start();
            if(numStarted == 64) {
                numStarted = 0;
                Thread.sleep(500);
            }
        }

        while(finished.size() != recordsToSanitate.size()) {
            System.out.println(">>> " + nullContarcts.size() + " " + finished.size() + " " + recordsToSanitate.size());
            Thread.sleep(500);
        }
        System.out.println(">>>> " + nullContarcts.size());
    }


    @Test
    public void ledgerCleanupTest() throws Exception{
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.seal();
        StateRecord r = ledger.findOrCreate(contract.getId());
        r.setExpiresAt(ZonedDateTime.now().minusSeconds(1));
        r.save();
        ledger.putItem(r,contract,Instant.now().plusSeconds(300));

        HashId hash1 = contract.getId();

        contract = new Contract(TestKeys.privateKey(0));
        contract.seal();
        r = ledger.findOrCreate(contract.getId());
        r.setExpiresAt(ZonedDateTime.now().plusMonths(1));
        r.save();
        ledger.putItem(r,contract,Instant.now().minusSeconds(1));

        HashId hash2 = contract.getId();

        ledger.cleanup(false);


        PreparedStatement st = ledger.getDb().statement("select count(*) from ledger where hash = ?", hash1.getDigest());

        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(rs.getInt(1),0);
        }


        st = ledger.getDb().statement("select count(*) from ledger where hash = ?", hash2.getDigest());

        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(rs.getInt(1),1);
        }

        st = ledger.getDb().statement("select count(*) from items where id in (select id from ledger where hash = ?)", hash2.getDigest());

        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(rs.getInt(1),0);
        }
    }


    @Test
    public void paymentSaveTest() throws Exception {
        try (PooledDb db = (PooledDb) ledger.getDb()) {
            try (PreparedStatement statement = db.statement("delete from payments_summary;")
            ) {
                statement.executeUpdate();
            }
        }

        NodeStats stats = new NodeStats();
        Config config = new Config();
        stats.init(ledger,config);
        ZonedDateTime now  = ZonedDateTime.now();
        ZonedDateTime dateTime = now.minusDays(now.getDayOfMonth()-1).minusMonths(1);
        while (dateTime.isBefore(ZonedDateTime.now().plusSeconds(1))) {
            ledger.savePayment(100,dateTime);
            ledger.savePayment(100,dateTime);
            dateTime = dateTime.plusDays(1);
        }

        stats.collect(ledger,config);


        //assertEquals(stats.todayPaidAmount,200);
        //assertEquals(stats.yesterdayPaidAmount,200);
        //assertEquals(stats.thisMonthPaidAmount,200*now.getDayOfMonth());
        //assertEquals(stats.lastMonthPaidAmount,200*now.minusMonths(1).getMonth().length(now.getYear() % 4 == 0));

    }

    @Test
    public void clearExpiredEnvironment() throws Exception {
        HashId hashId = HashId.createRandom();
        long id = ledger.getDb().queryOne("INSERT INTO environments (ncontract_type,ncontract_hash_id,kv_storage,transaction_pack)" +
                "VALUES(?,?,?,?) RETURNING id;", "aaa", hashId.getDigest(), hashId.getDigest(), hashId.getDigest());
        ledger.getDb().update("INSERT INTO name_storage (name_reduced,name_full,expires_at,environment_id)" +
                "VALUES(?,?,?,?);", hashId.toBase64String(), hashId.toBase64String(), 0, id);
        ledger.getDb().update("INSERT INTO name_entry (short_addr,long_addr,origin,environment_id,kvdata)" +
                "VALUES(?,?,?,?,cast(? as json));", "ccc", "ccc", hashId.getDigest(), id, "{}");
        assertEquals(1, (long)ledger.getDb().queryOne("SELECT COUNT(*) FROM environments WHERE id=?", id));
        assertEquals(1, (long)ledger.getDb().queryOne("SELECT COUNT(*) FROM name_storage WHERE environment_id=?", id));
        assertEquals(1, (long)ledger.getDb().queryOne("SELECT COUNT(*) FROM name_entry WHERE environment_id=?", id));
        ledger.clearExpiredNameRecords(Duration.ofDays(30));
        assertEquals(0, (long)ledger.getDb().queryOne("SELECT COUNT(*) FROM environments WHERE id=?", id));
        assertEquals(0, (long)ledger.getDb().queryOne("SELECT COUNT(*) FROM name_storage WHERE environment_id=?", id));
        assertEquals(0, (long)ledger.getDb().queryOne("SELECT COUNT(*) FROM name_entry WHERE environment_id=?", id));
    }

/*
    @Test
    public void addContractToStorage() throws Exception {

        HashId originId = HashId.createRandom();
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.seal();
        ledger.addContractToStorage(contract.getId(), contract.getPackedTransaction(), 50, originId);

        PreparedStatement st = ledger.getDb().statement("select count(*) from contract_storage where hash_id = ?", contract.getId().getDigest());
        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

    }


    @Test
    public void addAndGetEnvironment() throws Exception {

        long now = StateRecord.unixTime(ZonedDateTime.now());
        Binder someBinder = Binder.of("balance", "12.345", "expires_at", now);
        HashId contractId = HashId.createRandom();
        Contract someContract = new Contract(TestKeys.privateKey(0));
        someContract.seal();

        long id1 = ledger.updateEnvironment("SLOT0test", contractId, Boss.pack(someBinder), someContract.getPackedTransaction());
        System.out.println("id1: " + id1);

        byte[] readedBytes = ledger.getEnvironmentFromStorage(contractId);
        assertNotEquals(null, readedBytes);
        Binder readedBinder = Boss.unpack(readedBytes);

        assertEquals(someBinder.getStringOrThrow("balance"), readedBinder.getStringOrThrow("balance"));
        assertEquals(someBinder.getLongOrThrow("expires_at"), readedBinder.getLongOrThrow("expires_at"));

        Binder updatedBinder = new Binder(readedBinder);
        updatedBinder.set("balance", "23.456");
        updatedBinder.set("expires_at", 33);

        long id2 = ledger.updateEnvironment("SLOT0test", contractId, Boss.pack(updatedBinder), someContract.getPackedTransaction());
        System.out.println("id2: " + id2);

        byte[] readedBytes2 = ledger.getEnvironmentFromStorage(contractId);
        assertNotEquals(null, readedBytes2);
        Binder readedBinder2 = Boss.unpack(readedBytes2);

        assertEquals(updatedBinder.getStringOrThrow("balance"), readedBinder2.getStringOrThrow("balance"));
        assertEquals(updatedBinder.getLongOrThrow("expires_at"), readedBinder2.getLongOrThrow("expires_at"));
        assertEquals(id1, id2);
    }


    @Test
    public void clearExpiredStorage() throws Exception {

        HashId originId = HashId.createRandom();
        Contract contract = new Contract(TestKeys.privateKey(0));
        contract.seal();
        ledger.addContractToStorage(contract.getId(), contract.getPackedTransaction(), 5, originId);

        PreparedStatement st = ledger.getDb().statement("select count(*) from contract_storage where hash_id = ?", contract.getId().getDigest());
        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

        ledger.clearExpiredStorageSubscriptions();
        ledger.clearExpiredStorageContracts();

        st = ledger.getDb().statement("select count(*) from contract_storage where hash_id = ?", contract.getId().getDigest());
        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }

        Thread.sleep(10000);
        ledger.clearExpiredStorageSubscriptions();
        ledger.clearExpiredStorageContracts();

        st = ledger.getDb().statement("select count(*) from contract_storage where hash_id = ?", contract.getId().getDigest());
        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }

    }


    @Test
    public void addAndGetNameRecord() throws Exception {
        long now = StateRecord.unixTime(ZonedDateTime.now());
        Binder someBinder = Binder.of("balance", "12.345", "expires_at", now);
        HashId contractId = HashId.createRandom();
        Contract someContract = new Contract(TestKeys.privateKey(0));
        someContract.seal();

        long id1 = ledger.updateEnvironment("UNS0test", contractId, Boss.pack(someBinder), someContract.getPackedTransaction());
        System.out.println("id1: " + id1);

        NameRecordModel nameRecordModel = new NameRecordModel();
        nameRecordModel.environment_id = id1;
        nameRecordModel.name_full = "test_name";
        nameRecordModel.name_reduced = "1234_6789";
        nameRecordModel.description = "test description";
        nameRecordModel.url = "test url";
        nameRecordModel.expires_at = ZonedDateTime.now().plusMonths(1);
        nameRecordModel.entries = new ArrayList<>();
        NameEntryModel nameEntryModel = new NameEntryModel();
        nameEntryModel.short_addr = TestKeys.privateKey(0).getPublicKey().getShortAddress().toString();
        nameEntryModel.long_addr = TestKeys.privateKey(0).getPublicKey().getLongAddress().toString();
        nameRecordModel.entries.add(nameEntryModel);
        nameEntryModel = new NameEntryModel();
        nameEntryModel.origin = HashId.createRandom().getDigest();
        nameRecordModel.entries.add(nameEntryModel);

        ledger.removeNameRecord(nameRecordModel.name_reduced);
        ledger.addNameRecord(nameRecordModel);

        NameRecordModel loadedNameRecord = ledger.getNameRecord(nameRecordModel.name_reduced);

        assertEquals(nameRecordModel.name_reduced, loadedNameRecord.name_reduced);
        assertEquals(nameRecordModel.name_full, loadedNameRecord.name_full);
        assertEquals(nameRecordModel.description, loadedNameRecord.description);
        assertEquals(nameRecordModel.url, loadedNameRecord.url);
        assertEquals(nameRecordModel.expires_at.toEpochSecond(), loadedNameRecord.expires_at.toEpochSecond());
        assertEquals(nameRecordModel.entries.size(), loadedNameRecord.entries.size());

        loadedNameRecord = ledger.getNameByAddress(nameRecordModel.entries.get(0).short_addr);

        assertEquals(nameRecordModel.name_reduced, loadedNameRecord.name_reduced);
        assertEquals(nameRecordModel.name_full, loadedNameRecord.name_full);
        assertEquals(nameRecordModel.description, loadedNameRecord.description);
        assertEquals(nameRecordModel.url, loadedNameRecord.url);
        assertEquals(nameRecordModel.expires_at.toEpochSecond(), loadedNameRecord.expires_at.toEpochSecond());
        assertEquals(nameRecordModel.entries.size(), loadedNameRecord.entries.size());

        loadedNameRecord = ledger.getNameByAddress(nameRecordModel.entries.get(0).long_addr);

        assertEquals(nameRecordModel.name_reduced, loadedNameRecord.name_reduced);
        assertEquals(nameRecordModel.name_full, loadedNameRecord.name_full);
        assertEquals(nameRecordModel.description, loadedNameRecord.description);
        assertEquals(nameRecordModel.url, loadedNameRecord.url);
        assertEquals(nameRecordModel.expires_at.toEpochSecond(), loadedNameRecord.expires_at.toEpochSecond());
        assertEquals(nameRecordModel.entries.size(), loadedNameRecord.entries.size());

        loadedNameRecord = ledger.getNameByOrigin(nameRecordModel.entries.get(1).origin);

        assertEquals(nameRecordModel.name_reduced, loadedNameRecord.name_reduced);
        assertEquals(nameRecordModel.name_full, loadedNameRecord.name_full);
        assertEquals(nameRecordModel.description, loadedNameRecord.description);
        assertEquals(nameRecordModel.url, loadedNameRecord.url);
        assertEquals(nameRecordModel.expires_at.toEpochSecond(), loadedNameRecord.expires_at.toEpochSecond());
        assertEquals(nameRecordModel.entries.size(), loadedNameRecord.entries.size());

        nameRecordModel.entries.sort((NameEntryModel e1, NameEntryModel e2) -> e1.short_addr==null?-1:e1.short_addr.compareTo(e2.short_addr));
        loadedNameRecord.entries.sort((NameEntryModel e1, NameEntryModel e2) -> e1.short_addr==null?-1:e1.short_addr.compareTo(e2.short_addr));

        for (int i = 0; i < nameRecordModel.entries.size(); ++i) {
            assertEquals(nameRecordModel.entries.get(i).short_addr, loadedNameRecord.entries.get(i).short_addr);
            assertEquals(nameRecordModel.entries.get(i).long_addr, loadedNameRecord.entries.get(i).long_addr);
            if (nameRecordModel.entries.get(i).origin == null)
                assertEquals(nameRecordModel.entries.get(i).origin, loadedNameRecord.entries.get(i).origin);
            else
                assertEquals(Bytes.toHex(nameRecordModel.entries.get(i).origin), Bytes.toHex(loadedNameRecord.entries.get(i).origin));
        }

        PreparedStatement st = ledger.getDb().statement("select count(*) from name_storage where id = ?", loadedNameRecord.id);
        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
        st = ledger.getDb().statement("select count(*) from name_entry where name_storage_id = ?", loadedNameRecord.id);
        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(2, rs.getInt(1));
        }

        ledger.removeNameRecord(nameRecordModel.name_reduced);

        st = ledger.getDb().statement("select count(*) from name_storage where id = ?", loadedNameRecord.id);
        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
        st = ledger.getDb().statement("select count(*) from name_entry where name_storage_id = ?", loadedNameRecord.id);
        try(ResultSet rs = st.executeQuery()) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }


    @Test
    public void unsUniqueFields() throws Exception {
        long now = StateRecord.unixTime(ZonedDateTime.now());
        Binder someBinder = Binder.of("balance", "12.345", "expires_at", now);
        HashId contractId = HashId.createRandom();
        Contract someContract = new Contract(TestKeys.privateKey(0));
        someContract.seal();

        long id1 = ledger.updateEnvironment("UNS0test", contractId, Boss.pack(someBinder), someContract.getPackedTransaction());
        System.out.println("id1: " + id1);

        NameRecordModel nameRecordModel = new NameRecordModel();
        nameRecordModel.environment_id = id1;
        nameRecordModel.name_full = "test_name";
        nameRecordModel.name_reduced = "1234_6789";
        nameRecordModel.description = "test description";
        nameRecordModel.url = "test url";
        nameRecordModel.expires_at = ZonedDateTime.now().plusMonths(1);
        nameRecordModel.entries = new ArrayList<>();
        NameEntryModel nameEntryModel = new NameEntryModel();
        nameEntryModel.short_addr = TestKeys.privateKey(0).getPublicKey().getShortAddress().toString();
        nameEntryModel.long_addr = TestKeys.privateKey(0).getPublicKey().getLongAddress().toString();
        nameRecordModel.entries.add(nameEntryModel);
        nameEntryModel = new NameEntryModel();
        nameEntryModel.origin = HashId.createRandom().getDigest();
        nameRecordModel.entries.add(nameEntryModel);

        ledger.removeNameRecord(nameRecordModel.name_reduced);
        ledger.addNameRecord(nameRecordModel);

        NameRecordModel nameRecordModel2 = new NameRecordModel();
        nameRecordModel2.environment_id = id1;
        nameRecordModel2.name_full = "test_name2";
        nameRecordModel2.name_reduced = "1234_67890";
        nameRecordModel2.description = "test description";
        nameRecordModel2.url = "test url";
        nameRecordModel2.expires_at = ZonedDateTime.now().plusMonths(1);
        nameRecordModel2.entries = new ArrayList<>();
        NameEntryModel nameEntryModel2 = new NameEntryModel();
        nameEntryModel2.short_addr = TestKeys.privateKey(1).getPublicKey().getShortAddress().toString();
        nameEntryModel2.long_addr = TestKeys.privateKey(1).getPublicKey().getLongAddress().toString();
        nameRecordModel2.entries.add(nameEntryModel2);
        nameEntryModel2 = new NameEntryModel();
        nameEntryModel2.origin = HashId.createRandom().getDigest();
        nameRecordModel2.entries.add(nameEntryModel2);

        ledger.removeNameRecord(nameRecordModel2.name_reduced);
        ledger.addNameRecord(nameRecordModel2);

        assertFalse(ledger.isAllNameRecordsAvailable(Arrays.asList(nameRecordModel.name_reduced, nameRecordModel2.name_reduced)));
        assertFalse(ledger.isAllOriginsAvailable(Arrays.asList(HashId.withDigest(nameEntryModel.origin), HashId.withDigest(nameEntryModel2.origin))));
        assertFalse(ledger.isAllAddressesAvailable(Arrays.asList(
                TestKeys.publicKey(0).getShortAddress().toString(),
                TestKeys.publicKey(0).getLongAddress().toString(),
                TestKeys.publicKey(1).getShortAddress().toString(),
                TestKeys.publicKey(1).getLongAddress().toString())));

        assertFalse(ledger.isAllNameRecordsAvailable(Arrays.asList(nameRecordModel.name_reduced, nameRecordModel2.name_reduced, "some_name")));
        assertFalse(ledger.isAllOriginsAvailable(Arrays.asList(HashId.withDigest(nameEntryModel.origin), HashId.withDigest(nameEntryModel2.origin), HashId.createRandom())));
        assertFalse(ledger.isAllAddressesAvailable(Arrays.asList(
                TestKeys.publicKey(0).getShortAddress().toString(),
                TestKeys.publicKey(0).getLongAddress().toString(),
                TestKeys.publicKey(1).getShortAddress().toString(),
                TestKeys.publicKey(1).getLongAddress().toString(),
                TestKeys.publicKey(2).getShortAddress().toString(),
                TestKeys.publicKey(2).getLongAddress().toString())));

        ledger.removeNameRecord(nameRecordModel.name_reduced);
        ledger.removeNameRecord(nameRecordModel2.name_reduced);

        assertTrue(ledger.isAllNameRecordsAvailable(Arrays.asList(nameRecordModel.name_reduced, nameRecordModel2.name_reduced)));
        assertTrue(ledger.isAllOriginsAvailable(Arrays.asList(HashId.withDigest(nameEntryModel.origin), HashId.withDigest(nameEntryModel2.origin))));
        assertTrue(ledger.isAllAddressesAvailable(Arrays.asList(
                TestKeys.publicKey(0).getShortAddress().toString(),
                TestKeys.publicKey(0).getLongAddress().toString(),
                TestKeys.publicKey(1).getShortAddress().toString(),
                TestKeys.publicKey(1).getLongAddress().toString())));

        assertTrue(ledger.isAllNameRecordsAvailable(Arrays.asList(nameRecordModel.name_reduced, nameRecordModel2.name_reduced, "some_name")));
        assertTrue(ledger.isAllOriginsAvailable(Arrays.asList(HashId.withDigest(nameEntryModel.origin), HashId.withDigest(nameEntryModel2.origin), HashId.createRandom())));
        assertTrue(ledger.isAllAddressesAvailable(Arrays.asList(
                TestKeys.publicKey(0).getShortAddress().toString(),
                TestKeys.publicKey(0).getLongAddress().toString(),
                TestKeys.publicKey(1).getShortAddress().toString(),
                TestKeys.publicKey(1).getLongAddress().toString(),
                TestKeys.publicKey(2).getShortAddress().toString(),
                TestKeys.publicKey(2).getLongAddress().toString())));
    }
    */


    @Test
    public void votingTest() throws Exception {

        Contract contract = new Contract(TestKeys.privateKey(1));
        contract.seal();

        HashSet<HashId> candidates = new HashSet<>(Do.listOf(HashId.createRandom(), HashId.createRandom(), HashId.createRandom()));
        ledger.initiateVoting(contract,ZonedDateTime.now().plusSeconds(1000),"owner",candidates);


        assertEquals(ledger.getVotes(HashId.createRandom()).size(), 0);
        assertEquals(ledger.getVotes(Do.sample(candidates)).size(), 1);


        Contract contract2 = new Contract(TestKeys.privateKey(1));
        contract2.seal();

        ledger.initiateVoting(contract2,ZonedDateTime.now().plusSeconds(1000),"owner",candidates);

        assertEquals(ledger.getVotes(Do.sample(candidates)).size(), 2);


        VoteInfo vi1 = ledger.getVotingInfo(contract.getId());
        VoteInfo vi2 = ledger.getVotingInfo(contract2.getId());

        for(HashId hashId : vi1.candidateIds.keySet()) {
            ledger.addVotes(vi1.votingId,vi1.candidateIds.get(hashId),Do.listOf(TestKeys.publicKey(1).getLongAddress(),TestKeys.publicKey(2).getLongAddress(),TestKeys.publicKey(3).getLongAddress()));
        }

        HashId last = null;
        for(HashId hashId : vi2.candidateIds.keySet()) {
            last = hashId;
            ledger.addVotes(vi2.votingId,vi2.candidateIds.get(hashId),Do.listOf(TestKeys.publicKey(1).getLongAddress(),TestKeys.publicKey(2).getLongAddress(),TestKeys.publicKey(3).getLongAddress()));
        }


        for(HashId hashId : candidates) {
            HashId finalLast = last;
            ledger.getVotes(hashId).forEach(vr -> assertEquals(vr.votesCount.longValue(),hashId.equals(finalLast) ? 3 : 0));
        }




    }

    @Test
    public void ubotSessionTest() throws Exception {
        Map<String, Map<Integer,HashId>> storageUpdates = new ConcurrentHashMap<>();
        storageUpdates.put("s1", new ConcurrentHashMap<>());
        storageUpdates.get("s1").put(1, HashId.createRandom());
        storageUpdates.get("s1").put(2, HashId.createRandom());
        storageUpdates.get("s1").put(3, HashId.createRandom());
        storageUpdates.put("s2", new ConcurrentHashMap<>());
        storageUpdates.get("s2").put(4, HashId.createRandom());
        storageUpdates.get("s2").put(5, HashId.createRandom());
        Set<Integer> closeVotes = ConcurrentHashMap.newKeySet();
        closeVotes.add(1);
        closeVotes.add(2);
        closeVotes.add(3);
        Set<Integer> closeVotesFinished = ConcurrentHashMap.newKeySet();
        closeVotesFinished.add(4);
        closeVotesFinished.add(5);

        Ledger.UbotSessionCompact compact = new Ledger.UbotSessionCompact();
        compact.id = 0; // will be generated automatically if needed
        compact.executableContractId = HashId.createRandom();
        compact.requestId = HashId.createRandom();
        compact.requestContract = Do.randomBytes(1024);
        compact.state = 33;
        compact.sessionId = HashId.createRandom();
        compact.storageUpdates = storageUpdates;
        compact.closeVotes = closeVotes;
        compact.closeVotesFinished = closeVotesFinished;
        compact.quantaLimit = 400;
        compact.expiresAt = ZonedDateTime.now();

        BiConsumer<Ledger.UbotSessionCompact, Ledger.UbotSessionCompact> assertUbotSessionCompactEquals = (expected, loaded) -> {
            assertEquals(expected.executableContractId, loaded.executableContractId);
            assertEquals(expected.requestId, loaded.requestId);
            assertArrayEquals(expected.requestContract, loaded.requestContract);
            assertEquals(expected.state, loaded.state);
            assertEquals(expected.sessionId, loaded.sessionId);
            assertEquals(expected.storageUpdates.size(), loaded.storageUpdates.size());
            expected.storageUpdates.forEach((k, v) -> {
                assertTrue(loaded.storageUpdates.containsKey(k));
                Map<Integer,HashId> lv = loaded.storageUpdates.get(k);
                assertEquals(v.size(), lv.size());
                v.forEach((k0, v0) -> {
                    assertTrue(lv.containsKey(k0));
                    assertEquals(v0, lv.get(k0));
                });
            });
            assertEquals(expected.closeVotes.size(), loaded.closeVotes.size());
            assertTrue(loaded.closeVotes.containsAll(expected.closeVotes));
            assertEquals(expected.closeVotesFinished.size(), loaded.closeVotesFinished.size());
            assertTrue(loaded.closeVotesFinished.containsAll(expected.closeVotesFinished));
            assertEquals(expected.quantaLimit, loaded.quantaLimit);
            assertTrue(Duration.between(expected.expiresAt, loaded.expiresAt).getSeconds() <= 1);
        };

        // test creation of new ubot_session in ledger
        ledger.saveUbotSession(compact);
        Ledger.UbotSessionCompact loaded = ledger.loadUbotSession(compact.executableContractId);
        assertUbotSessionCompactEquals.accept(compact, loaded);

        // test update of existing ubot_session
        loaded.state = 44;
        loaded.requestId = HashId.createRandom();
        loaded.storageUpdates.get("s1").clear();
        loaded.closeVotes.add(8);
        loaded.closeVotesFinished.clear();
        loaded.closeVotesFinished.add(42);
        loaded.quantaLimit = 505;
        loaded.expiresAt = ZonedDateTime.now().plusSeconds(600);
        ledger.saveUbotSession(loaded);
        Ledger.UbotSessionCompact loaded2 = ledger.loadUbotSession(loaded.executableContractId);
        assertEquals(loaded.id, loaded2.id);
        assertUbotSessionCompactEquals.accept(loaded, loaded2);
    }

    @Test
    public void ubotSessionCleanup() throws Exception {
        Ledger.UbotSessionCompact compact = new Ledger.UbotSessionCompact();
        compact.storageUpdates = new ConcurrentHashMap<>();
        compact.executableContractId = HashId.createRandom();
        compact.expiresAt = ZonedDateTime.now().plusSeconds(2);
        compact.requestId = HashId.createRandom();
        compact.sessionId = HashId.createRandom();
        compact.closeVotes = ConcurrentHashMap.newKeySet();
        compact.closeVotesFinished = ConcurrentHashMap.newKeySet();
        ledger.saveUbotSession(compact);
        assertNotEquals(null, ledger.loadUbotSession(compact.executableContractId));
        assertTrue(ledger.hasUbotSession(compact.executableContractId));
        ledger.deleteExpiredUbotSessions();
        assertNotEquals(null, ledger.loadUbotSession(compact.executableContractId));
        assertTrue(ledger.hasUbotSession(compact.executableContractId));
        System.out.println("wait for session cleanup (~4 sec)...");
        Thread.sleep(4000);
        ledger.deleteExpiredUbotSessions();
        assertEquals(null, ledger.loadUbotSession(compact.executableContractId));
        assertFalse(ledger.hasUbotSession(compact.executableContractId));
    }

    @Test
    public void ubotStorageTest() throws Exception {
        HashId executableContractId = HashId.createRandom();
        ZonedDateTime expiresAt = ZonedDateTime.now().plusSeconds(2);
        String storageName = "s1";
        HashId val1 = HashId.createRandom();
        ledger.saveUbotStorageValue(executableContractId, expiresAt, storageName, val1);
        assertEquals(val1, ledger.getUbotStorageValue(executableContractId, storageName));
        HashId val2 = HashId.createRandom();
        assertNotEquals(val1, val2);
        ledger.saveUbotStorageValue(executableContractId, expiresAt, storageName, val2);
        assertEquals(val2, ledger.getUbotStorageValue(executableContractId, storageName));
    }

    @Test
    public void ubotGetStorages() throws Exception {
        HashId executableContractId = HashId.createRandom();
        ZonedDateTime expiresAt = ZonedDateTime.now().plusSeconds(2);
        String storage1Name = "s1";
        String storage2Name = "s2";
        HashId val1 = HashId.createRandom();
        HashId val2 = HashId.createRandom();
        ledger.saveUbotStorageValue(executableContractId, expiresAt, storage1Name, val1);
        ledger.saveUbotStorageValue(executableContractId, expiresAt, storage2Name, val2);
        Map<String, HashId> storages = new ConcurrentHashMap<>();
        ledger.getUbotStorages(executableContractId, storages);
        assertEquals(2, storages.size());
        assertEquals(val1, storages.get(storage1Name));
        assertEquals(val2, storages.get(storage2Name));
    }

    @Test
    public void ubotStorageCleanup() throws Exception {
        HashId executableContractId = HashId.createRandom();
        ZonedDateTime expiresAt = ZonedDateTime.now().plusSeconds(2);
        String storageName = "s1";
        HashId val1 = HashId.createRandom();
        ledger.saveUbotStorageValue(executableContractId, expiresAt, storageName, val1);
        assertEquals(val1, ledger.getUbotStorageValue(executableContractId, storageName));
        ledger.deleteExpiredUbotSessions();
        assertEquals(val1, ledger.getUbotStorageValue(executableContractId, storageName));
        System.out.println("wait for storage cleanup (~4 sec)...");
        Thread.sleep(4000);
        ledger.deleteExpiredUbotStorages();
        assertEquals(null, ledger.getUbotStorageValue(executableContractId, storageName));
    }

}
