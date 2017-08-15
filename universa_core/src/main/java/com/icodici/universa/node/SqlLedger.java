/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.db.Db;
import com.icodici.universa.HashId;
import org.sqlite.SQLiteConfig;

import java.lang.ref.WeakReference;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The basic SQL-based ledger.
 * <p>
 * This implementation uses SQLite, but could be easily enhanced to use any jdbc provider.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public class SqlLedger implements Ledger {
    private final Db dbtool;

//    private final Connection connection;

    private AtomicInteger aint = new AtomicInteger(177140);
    private String connString;
    private boolean sqlite = false;

    private Object creationLock = new Object();
    private Object transactionLock = new Object();
    private Map<HashId, WeakReference<StateRecord>> cachedRecords = new WeakHashMap<>();
    private boolean useCache = true;

    public SqlLedger(String connectionString) throws SQLException {
        sqlite = connectionString.contains("jdbc");
        if (sqlite) {
            SQLiteConfig config = new SQLiteConfig();
            config.setSharedCache(true);
            dbtool = new Db(connectionString, config.toProperties(), "/migrations/migrate_");
        } else {
            dbtool = new Db(connectionString, null, "/migrations/migrate_");
        }
    }

    /**
     * Get the instance of the {@link Db} for a calling thread. Safe to call repeatedly (retuns the same instance
     * per thread).
     */
    public final Db db() {
        return dbtool.instance();
    }


    @Override
    public StateRecord getRecord(HashId itemId) {
        StateRecord sr = protect(() -> {
            StateRecord cached = getFromCache(itemId);
            if (cached != null)
                return cached;
            try (ResultSet rs = dbtool.queryRow("SELECT * FROM ledger WHERE hash = ? limit 1", itemId.getDigest())) {
                if (rs != null) {
                    StateRecord record = new StateRecord(this, rs);
                    putToCache(record);
                    return record;
                }
            }
            return null;
        });
        if( sr != null && sr.isExpired() ) {
            sr.destroy();
            return null;
        }
        return sr;
    }

    private StateRecord getFromCache(HashId itemId) {
        if( useCache ) {
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
        }
        else
            return null;
    }

    private void putToCache(StateRecord r) {
        if( useCache ) {
            synchronized (cachedRecords) {
                cachedRecords.put(r.getId(), new WeakReference<StateRecord>(r));
            }
        }
    }


    @Override
    public StateRecord createOutputLockRecord(long creatorRecordId, HashId newItemHashId) {
        synchronized (creationLock) {
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
    }

    @Override
    public StateRecord findOrCreate(HashId itemId) {
        // This simple version requires that database is used exclusively by one localnode - the normal way. As nodes
        // are multithreaded, there is absolutely no use to share database between nodes.
        return protect(() -> {
            synchronized (creationLock) {
                StateRecord r = getRecord(itemId);
                if (r == null) {
                    r = new StateRecord(this);
                    r.setId(itemId);
                    r.setState(ItemState.PENDING);
                    r.save();
                }
                return r;
            }
        });
    }

    private <T> T protect(Callable<T> block) {
        try {
            return block.call();
        } catch (Exception ex) {
            throw new Ledger.Failure("Ledger operation failed: ", ex);
        }
    }

    @Override
    public <T> T transaction(Callable<T> callable) {
        return protect(() -> {
            synchronized (transactionLock) {
                // as Rollback exception is instanceof Db.Rollback, it will work as supposed by default:
                // rethrow unchecked exceotions and return null on rollback.
                return dbtool.transaction(() -> callable.call());
            }
        });
    }

    @Override
    public void destroy(StateRecord record) {
        long recordId = record.getRecordId();
        if (recordId == 0) {
            throw new IllegalStateException("can't destroy record without recordId");
        }
        protect(() -> {
            dbtool.update("DELETE FROM ledger WHERE id = ?", recordId);
            synchronized (cachedRecords) {
                cachedRecords.remove(record.getId());
            }
            return null;
        });
    }

    @Override
    public void save(StateRecord stateRecord) {
        PreparedStatement statement;
        if (stateRecord.getLedger() == null) {
            stateRecord.setLedger(this);
        } else if (stateRecord.getLedger() != this)
            throw new IllegalStateException("can't save with  adifferent ledger (make a copy!)");

        try {
            if (stateRecord.getRecordId() == 0) {
                statement = dbtool.statement("insert into ledger(hash,state,created_at, expires_at, locked_by_id) values(?,?,?,?,?);");
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
                putToCache(stateRecord);
            } else {
                dbtool.update("update ledger set state=?, expires_at=?, locked_by_id=? where id=?",
                              stateRecord.getState().ordinal(),
                              StateRecord.unixTime(stateRecord.getExpiresAt()),
                              stateRecord.getLockedByRecordId(),
                              stateRecord.getRecordId()
                );
            }
        } catch (SQLException se) {
//            se.printStackTrace();
            throw new Ledger.Failure("StateRecord save failed:" + se);
        }
    }


    @Override
    public void reload(StateRecord stateRecord) throws StateRecord.NotFoundException {
        try (ResultSet rs = dbtool.queryRow("SELECT * FROM ledger WHERE hash = ? limit 1",
                                            stateRecord.getId().getDigest())) {
            if (rs == null)
                throw new StateRecord.NotFoundException("record not found");
            stateRecord.initFrom(rs);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to reload RecordSet", e);
        }
    }

    /**
     * Enable or disable records caching. USe it in tests only, in production it should always be enabled
     * @param enable
     */
    public void enableCache(boolean enable) {
        if( enable ) {
            this.useCache = true;
        }
        else {
            this.useCache = false;
            cachedRecords.clear();
        }
    }
}
