/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.crypto.PrivateKey;
import com.icodici.db.Db;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.services.NImmutableEnvironment;
import com.icodici.universa.contract.services.NNameRecord;
import com.icodici.universa.contract.services.NSmartContract;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import java.lang.ref.WeakReference;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @exclude out of date, need updating to match postgres ledger
 *
 * The basic SQL-based ledger. At the moment its code is outdated and should not be used upon refresfing.
 * reserved for some tests and small private networks on weak devices.
 * <p>
 * This implementation uses SQLite, but could be easily enhanced to use any jdbc provider.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public class SqliteLedger implements Ledger {
    private final Db db;

    private Object writeLock = new Object();
    //    private Object transactionLock = new Object();
    private Map<HashId, WeakReference<StateRecord>> cachedRecords = new WeakHashMap<>();
    private boolean useCache = true;

    public SqliteLedger(String connectionString) throws SQLException {
        Properties properties;
            SQLiteConfig config = new SQLiteConfig();
            config.setSharedCache(true);
            config.setJournalMode(SQLiteConfig.JournalMode.WAL);
            config.setOpenMode(SQLiteOpenMode.FULLMUTEX);
            properties = config.toProperties();
        db = new Db(connectionString, properties);
        db.setupDatabase("/migrations/sqlite/migrate_");
    }

    @Override
    public StateRecord getRecord(HashId itemId) {
        StateRecord sr = protect(() -> {
            StateRecord cached = getFromCache(itemId);
            if (cached != null)
                return cached;
            try (ResultSet rs = db.queryRow("SELECT * FROM ledger WHERE hash = ? limit 1", itemId.getDigest())) {
                if (rs != null) {
                    StateRecord record = new StateRecord(this, rs);
                    putToCache(record);
                    return record;
                }
            }
            return null;
        });
        if (sr != null && sr.isExpired()) {
            sr.destroy();
            return null;
        }
        return sr;
    }

    private StateRecord getFromCache(HashId itemId) {
        if (useCache) {
            synchronized (cachedRecords) {
                WeakReference<StateRecord> ref = cachedRecords.get(itemId);
                if (ref == null)
                    return null;
                StateRecord r = ref.get();
                if (r == null) {
                    cachedRecords.remove(itemId);
                    return null;
                }
                return r;
            }
        } else
            return null;
    }

    private void putToCache(StateRecord r) {
        if (useCache) {
            synchronized (cachedRecords) {
                cachedRecords.put(r.getId(), new WeakReference<StateRecord>(r));
            }
        }
    }


    @Override
    public StateRecord createOutputLockRecord(long creatorRecordId, HashId newItemHashId) {
        StateRecord r = new StateRecord(this);
        r.setState(ItemState.LOCKED_FOR_CREATION);
        r.setLockedByRecordId(creatorRecordId);
        r.setId(newItemHashId);
        try {
            r.save();
            return r;
        } catch (Ledger.Failure e) {
            return null;
        }
    }

    @Override
    public StateRecord getLockOwnerOf(StateRecord rc) {
        throw new RuntimeException("not implemented");
//        return null;
    }

    @Override
    public StateRecord findOrCreate(HashId itemId) {
        // This simple version requires that database is used exclusively by one localnode - the normal way. As nodes
        // are multithreaded, there is absolutely no use to share database between nodes.
        return protect(() -> {
            synchronized (writeLock) {
                StateRecord r = getRecord(itemId);
                if (r == null) {
                    r = new StateRecord(this);
                    r.setId(itemId);
                    r.setState(ItemState.PENDING);
                    r.save();
                }
                if( r == null )
                    throw new RuntimeException("failure creating new stateReocrd");
                return r;
            }
        });
    }

    private <T> T protect(Callable<T> block) {
        try {
            return block.call();
        } catch (Exception ex) {
            throw new Ledger.Failure("Ledger operation failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        try {
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Map<ItemState, Integer> getLedgerSize(ZonedDateTime createdAfter) {
        return null;
    }

    @Override
    public void savePayment(int amount, ZonedDateTime date) {

    }

    @Override
    public Map<Integer, Integer> getPayments(ZonedDateTime fromDate) {
        return null;
    }

    @Override
    public void markTestRecord(HashId hashId) {

    }

    @Override
    public boolean isTestnet(HashId itemId) {
        return false;
    }

    @Override
    public void updateSubscriptionInStorage(long id, ZonedDateTime expiresAt) {

    }

    @Override
    public void updateNameRecord(long id, ZonedDateTime expiresAt) {

    }

    @Override
    public Set<HashId> saveEnvironment(NImmutableEnvironment environment) {
        return null;
    }

    @Override
    public void saveConfig(NodeInfo myInfo, NetConfig netConfig, PrivateKey nodeKey) {

    }

    @Override
    public Object[] loadConfig() {
        return new Object[0];
    }

    @Override
    public void addNode(NodeInfo nodeInfo) {

    }

    @Override
    public void removeNode(NodeInfo nodeInfo) {

    }

    @Override
    public Map<HashId,StateRecord> findUnfinished() {
        return null;
    }

    @Override
    public Approvable getItem(StateRecord record) {
        return null;
    }

    @Override
    public void putItem(StateRecord record, Approvable item, Instant keepTill) {

    }

    @Override
    public NImmutableEnvironment getEnvironment(long environmentId) {
        return null;
    }

    @Override
    public NImmutableEnvironment getEnvironment(HashId contractId) {
        return null;
    }

    @Override
    public NImmutableEnvironment getEnvironment(NSmartContract smartContract) {
        return null;
    }

    @Override
    public void updateEnvironment(long id, String ncontractType, HashId ncontractHashId, byte[] kvStorage, byte[] transactionPack) {

    }


    @Override
    public <T> T transaction(Callable<T> callable) {
        return protect(() -> {
//            synchronized (transactionLock) {
            // as Rollback exception is instanceof Db.Rollback, it will work as supposed by default:
            // rethrow unchecked exceotions and return null on rollback.
            return db.transaction(() -> callable.call());
//            }
        });
    }

    @Override
    public void destroy(StateRecord record) {
        long recordId = record.getRecordId();
        if (recordId == 0) {
            throw new IllegalStateException("can't destroy record without recordId");
        }
        protect(() -> {
            synchronized (writeLock) {
                db.update("DELETE FROM ledger WHERE id = ?", recordId);
            }
            synchronized (cachedRecords) {
                cachedRecords.remove(record.getId());
            }
            return null;
        });
    }

    @Override
    public void save(StateRecord stateRecord) {
        if (stateRecord.getLedger() == null) {
            stateRecord.setLedger(this);
        } else if (stateRecord.getLedger() != this)
            throw new IllegalStateException("can't save with  adifferent ledger (make a copy!)");

        try {
            synchronized (writeLock) {
                if (stateRecord.getRecordId() == 0) {
                    try (
                            PreparedStatement statement =
                                    db.statement(
                                            "insert into ledger(hash,state,created_at, expires_at, locked_by_id) values(?,?,?,?,?);")
                    ) {
                        statement.setBytes(1, stateRecord.getId().getDigest());
                        statement.setInt(2, stateRecord.getState().ordinal());
                        statement.setLong(3, StateRecord.unixTime(stateRecord.getCreatedAt()));
                        statement.setLong(4, StateRecord.unixTime(stateRecord.getExpiresAt()));
                        statement.setLong(5, stateRecord.getLockedByRecordId());
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (!keys.next())
                                throw new RuntimeException("generated keys are not supported");
                            long id = keys.getLong(1);
                            stateRecord.setRecordId(id);
                        }
                    }
                    putToCache(stateRecord);
                } else {
                    db.update("update ledger set state=?, expires_at=?, locked_by_id=? where id=?",
                              stateRecord.getState().ordinal(),
                              StateRecord.unixTime(stateRecord.getExpiresAt()),
                              stateRecord.getLockedByRecordId(),
                              stateRecord.getRecordId()
                    );
                }
            }
        } catch (SQLException se) {
//            se.printStackTrace();
            throw new Ledger.Failure("StateRecord save failed:" + se);
        }
    }


    @Override
    public void reload(StateRecord stateRecord) throws StateRecord.NotFoundException {
        try {
            try (ResultSet rs = db.queryRow("SELECT * FROM ledger WHERE hash = ? limit 1",
                                            stateRecord.getId().getDigest())
            ) {
                if (rs == null)
                    throw new StateRecord.NotFoundException("record not found");
                stateRecord.initFrom(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to reload RecordSet", e);
        }
    }


    @Override
    public List<Long> clearExpiredStorageSubscriptions() {return null;}

    @Override
    public void clearExpiredStorageContracts() {}


    @Override
    public long saveContractInStorage(HashId contractId, byte[] binData, ZonedDateTime expiresAt, HashId origin) {return 0;}

    @Override
    public long saveSubscriptionInStorage(long contractStorageId, ZonedDateTime expiresAt, long environmentId) {return 0;}

    @Override
    public Set<Long> getSubscriptionEnviromentIdsForContractId(HashId contractId) {
        return null;
    }


    @Override
    public byte[] getContractInStorage(HashId contractId) {return null;}
    @Override
    public void removeEnvironmentSubscription(long subscriptionId) {}

    @Override
    public long removeEnvironment(HashId ncontractHashId) {return 0;}


    @Override
    public void removeExpiredStorageSubscriptionsCascade() {}

    @Override
    public void addNameRecord(NNameRecord nameRecordModel) {

    }

    @Override
    public byte[] getSlotContractBySlotId(HashId slotId) {return null;}



    @Override
    public void removeNameRecord(final String nameReduced) {}

    @Override
    public NNameRecord getNameRecord(final String nameReduced) {return null;}

    @Override
    public NNameRecord getNameByAddress (String address) {return null;}

    @Override
    public NNameRecord getNameByOrigin (byte[] origin) {return null;}

    @Override
    public List<String> isAllNameRecordsAvailable(Collection<String> reducedNames) {
        return Arrays.asList("not_implemented");
    }

    @Override
    public List<String> isAllOriginsAvailable(Collection<HashId> origins) {
        return Arrays.asList("not_implemented");
    }

    @Override
    public List<String> isAllAddressesAvailable(Collection<String> addresses) {
        return Arrays.asList("not_implemented");
    }


    @Override
    public void clearExpiredNameRecords(Duration holdDuration) {

    }


    /**
         * Enable or disable records caching. USe it in tests only, in production it should always be enabled
         *
         * @param enable, if true it is enabling cache
         */
    public void enableCache(boolean enable) {
        if (enable) {
            this.useCache = true;
        } else {
            this.useCache = false;
            cachedRecords.clear();
        }
    }

    public Db getDb() {
        return db;
    }

    public void cleanup() {

    }

}
