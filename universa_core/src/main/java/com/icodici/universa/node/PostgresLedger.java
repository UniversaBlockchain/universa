/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.crypto.PrivateKey;
import com.icodici.db.Db;
import com.icodici.db.DbPool;
import com.icodici.db.PooledDb;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;

import java.lang.ref.WeakReference;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * The basic SQL-based ledger.
 * <p>
 * This implementation uses SQLite, but could be easily enhanced to use any jdbc provider.
 * <p>
 * Created by sergeych on 16/07/2017.
 */
public class PostgresLedger implements Ledger {

    private final static int MAX_CONNECTIONS = 64;

    private final DbPool dbPool;

    private boolean sqlite = false;

//    private Object writeLock = new Object();
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
     *
     * @param consumer is {@link DbPool.DbConsumer}
     * @param <T> is type
     * @return result
     * @throws Exception if something went wrong
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
    public StateRecord getLockOwnerOf(StateRecord rc) {
        StateRecord cached = getFromCache(rc.getId());
        if (cached != null) {
            return cached;
        }
        StateRecord sr = protect(() ->
            dbPool.execute(db -> {
                try (ResultSet rs = db.queryRow("SELECT * FROM ledger WHERE id = ? limit 1", rc.getLockedByRecordId())) {
                    if (rs == null)
                        return null;
                    StateRecord r = new StateRecord(this, rs);
                    putToCache(r);
                    return r;
                }
            })
        );
        if (sr != null && sr.isExpired()) {
            sr.destroy();
            return null;
        }
        return sr;
    }

    @Override
    public StateRecord findOrCreate(HashId itemId) {
        // This simple version requires that database is used exclusively by one localnode - the normal way. As nodes
        // are multithreaded, there is absolutely no use to share database between nodes.
        return protect(() -> {
            StateRecord record = getFromCache(itemId);
            if (record == null) {
                try (ResultSet rs = inPool(db -> db.queryRow("select * from sr_find_or_create(?)", itemId.getDigest()))) {
                    record = new StateRecord(this, rs);
                    putToCache(record);
                }
            }
            return record;

        });
    }

    @Override
    public Map<HashId,StateRecord> findUnfinished() {
        return protect(() -> {
            HashMap<HashId,StateRecord> map = new HashMap<>();
                try (ResultSet rs = inPool(db -> db.queryRow("select * from sr_find_unfinished()" ))) {
                    if(rs != null) {
                        do {
                            StateRecord record = new StateRecord(this, rs);
                            map.put(record.getId(),record);
                        } while (rs.next());
                    }
                }
            return map;

        });
    }

    @Override
    public Approvable getItem(final StateRecord record) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("select * from items where id = ?", record.getRecordId()))) {
                if(rs == null)
                    return null;
                return Contract.fromPackedTransaction(rs.getBytes("packed"));
            }
        });
    }

    @Override
    public void putItem(StateRecord record, Approvable item, Instant keepTill) {
        if(item instanceof Contract) {
            try (PooledDb db = dbPool.db()) {
                try (
                        PreparedStatement statement =
                                db.statement(
                                        "insert into items(id,packed,keepTill) values(?,?,?);"
                                )
                ) {
                    statement.setLong(1, record.getRecordId());
                    statement.setBytes(2, ((Contract) item).getPackedTransaction());
                    statement.setLong(3, keepTill.getEpochSecond());
                    statement.executeUpdate();
                }
            } catch (SQLException se) {
                throw new Failure("item save failed:" + se);
            }
        }
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
            return dbPool.execute((db) -> (long) db.queryOne("SELECT COUNT(*) FROM ledger"));
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public Map<ItemState, Integer> getLedgerSize(Instant createdAfter) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("select count(id), state from ledger where created_at >= ? group by state",createdAfter != null ? createdAfter.getEpochSecond() : 0))) {
                Map<ItemState,Integer> result = new HashMap<>();
                if(rs != null) {
                    do {
                        int count = rs.getInt(1);
                        ItemState state = ItemState.values()[rs.getInt(2)];
                        result.put(state, count);

                    } while (rs.next());
                }
                return result;
            }
        });
    }

