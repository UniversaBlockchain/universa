package com.icodici.universa.node;

import com.icodici.universa.HashId;
import net.sergeych.tools.Do;
import net.sergeych.tools.StopWatch;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
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
    }

    @Test
    public void checkNegatoveBytesInId() throws Exception {
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

    @Test
    public void ledgerBenchmark() throws Exception {
        ExecutorService es = Executors.newCachedThreadPool();
//        ExecutorService es = Executors.newSingleThreadExecutor();
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
        assertAlmostSame(LocalDateTime.now(), r.getCreatedAt());

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

        StateRecord r2 = r.lockToRevoke(existing.getId());

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
        assert(r.getExpiresAt().isAfter(LocalDateTime.now()));
        long recordId = r.getRecordId();

        LocalDateTime inFuture = LocalDateTime.now().plusHours(2);
        r.setExpiresAt(inFuture);

        StateRecord r1 = ledger.getRecord(hashId);
        assertNotEquals(r1.getExpiresAt(), inFuture);

        r.save();
        r1 = ledger.getRecord(hashId);
        assertAlmostSame(r.getExpiresAt(), r1.getExpiresAt());

        r.setExpiresAt(LocalDateTime.now().minusHours(1));
        r.save();

        r1 = ledger.getRecord(hashId);
        assertNull(r1);


    }

}
