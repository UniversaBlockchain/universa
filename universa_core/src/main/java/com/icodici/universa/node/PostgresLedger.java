/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.db.Db;
import com.icodici.db.DbPool;
import com.icodici.db.PooledDb;
import com.icodici.universa.HashId;

import java.lang.ref.WeakReference;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;

/**
 * The basic SQL-based ledger.
 * <p>
 * This implementation uses SQLite, but could be easily enhanced to use any jdbc provider.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public class PostgresLedger implements Ledger {

    private final static int MAX_CONNECTIONS = 32;

    private final DbPool dbPool;

    private boolean sqlite = false;

    private Object writeLock = new Object();
    //    private Object transactionLock = new Object();
    private Map<HashId, WeakReference<StateRecord>> cachedRecords = new WeakHashMap<>();
    private boolean useCache = true;

    public PostgresLedger(String connectionString, Properties properties) throws SQLException {
        dbPool = new DbPool(connectionString, properties, MAX_CONNECTIONS);
        init(dbPool);
    }

    public PostgresLedger(String connectionString) throws SQLException {
        Properties properties = new Properties();
        dbPool = new DbPool(connectionString, properties, MAX_CONNECTIONS);
        init(dbPool);
    }

    private void init(DbPool dbPool) throws SQLException {
        try {
            dbPool.execute(db -> {
                db.setupDatabase("/migrations/postgres/migrate_");
            });
        } catch (Exception e) {
            throw new SQLException("Failed to migrate", e);
        }
    }

    /**
     * Get the instance of the {@link Db} for a calling thread. Safe to call repeatedly (returns the same instance per
     * thread).
     */
    public final <T> T inPool(DbPool.DbConsumer<T> consumer) throws Exception {
        return dbPool.execute(consumer);
    }


    @Override
    public StateRecord getRecord(HashId itemId) {
        StateRecord sr = protect(() -> {
            StateRecord cached = getFromCache(itemId);
            if (cached != null)
                return cached;
            try (ResultSet rs = inPool(db -> db.queryRow("SELECT * FROM ledger WHERE hash = ? limit 1", itemId.getDigest()))) {
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
        } catch (Failure e) {
            return null;
        }
    }

    @Override
    public StateRecord findOrCreate(HashId itemId) {
        // This simple version requires that database is used exclusively by one localnode - the normal way. As nodes
        // are multithreaded, there is absolutely no use to share database between nodes.
        return protect(() -> {
            StateRecord record = getFromCache(itemId);
            if( record == null) {
                try (ResultSet rs = inPool(db->db.queryRow("select * from sr_find_or_create(?)", itemId.getDigest()))) {
                    record = new StateRecord(this, rs);
                    putToCache(record);
                }
            }
            return record;

        });
    }

    private <T> T protect(Callable<T> block) {
        try {
            return block.call();
        } catch (Exception ex) {
            throw new Failure("Ledger operation failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void close() {
        try {
            dbPool.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public long countRecords() {
        try {
            return dbPool.execute((db)->(long)db.queryOne("SELECT COUNT(*) FROM ledger"));
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public <T> T transaction(Callable<T> callable) {
        return protect(() -> {
//            synchronized (transactionLock) {
            // as Rollback exception is instanceof Db.Rollback, it will work as supposed by default:
            // rethrow unchecked exceotions and return null on rollback.
            try (Db db = dbPool.db()) {
                return db.transaction(() -> callable.call());
            }
//            }
        });
    }

    public void testClearLedger() {
        try {
            dbPool.execute(db -> {
                db.update("truncate ledger;");
                return null;
            });
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void destroy(StateRecord record) {
        long recordId = record.getRecordId();
        if (recordId == 0) {
            throw new IllegalStateException("can't destroy record without recordId");
        }
        protect(() -> {
            inPool(d -> {
                d.update("DELETE FROM ledger WHERE id = ?", recordId);
                return null;
            });
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
            throw new IllegalStateException("can't save with a different ledger (make a copy!)");

        // TODO: probably, it should take a PooledDb as an argument and reuse it
        try (PooledDb db = dbPool.db()) {
            if (stateRecord.getRecordId() == 0) {
                try (
                        PreparedStatement statement =
                                db.statementReturningKeys(
                                        "insert into ledger(hash,state,created_at, expires_at, locked_by_id) values(?,?,?,?,?);"
                                )
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
        } catch (SQLException se) {
//            se.printStackTrace();
            throw new Failure("StateRecord save failed:" + se);
        }
    }


    @Override
    public void reload(StateRecord stateRecord) throws StateRecord.NotFoundException {
        try {
            try (
                    PooledDb db = dbPool.db();
                    ResultSet rs = db.queryRow("SELECT * FROM ledger WHERE hash = ? limit 1",
                                               stateRecord.getId().getDigest()
                    );
            ) {
                if (rs == null)
                    throw new StateRecord.NotFoundException("record not found");
                stateRecord.initFrom(rs);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to reload RecordSet", e);
        }
    }

    /**
     * Enable or disable records caching. USe it in tests only, in production it should always be enabled
     *
     * @param enable
     */
    public void enableCache(boolean enable) {
        if (enable) {
            this.useCache = true;
        } else {
            this.useCache = false;
            cachedRecords.clear();
        }
    }

    public Db getDb() throws SQLException {
        return dbPool.db();
    }
}