//    @Override
//    public List<StateRecord> getAllByState(ItemState is) {
//        try {
//            return dbPool.execute((db) -> {
//                ResultSet rs = db.queryRow("SELECT * FROM ledger WHERE state = ?", is.ordinal());
//                for ( : rs.)
//                List<StateRecord> records = new ArrayList<>();
//                return records;
//            });
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }

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
        } catch (Exception e) {
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
                d.update("DELETE FROM items WHERE id = ?", recordId);
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

    public Db getDb() throws SQLException {
        return dbPool.db();
    }

    @Override
    public void saveConfig(NodeInfo myInfo, NetConfig netConfig, PrivateKey nodeKey) {
        try (PooledDb db = dbPool.db()) {

            try (
                    PreparedStatement statement =
                            db.statement(
                                    "delete from config;"
                            )
            ) {
                statement.executeUpdate();
            }


            for(NodeInfo nodeInfo : netConfig.toList()) {
                String sqlText;
                if(nodeInfo.getNumber() == myInfo.getNumber()) {
                    sqlText = "insert into config(http_client_port,http_server_port,udp_server_port, node_number, node_name, public_host,host,public_key,private_key) values(?,?,?,?,?,?,?,?,?);";
                } else {
                    sqlText = "insert into config(http_client_port,http_server_port,udp_server_port, node_number, node_name, public_host,host,public_key) values(?,?,?,?,?,?,?,?);";
                }

                try (
                        PreparedStatement statement = db.statementReturningKeys(sqlText)
                ) {
                    statement.setInt(1, nodeInfo.getClientAddress().getPort());
                    statement.setInt(2, nodeInfo.getServerAddress().getPort());
                    statement.setInt(3, nodeInfo.getNodeAddress().getPort());
                    statement.setInt(4, nodeInfo.getNumber());
                    statement.setString(5, nodeInfo.getName());
                    statement.setString(6, nodeInfo.getPublicHost());
                    statement.setString(7, nodeInfo.getClientAddress().getHostName());
                    statement.setBytes(8, nodeInfo.getPublicKey().pack());

                    if(statement.getParameterMetaData().getParameterCount() > 8) {
                        statement.setBytes(9, nodeKey.pack());
                    }

                    statement.executeUpdate();
                }
            }



        } catch (SQLException se) {
//            se.printStackTrace();
            throw new Failure("config save failed:" + se);
        }
    }

    @Override
    public Object[] loadConfig() {

        try {
            Object[] result = new Object[3];
            result[0] = null;
            result[2] = null;
            ArrayList<NodeInfo> nodeInfos = new ArrayList<>();
            try (
                    PooledDb db = dbPool.db();
                    ResultSet rs = db.queryRow("SELECT * FROM config;")
            ) {
                if (rs == null)
                    throw new Exception("config not found");

                do {
                    NodeInfo nodeInfo = NodeInfo.initFrom(rs);
                    nodeInfos.add(nodeInfo);
                    byte[] packedKey = rs.getBytes("private_key");
                    if(packedKey != null) {
                        result[0] = nodeInfo;
                        result[2] = new PrivateKey(packedKey);
                    }
                } while (rs.next());

                if (nodeInfos.isEmpty())
                    throw new Exception("config not found");


                result[1] = new NetConfig(nodeInfos);
                return result;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    @Override
    public void addNode(NodeInfo nodeInfo) {
        try (PooledDb db = dbPool.db()) {

            String sqlText = "insert into config(http_client_port,http_server_port,udp_server_port, node_number, node_name, public_host,host,public_key) values(?,?,?,?,?,?,?,?);";

            try (
                    PreparedStatement statement = db.statementReturningKeys(sqlText)
            ) {
                statement.setInt(1, nodeInfo.getClientAddress().getPort());
                statement.setInt(2, nodeInfo.getServerAddress().getPort());
                statement.setInt(3, nodeInfo.getNodeAddress().getPort());
                statement.setInt(4, nodeInfo.getNumber());
                statement.setString(5, nodeInfo.getName());
                statement.setString(6, nodeInfo.getPublicHost());
                statement.setString(7, nodeInfo.getClientAddress().getHostName());
                statement.setBytes(8, nodeInfo.getPublicKey().pack());

                statement.executeUpdate();
            }

        } catch (SQLException se) {
            throw new Failure("add node failed:" + se);
        }
    }

    @Override
    public void removeNode(NodeInfo nodeInfo) {
        try (PooledDb db = dbPool.db()) {

            String sqlText = "delete from config where node_number = ?;";

            try (
                    PreparedStatement statement = db.statementReturningKeys(sqlText, nodeInfo.getNumber())
            ) {
                statement.executeUpdate();
            }

        } catch (SQLException se) {
            throw new Failure("remove node failed:" + se);
        }
    }
}
