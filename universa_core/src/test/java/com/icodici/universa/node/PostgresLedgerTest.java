package com.icodici.universa.node;

import com.icodici.crypto.PrivateKey;
import com.icodici.db.PooledDb;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node.network.TestKeys;
import com.icodici.universa.node2.ItemLock;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.NodeInfo;
import com.icodici.universa.node2.NodeStats;
import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
        assertSame(r2, r1);
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

        // we lock again the same record, everything should be still ok:
        StateRecord r2 = r.lockToRevoke(existing.getId());
        assertNotNull(r2);
        existing.reload();
        r.reload();
        assertSameRecords(existing, r1);
        assertSameRecords(existing, r2);
        assertSame(r1, r2);
        assertEquals(ItemState.LOCKED, existing.getState());
        assertEquals(r.getRecordId(), existing.getLockedByRecordId());

        StateRecord r3 = r.lockToRevoke(existing2.getId());
        existing2.reload();
        assertSameRecords(existing2, r3);
        assertEquals(ItemState.LOCKED, existing2.getState());
        assertEquals(r.getRecordId(), existing2.getLockedByRecordId());
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
                            StateRecord.unixTime(sr.getExpiresAt()),
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
                                        StateRecord.unixTime(sr.getExpiresAt()),
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

        ledger.cleanup();


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
        stats.init(ledger);
        ZonedDateTime now  = ZonedDateTime.now();
        ZonedDateTime dateTime = now.minusDays(now.getDayOfMonth()-1).minusMonths(1);
        while (dateTime.isBefore(ZonedDateTime.now().plusSeconds(1))) {
            ledger.savePayment(100,dateTime);
            ledger.savePayment(100,dateTime);
            dateTime = dateTime.plusDays(1);
        }

        Config config = new Config();
        stats.collect(ledger,config);


        assertEquals(stats.todayPaidAmount,200);
        assertEquals(stats.yesterdayPaidAmount,200);
        assertEquals(stats.thisMonthPaidAmount,200*now.getDayOfMonth());
        assertEquals(stats.lastMonthPaidAmount,200*now.minusMonths(1).getMonth().length(now.getYear() % 4 == 0));

    }

}
