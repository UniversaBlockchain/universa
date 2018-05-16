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
import com.icodici.universa.contract.services.ContractStorageSubscription;
import com.icodici.universa.contract.services.SlotContractStorageSubscription;
import com.icodici.universa.node.models.NameEntryModel;
import com.icodici.universa.node.models.NameRecordModel;
import com.icodici.universa.node2.NetConfig;
import com.icodici.universa.node2.NodeInfo;

import java.lang.ref.WeakReference;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
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

    private Map<HashId, WeakReference<StateRecord>> cachedRecords = new WeakHashMap<>();
    private Map<Long, WeakReference<StateRecord>> cachedRecordsById = new WeakHashMap<>();
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
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
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

    private StateRecord getFromCacheById(long recordId) {
        if (useCache) {
            synchronized (cachedRecordsById) {
                WeakReference<StateRecord> ref = cachedRecordsById.get(recordId);
                if (ref == null)
                    return null;
                StateRecord r = ref.get();
                if (r == null) {
                    cachedRecordsById.remove(recordId);
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
            synchronized (cachedRecordsById) {
                cachedRecordsById.put(r.getRecordId(), new WeakReference<StateRecord>(r));
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
            e.printStackTrace();
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public StateRecord getLockOwnerOf(StateRecord rc) {
        StateRecord cached = getFromCacheById(rc.getLockedByRecordId());
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
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
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
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
            return record;

        });
    }

    @Override
    public Map<HashId,StateRecord> findUnfinished() {
            return protect(() -> {
                HashMap<HashId, StateRecord> map = new HashMap<>();
                try (ResultSet rs = inPool(db -> db.queryRow("select * from sr_find_unfinished()"))) {
                    if (rs != null) {
                        do {
                            StateRecord record = new StateRecord(this, rs);
                            if (record.isExpired()) {
                                record.destroy();
                            } else {
                                map.put(record.getId(), record);
                            }
                        } while (rs.next());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
                return map;

            });
    }

    @Override
    public Approvable getItem(final StateRecord record) {
            return protect(() -> {
                try (ResultSet rs = inPool(db -> db.queryRow("select * from items where id = ?", record.getRecordId()))) {
                    if (rs == null)
                        return null;
                    return Contract.fromPackedTransaction(rs.getBytes("packed"));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            });
    }

    @Override
    public void putItem(StateRecord record, Approvable item, Instant keepTill) {
        if (item instanceof Contract) {
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
                    db.updateWithStatement(statement);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            } catch (SQLException se) {
                se.printStackTrace();
                throw new Failure("item save failed:" + se);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private <T> T protect(Callable<T> block) {
        try {
            return block.call();
        } catch (Exception ex) {
            ex.printStackTrace();
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
    public Map<ItemState, Integer> getLedgerSize(ZonedDateTime createdAfter) {
            return protect(() -> {
                try (ResultSet rs = inPool(db -> db.queryRow("select count(id), state from ledger where created_at >= ? group by state", createdAfter != null ? createdAfter.toEpochSecond() : 0))) {
                    Map<ItemState, Integer> result = new HashMap<>();
                    if (rs != null) {
                        do {
                            int count = rs.getInt(1);
                            ItemState state = ItemState.values()[rs.getInt(2)];
                            result.put(state, count);

                        } while (rs.next());
                    }
                    return result;
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
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
            // as Rollback exception is instanceof Db.Rollback, it will work as supposed by default:
            // rethrow unchecked exceotions and return null on rollback.
            try (Db db = dbPool.db()) {
                return db.transaction(() -> callable.call());
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
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
            synchronized (cachedRecordsById) {
                cachedRecordsById.remove(record.getRecordId());
            }
            return null;
        });
    }

    @Override
    public void markTestRecord(HashId hash) {
        try (PooledDb db = dbPool.db()) {
                try (
                        PreparedStatement statement =
                                db.statement(
                                        "insert into ledger_testrecords(hash) values(?) on conflict do nothing;"
                                )
                ) {
                    statement.setBytes(1, hash.getDigest());
                    db.updateWithStatement(statement);
                }

        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("StateRecord markTest failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public boolean isTestnet(HashId itemId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("select exists(select 1 from ledger_testrecords where hash=?)", itemId.getDigest()))) {
                return rs.getBoolean(1);
            }
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
                    db.updateWithStatement(statement);
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next())
                            throw new RuntimeException("generated keys are not supported");
                        long id = keys.getLong(1);
                        stateRecord.setRecordId(id);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
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
            se.printStackTrace();
            throw new Failure("StateRecord save failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
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
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            cachedRecordsById.clear();
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
                db.updateWithStatement(statement);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
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

                    db.updateWithStatement(statement);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }



        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("config save failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
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
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        } catch (Exception e) {
            e.printStackTrace();
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

                db.updateWithStatement(statement);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }

        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("add node failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeNode(NodeInfo nodeInfo) {
        try (PooledDb db = dbPool.db()) {

            String sqlText = "delete from config where node_number = ?;";

            try (
                    PreparedStatement statement = db.statementReturningKeys(sqlText, nodeInfo.getNumber())
            ) {
                db.updateWithStatement(statement);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }

        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("remove node failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cleanup() {
        try (PooledDb db = dbPool.db()) {

            long now = Instant.now().getEpochSecond();
            String sqlText = "delete from items where id in (select id from ledger where expires_at < ?);";
            db.update(sqlText, now);

            sqlText = "delete from ledger where expires_at < ?;";
            db.update(sqlText, now);

            sqlText = "delete from items where keepTill < ?;";
            db.update(sqlText, now);


        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("cleanup failed:" + se);

        }
    }

    public void savePayment(int amount, ZonedDateTime date) {


        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "insert into payments_summary (amount,date) VALUES (?,?) ON CONFLICT (date) DO UPDATE SET amount = payments_summary.amount + excluded.amount"
                            )
            ) {
                statement.setInt(1, amount);
                statement.setInt(2, (int) date.truncatedTo(ChronoUnit.DAYS).toEpochSecond());
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("payment save failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Map<Integer,Integer> getPayments(ZonedDateTime fromDate) {

        try (
                PooledDb db = dbPool.db();
                ResultSet rs = db.queryRow("SELECT * FROM payments_summary where date >= ?;",fromDate.truncatedTo(ChronoUnit.DAYS).toEpochSecond())
        ) {
            Map<Integer,Integer> payments = new HashMap<>();
            if (rs == null)
                return payments;


            do {
                payments.put(rs.getInt("date"),rs.getInt("amount"));
            } while (rs.next());

            return payments;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void addContractToStorage(HashId contractId, byte[] binData, long forTimeInSecs, HashId origin) {
        try (PooledDb db = dbPool.db()) {
            ZonedDateTime expiresAt = ZonedDateTime.now().plusSeconds(forTimeInSecs);
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "WITH contract_storage AS (" +
                                           "  INSERT INTO contract_storage (hash_id,bin_data,origin,expires_at) VALUES (?,?,?,?) RETURNING contract_storage.*" +
                                           ")" +
                                           "INSERT INTO contract_subscription (contract_storage_id,expires_at)" +
                                           "SELECT contract_storage.id, ? FROM contract_storage"
                            )
            ) {
                statement.setBytes(1, contractId.getDigest());
                statement.setBytes(2, binData);
                statement.setBytes(3, origin.getDigest());
                statement.setLong(4, StateRecord.unixTime(expiresAt));
                statement.setLong(5, StateRecord.unixTime(expiresAt));
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("addContractToStorage failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public long saveContractInStorage(HashId contractId, byte[] binData, ZonedDateTime expiresAt, HashId origin) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement("" +
                            "INSERT INTO contract_storage (hash_id,bin_data,origin,expires_at) VALUES (?,?,?,?) " +
                            "ON CONFLICT(hash_id) DO UPDATE SET hash_id=EXCLUDED.hash_id " +
                            "RETURNING id")
            ) {
                statement.setBytes(1, contractId.getDigest());
                statement.setBytes(2, binData);
                statement.setBytes(3, origin.getDigest());
                statement.setLong(4, StateRecord.unixTime(expiresAt));
                //db.updateWithStatement(statement);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("saveContractInStorage failed: returning null");
                rs.next();
                long resId = rs.getLong(1);
                rs.close();
                return resId;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("saveContractInStorage failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public long saveSubscriptionInStorage(long contractStorageId, ZonedDateTime expiresAt) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement("INSERT INTO contract_subscription (contract_storage_id,expires_at) VALUES(?,?) RETURNING id")
            ) {
                statement.setLong(1, contractStorageId);
                statement.setLong(2, StateRecord.unixTime(expiresAt));
                //db.updateWithStatement(statement);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("saveSubscriptionInStorage failed: returning null");
                rs.next();
                long resId = rs.getLong(1);
                rs.close();
                return resId;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("saveSubscriptionInStorage failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    @Override
    public List<Long> clearExpiredStorageSubscriptions() {
        try (PooledDb db = dbPool.db()) {
            ZonedDateTime now = ZonedDateTime.now();
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM contract_subscription WHERE expires_at<? RETURNING contract_storage_id"
                            )
            ) {
                statement.setLong(1, StateRecord.unixTime(now));
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("clearExpiredStorageSubscriptions failed: returning null");
                List<Long> resList = new ArrayList<>();
                while (rs.next())
                    resList.add(rs.getLong(1));
                rs.close();
                return resList;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("clearExpiredStorageSubscriptions failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void clearExpiredStorageContracts() {
        //TODO: add trigger for delete expired contracts after deleting all subscriptions, and remove this function
        try (PooledDb db = dbPool.db()) {
            ZonedDateTime now = ZonedDateTime.now();
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM contract_storage WHERE id IN (SELECT contract_storage.id FROM contract_storage LEFT OUTER JOIN contract_subscription ON (contract_storage.id=contract_subscription.contract_storage_id) WHERE contract_subscription.id IS NULL)"
                            )
            ) {
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("clearExpiredStorageContracts failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public long saveEnvironmentToStorage(String ncontractType, HashId ncontractHashId, byte[] kvStorage, byte[] transactionPack) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "INSERT INTO environments (ncontract_type,ncontract_hash_id,kv_storage,transaction_pack) VALUES (?,?,?,?) " +
                                           "ON CONFLICT (ncontract_hash_id) DO UPDATE SET ncontract_type=EXCLUDED.ncontract_type, kv_storage=EXCLUDED.kv_storage, transaction_pack=EXCLUDED.transaction_pack " +
                                           "RETURNING id"
                            )
            ) {
                statement.setString(1, ncontractType);
                statement.setBytes(2, ncontractHashId.getDigest());
                statement.setBytes(3, kvStorage);
                statement.setBytes(4, transactionPack);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("addEnvironmentToStorage failed: returning null");
                rs.next();
                long resId = rs.getLong(1);
                rs.close();
                return resId;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("addEnvironmentToStorage failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public void saveEnvironmentSubscription(long subscriptionId, long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement("" +
                                    "INSERT INTO environment_subscription (subscription_id,environemtn_id) VALUES (?,?) " +
                                    "ON CONFLICT(subscription_id) DO UPDATE SET environemtn_id=EXCLUDED.environemtn_id "
                            )
            ) {
                statement.setLong(1, subscriptionId);
                statement.setLong(2, environmentId);
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("addEnvironmentToStorage failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] getEnvironmentFromStorage(HashId ncontractHashId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("SELECT kv_storage FROM environments WHERE ncontract_hash_id=?", ncontractHashId.getDigest()))) {
                if (rs == null)
                    return null;
                return rs.getBytes("kv_storage");
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public Set<byte[]> getEnvironmentsForContractId(HashId contractId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT environments.kv_storage FROM contract_storage " +
                    "LEFT JOIN contract_subscription ON contract_storage.id=contract_subscription.contract_storage_id " +
                    "LEFT JOIN environment_subscription ON contract_subscription.id=environment_subscription.subscription_id " +
                    "LEFT JOIN environments ON environment_subscription.environemtn_id=environments.id " +
                    "WHERE contract_storage.hash_id=?", contractId.getDigest()))) {
                if (rs == null)
                    return null;
                HashSet<byte[]> res = new HashSet<>();
                do {
                    res.add(rs.getBytes(1));
                } while (rs.next());
                return res;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public Set<byte[]> getEnvironmentsForSubscriptionStorageId(long subscriptionStorageId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT environments.kv_storage FROM contract_subscription " +
                    "LEFT JOIN environment_subscription ON contract_subscription.id=environment_subscription.subscription_id " +
                    "LEFT JOIN environments ON environment_subscription.environemtn_id=environments.id " +
                    "WHERE contract_subscription.id=?", subscriptionStorageId))) {
                if (rs == null)
                    return null;
                HashSet<byte[]> res = new HashSet<>();
                do {
                    res.add(rs.getBytes(1));
                } while (rs.next());
                return res;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public byte[] getSlotForSubscriptionStorageId(long subscriptionStorageId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT environments.transaction_pack FROM environment_subscription " +
                    "LEFT JOIN environments ON environment_subscription.environemtn_id=environments.id " +
                    "WHERE environment_subscription.subscription_id=?", subscriptionStorageId))) {
                if (rs == null)
                    return null;
                return rs.getBytes(1);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public Set<ContractStorageSubscription> getStorageSubscriptionsForContractId(HashId contractId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT contract_subscription.id, contract_subscription.expires_at, contract_subscription.contract_storage_id FROM contract_storage " +
                    "LEFT JOIN contract_subscription ON contract_storage.id=contract_subscription.contract_storage_id " +
                    "WHERE contract_storage.hash_id=?", contractId.getDigest()))) {
                if (rs == null)
                    return null;
                HashSet<ContractStorageSubscription> res = new HashSet<>();
                do {
                    SlotContractStorageSubscription css = new SlotContractStorageSubscription();
                    css.setId(rs.getLong(1));
                    css.setExpiresAt(StateRecord.getTime(rs.getLong(2)));
                    css.setContractStorageId(rs.getLong(3));
                    res.add(css);
                } while (rs.next());
                return res;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public byte[] getSlotContractByEnvironmentId(long environmentId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT transaction_pack FROM environments " +
                    "WHERE id=?", environmentId))) {
                if (rs == null)
                    return null;
                return rs.getBytes(1);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public byte[] getSlotContractBySlotId(HashId slotId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT transaction_pack FROM environments " +
                    "WHERE ncontract_hash_id=?", slotId.getDigest()))) {
                if (rs == null)
                    return null;
                return rs.getBytes(1);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public byte[] getContractInStorage(HashId contractId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT bin_data FROM contract_storage " +
                    "WHERE hash_id=?", contractId.getDigest()))) {
                if (rs == null)
                    return null;
                return rs.getBytes(1);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public void removeEnvironmentSubscription(long subscriptionId) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                "DELETE FROM environment_subscription WHERE subscription_id=?"
                    )
            ) {
                statement.setLong(1, subscriptionId);
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeEnvironmentSubscription failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<Long> removeEnvironmentSubscriptionsByEnvId(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM environment_subscription WHERE environemtn_id=? RETURNING subscription_id"
                            )
            ) {
                statement.setLong(1, environmentId);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("removeEnvironmentSubscriptionsByEnvId failed: returning null");
                List<Long> resList = new ArrayList<>();
                while (rs.next())
                    resList.add(rs.getLong(1));
                rs.close();
                return resList;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeEnvironmentSubscriptionsByEnvId failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Long> getEnvironmentSubscriptionsByEnvId(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "SELECT subscription_id FROM environment_subscription WHERE environemtn_id=?"
                            )
            ) {
                statement.setLong(1, environmentId);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getEnvironmentSubscriptionsByEnvId failed: returning null");
                List<Long> resList = new ArrayList<>();
                while (rs.next())
                    resList.add(rs.getLong(1));
                rs.close();
                return resList;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getEnvironmentSubscriptionsByEnvId failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public long removeEnvironment(HashId ncontractHashId) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                    "DELETE FROM environments WHERE ncontract_hash_id=? RETURNING id"
                    )
            ) {
                statement.setBytes(1, ncontractHashId.getDigest());
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("removeEnvironment failed: returning null");
                long resId = 0;
                if (rs.next())
                    resId = rs.getLong(1);
                rs.close();
                return resId;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeEnvironment failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public long getEnvironmentId(HashId ncontractHashId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "SELECT id FROM environments WHERE ncontract_hash_id=?"
                            )
            ) {
                statement.setBytes(1, ncontractHashId.getDigest());
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getEnvironmentId failed: returning null");
                long resId = 0;
                if (rs.next())
                    resId = rs.getLong(1);
                rs.close();
                return resId;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getEnvironmentId failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public List<Long> removeStorageSubscriptionsByIds(List<Long> subscriptionIds) {
        try (PooledDb db = dbPool.db()) {
            List<String> queryPatterns = new ArrayList<>();
            for (int i = 0; i < subscriptionIds.size(); ++i)
                queryPatterns.add("?");
            String queryPatternStr = String.join(",", queryPatterns);
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM contract_subscription WHERE id IN ("+queryPatternStr+") RETURNING contract_storage_id"
                            )
            ) {
                for (int i = 0; i < subscriptionIds.size(); ++i)
                    statement.setLong(i+1, subscriptionIds.get(i));
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("removeStorageSubscriptionsByIds failed: returning null");
                List<Long> resList = new ArrayList<>();
                while (rs.next())
                    resList.add(rs.getLong(1));
                rs.close();
                return resList;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeStorageSubscriptionsByIds failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void removeStorageContractsForIds(List<Long> contracts) {
        try (PooledDb db = dbPool.db()) {
            List<String> queryPatterns = new ArrayList<>();
            for (int i = 0; i < contracts.size(); ++i)
                queryPatterns.add("?");
            String queryPatternStr = String.join(",", queryPatterns);
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM contract_storage WHERE id IN ("+queryPatternStr+") AND (SELECT COUNT(*) FROM contract_subscription WHERE contract_storage_id=contract_storage.id)=0"
                            )
            ) {
                for (int i = 0; i < contracts.size(); ++i)
                    statement.setLong(i+1, contracts.get(i));
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeStorageContractsForIds failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeSlotContractWithAllSubscriptions(HashId slotHashId) {
        long environmentId = getEnvironmentId(slotHashId);
        List<Long> subscriptionIdList = getEnvironmentSubscriptionsByEnvId(environmentId);
        removeEnvironment(slotHashId);
        if (environmentId != 0) {
            if ((subscriptionIdList != null) && (subscriptionIdList.size() > 0)) {
                List<Long> contracts = removeStorageSubscriptionsByIds(subscriptionIdList);
                if ((contracts != null) && (contracts.size() > 0))
                    removeStorageContractsForIds(contracts);
            }
        }
    }

    @Override
    public void removeExpiredStorageSubscriptionsCascade() {
        List<Long> contracts = clearExpiredStorageSubscriptions();
        if ((contracts != null) && (contracts.size() > 0))
            removeStorageContractsForIds(contracts);
    }

    private long addNameStorage(final NameRecordModel nameRecordModel) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement("" +
                                    "INSERT INTO name_storage (name_reduced,name_full,description,url,expires_at,environment_id) " +
                                    "VALUES (?,?,?,?,?,?) " +
                                    "RETURNING id")
            ) {
                statement.setString(1, nameRecordModel.name_reduced);
                statement.setString(2, nameRecordModel.name_full);
                statement.setString(3, nameRecordModel.description);
                statement.setString(4, nameRecordModel.url);
                statement.setLong(5, StateRecord.unixTime(nameRecordModel.expires_at));
                statement.setLong(6, nameRecordModel.environment_id);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("addNameStorage failed: returning null");
                rs.next();
                long resId = rs.getLong(1);
                rs.close();
                return resId;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("addNameStorage failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    private long addNameEntry(final long nameStorageId, final NameEntryModel nameEntryModel) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement("" +
                                    "INSERT INTO name_entry (name_storage_id,short_addr,long_addr,origin) " +
                                    "VALUES (?,?,?,?) " +
                                    "RETURNING entry_id")
            ) {
                statement.setLong(1, nameStorageId);
                statement.setString(2, nameEntryModel.short_addr);
                statement.setString(3, nameEntryModel.long_addr);
                statement.setBytes(4, nameEntryModel.origin);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("addNameEntry failed: returning null");
                rs.next();
                long resId = rs.getLong(1);
                rs.close();
                return resId;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("addNameEntry failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public void saveNameRecord(final NameRecordModel nameRecordModel) {
        long nameStorageId = addNameStorage(nameRecordModel);
        if (nameStorageId != 0) {
            nameRecordModel.id = nameStorageId;
            for (NameEntryModel nameEntryModel : nameRecordModel.entries)
                addNameEntry(nameStorageId, nameEntryModel);
        } else {
            throw new Failure("addNameRecord failed");
        }
    }

    @Override
    public void removeNameRecord(final String nameReduced) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM name_storage WHERE name_reduced=?"
                            )
            ) {
                statement.setString(1, nameReduced);
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeNameRecord failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public NameRecordModel getNameRecord(final String nameReduced) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "" +
                                            "SELECT " +
                                            "  name_storage.id AS id, " +
                                            "  name_storage.name_reduced AS name_reduced, " +
                                            "  name_storage.name_full AS name_full, " +
                                            "  name_storage.description AS description, " +
                                            "  name_storage.url AS url, " +
                                            "  name_storage.expires_at AS expires_at, " +
                                            "  name_storage.environment_id AS environment_id, " +
                                            "  name_entry.entry_id AS entry_id, " +
                                            "  name_entry.short_addr AS short_addr, " +
                                            "  name_entry.long_addr AS long_addr, " +
                                            "  name_entry.origin AS origin " +
                                            "FROM name_storage JOIN name_entry ON name_storage.id=name_entry.name_storage_id " +
                                            "WHERE name_storage.name_reduced=?"
                            )
            ) {
                statement.setString(1, nameReduced);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getNameRecord failed: returning null");
                NameRecordModel nameRecordModel = new NameRecordModel();
                nameRecordModel.entries = new ArrayList<>();
                boolean firstRow = true;
                while (rs.next()) {
                    if (firstRow) {
                        nameRecordModel.id = rs.getLong("id");
                        nameRecordModel.name_reduced = rs.getString("name_reduced");
                        nameRecordModel.name_full = rs.getString("name_full");
                        nameRecordModel.description = rs.getString("description");
                        nameRecordModel.url = rs.getString("url");
                        nameRecordModel.expires_at = StateRecord.getTime(rs.getLong("expires_at"));
                        nameRecordModel.environment_id = rs.getLong("environment_id");
                        firstRow = false;
                    }
                    NameEntryModel nameEntryModel = new NameEntryModel();
                    nameEntryModel.name_storage_id = nameRecordModel.id;
                    nameEntryModel.entry_id = rs.getLong("entry_id");
                    nameEntryModel.short_addr = rs.getString("short_addr");
                    nameEntryModel.long_addr = rs.getString("long_addr");
                    nameEntryModel.origin = rs.getBytes("origin");
                    nameRecordModel.entries.add(nameEntryModel);
                }
                rs.close();
                return nameRecordModel;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getNameRecord failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }


    @Override
    public boolean isAllNameRecordsAvailable(final List<String> reducedNames) {
        try (PooledDb db = dbPool.db()) {
            String queryPart = "";
            List<String> queryPartList = new ArrayList<>();
            for (int i = 0; i < reducedNames.size(); ++i)
                queryPartList.add("?");
            queryPart = String.join(",", queryPartList);
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "" +
                                            "SELECT " +
                                            "  COUNT(id) " +
                                            "FROM name_storage " +
                                            "WHERE name_reduced IN ["+queryPart+"]"
                            )
            ) {
                for (int i = 1; i <= reducedNames.size(); ++i)
                    statement.setString(i, reducedNames.get(i));
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("isNameRecordBusy failed: returning null");
                boolean res = false;
                if (rs.next()) {
                    if (rs.getLong(1) == 0)
                        res = true;
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("isNameRecordBusy failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return true;
        }

    }


    public void clearExpiredNameRecords() {
        try (PooledDb db = dbPool.db()) {
            //TODO: get hold interval from config
            ZonedDateTime now = ZonedDateTime.now().minusMonths(1);
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM name_storage WHERE expires_at < ? "
                            )
            ) {
                statement.setLong(1, StateRecord.unixTime(now));
                statement.closeOnCompletion();
                statement.executeUpdate();
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("clearExpiredNameRecords failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
