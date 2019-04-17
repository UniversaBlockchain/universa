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
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node2.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Ut;

import java.lang.ref.WeakReference;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

    @Override
    public byte[] getKeepingItem(HashId itemId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("select * from kept_items, ledger where ledger.hash = ? and ledger.id = kept_items.ledger_id limit 1", itemId.getDigest()))) {
                if (rs == null)
                    return null;
                return rs.getBytes("packed");
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    @Deprecated
    public Object getKeepingByOrigin(HashId origin, int limit) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow(
                    "select kept_items.packed, kept_items.hash from kept_items, ledger where ledger.id = kept_items.ledger_id and origin = ? and state = ? order by kept_items.id desc limit ?",
                    origin.getDigest(), ItemState.APPROVED.ordinal(), limit))) {
                if (rs == null)
                    return null;

                byte[] packed = rs.getBytes("packed");
                List<byte[]> contractIds = new ArrayList<>();
                contractIds.add(rs.getBytes("hash"));

                while (rs.next())
                    contractIds.add(rs.getBytes("hash"));

                if (contractIds.size() > 1)
                    return contractIds;
                else
                    return packed;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public Binder getKeepingBy(String field, HashId id, Binder tags, int limit, int offset, String sortBy, String sortOrder) {
        String searchColumn;
        if(field == null) {
            searchColumn = null;
        } else if(field.equals("state.origin")) {
            searchColumn = "kept_items.origin";
        } else if(field.equals("state.parent")) {
            searchColumn = "kept_items.parent";
        } else {
            throw new IllegalArgumentException("Can't get contracts by '" + field +"'. Should be either state.origin or state.parent");
        }

        String orderColumn;
        if(sortBy.equals("")) {
            orderColumn = "ledger.id";
        } else  if(sortBy.equals("state.createdAt")) {
            orderColumn = "ledger.created_at";
        } else if(sortBy.equals("state.expiresAt")) {
            orderColumn = "ledger.expires_at";
        } else {
            throw new IllegalArgumentException("Can't order contracts by '" + sortBy +"'. Should be either state.createdAt or state.expiresAt");
        }

        if(!sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("Invalid sort order: '" + sortOrder +"'. Should be either ASC or DESC");
        }


        final StringBuilder query = new StringBuilder("");

        List<String> tagsFlat = new ArrayList<>();

        if(tags != null && !tags.isEmpty()) {
            String tagsQuery = extractTags(true, tags, tagsFlat);

            query.append("WITH tagged_ledger_items AS ( SELECT ledger.id AS ledger_id FROM ledger ");

            for(int i = 0; i < tagsFlat.size()/2; i++) {
                query.append("INNER JOIN kept_items_tags AS tags_"+i+" ON tags_"+i+".ledger_id = ledger.id ");
            }

            query.append("WHERE ");
            query.append(tagsQuery);
            query.append(")");
        }

        query.append("select kept_items.packed, ledger.hash from kept_items, ledger ");
        query.append("WHERE ");
        if(tags != null && !tags.isEmpty()) {
            query.append("ledger.id IN (SELECT ledger_id FROM tagged_ledger_items) and ");
        }

        if(searchColumn != null) {
            query.append(searchColumn + " = ? and ");
        }

        query.append("kept_items.ledger_id = ledger.id and ledger.state = ? order by " + orderColumn + " " + sortOrder + "  limit ? offset ?");

//        select keeping_items.packed, keeping_items.hash from keeping_items, ledger where ledger.hash = keeping_items.hash and " + searchColumn + " = ? and ledger.state = ? order by " + orderColumn + " " + sortOrder + "  limit ? offset ?";




        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    query.toString()
                            )
            ) {
                int idx = 1;

                for(int i = 0; i < tagsFlat.size(); i++) {
                    statement.setString(idx,tagsFlat.get(i));
                    idx++;
                }

                if(searchColumn != null) {
                    statement.setBytes(idx,id.getDigest());
                    idx++;
                }

                statement.setInt(idx,ItemState.APPROVED.ordinal());
                idx++;

                statement.setInt(idx,limit);
                idx++;
                statement.setInt(idx,offset);


                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    return null;
                if(!rs.next())
                    return null;

                byte[] packed = rs.getBytes("packed");
                List<byte[]> contractIds = new ArrayList<>();
                contractIds.add(rs.getBytes("hash"));

                while (rs.next())
                    contractIds.add(rs.getBytes("hash"));

                Binder res = Binder.of("contractIds", contractIds);
                if (contractIds.size() == 1)
                    res.put("packedContract",packed);
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getContractSubscriptions failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("getContractSubscriptions failed: " + e);
        }
    }

    private String extractTags(boolean all, Binder tags, List<String> tagsFlat) {
        StringBuilder result = new StringBuilder("(");
        tags.forEach((k,v)-> {
            if(v instanceof String) {
                int i = tagsFlat.size()/2;
                result.append((result.length() > 1 ? (all ? " and " : " or ") : ""));
                result.append("(tags_"+i+".tag = ? and tags_"+i+".value = ?)");
                tagsFlat.add(k);
                tagsFlat.add((String) v);
            } else if(v instanceof Binder) {
                boolean isAll;
                if(k.equalsIgnoreCase("all_of")) {
                    isAll = true;
                } else if(k.equalsIgnoreCase("any_of")) {
                    isAll = false;
                } else {
                    throw new IllegalArgumentException("Dictionary should come with either any_of or all_of key. Got: " + k);
                }
                result.append((result.length() > 1 ? (all ? " and " : " or ") : ""));
                result.append(extractTags(isAll, (Binder) v,tagsFlat));

            } else {
                throw new IllegalArgumentException("Expected either string or dictionary");
            }
        });
        result.append(")");

        return result.toString();
    }


    public final static String SEARCH_TAGS = "search_tags";

    @Override
    public void putKeepingItem(StateRecord record, Approvable item) {
        if (item instanceof Contract) {
            Contract contract = (Contract) item;
            try (PooledDb db = dbPool.db()) {
                try (
                        PreparedStatement statement =
                                db.statement(
                                        "insert into kept_items (ledger_id,origin,parent,packed) values(?,?,?,?);"
                                )
                ) {
                    statement.setLong(1, record.getRecordId());
                    statement.setBytes(2, contract.getOrigin().getDigest());

                    if(((Contract) item).getParent() != null)
                        statement.setBytes(3, contract.getParent().getDigest());
                    else
                        statement.setNull(3, Types.VARBINARY);

                    statement.setBytes(4, contract.getPackedTransaction());

                    db.updateWithStatement(statement);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }

            } catch (SQLException se) {
                se.printStackTrace();
                throw new Failure("keeping item save failed:" + se);
            } catch (Exception e) {
                e.printStackTrace();
            }

            Map<String,String> tagsToSave = new HashMap();
            if(contract.getDefinition().getData().containsKey(SEARCH_TAGS)) {
                Object fieldValue = contract.getDefinition().getData().get(SEARCH_TAGS);
                if(fieldValue instanceof Map) {
                    ((Map)fieldValue).forEach((k,v) -> tagsToSave.put(k.toString(),v.toString()));
                }
            }
            if(tagsToSave.size() > 0) {
                try (PooledDb db = dbPool.db()) {
                    try (
                            PreparedStatement statement =
                                    db.statement(
                                            "insert into kept_items_tags (ledger_id,tag,value) values(?,?,?);"
                                    )
                    ) {
                        int insertCount = 0;

                        for (String key : tagsToSave.keySet()) {
                            statement.setLong(1, record.getRecordId());
                            statement.setString(2, key);
                            statement.setString(3, tagsToSave.get(key));
                            statement.addBatch();
                            insertCount++;

                            if (insertCount % 100 == 0 || insertCount == tagsToSave.size()) {
                                statement.executeBatch(); // Execute every 1000 items.
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }

                } catch (SQLException se) {
                    se.printStackTrace();
                    throw new Failure("keeping item tags save failed:" + se);
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
                    statement.setLong(3, Ut.unixTime(stateRecord.getCreatedAt()));
                    statement.setLong(4, Ut.unixTime(stateRecord.getExpiresAt()));
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
                        Ut.unixTime(stateRecord.getExpiresAt()),
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

    public void cleanup(boolean isPermanetMode) {
        try (PooledDb db = dbPool.db()) {

            long now = Instant.now().getEpochSecond();
            String sqlText = "delete from items where id in (select id from ledger where expires_at < ?);";
            db.update(sqlText, now);

            if (!isPermanetMode) {
                sqlText = "delete from ledger where expires_at < ?;";
                db.update(sqlText, now);
            }

            sqlText = "delete from items where keepTill < ?;";
            db.update(sqlText, now);

            sqlText = "delete from follower_callbacks where stored_until < ?;";
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

    /*@Override
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
    }*/



    private List<byte[]> getSmartContractForEnvironmentId(long environmentId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT transaction_pack, kv_storage, ncontract_hash_id FROM environments " +
                    "WHERE id=?", environmentId))) {
                if (rs == null)
                    return null;
                List<byte[]> res = new ArrayList<>();
                res.add(rs.getBytes(1));
                res.add(rs.getBytes(2));
                res.add(rs.getBytes(3));
                return res;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }


    private Collection<ContractSubscription> getContractSubscriptions(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "SELECT hash_id, subscription_on_chain, expires_at, id FROM contract_subscription WHERE environment_id = ?"
                    )
            ) {
                statement.setLong(1, environmentId);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getContractSubscriptions failed: returning null");
                List<ContractSubscription> res = new ArrayList<>();
                while (rs.next()) {
                    NContractSubscription css = new NContractSubscription(HashId.withDigest(rs.getBytes(1)),
                            rs.getBoolean(2), Ut.getTime(rs.getLong(3)));
                    css.setId(rs.getLong(4));
                    res.add(css);
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getContractSubscriptions failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("getContractSubscriptions failed: " + e);
        }
    }


    private Collection<ContractStorage> getContractStorages(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "SELECT bin_data, expires_at, id FROM contract_storage JOIN contract_binary " +
                        "ON contract_binary.hash_id = contract_storage.hash_id WHERE environment_id = ?"
                    )
            ) {
                statement.setLong(1, environmentId);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getContractStorages failed: returning null");
                List<ContractStorage> res = new ArrayList<>();
                while (rs.next()) {
                    NContractStorage cst = new NContractStorage(rs.getBytes(1), Ut.getTime(rs.getLong(2)));
                    cst.setId(rs.getLong(3));
                    res.add(cst);
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getContractStorages failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("getContractStorages failed: " + e);
        }
    }


    private List<String> getReducedNames(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "" +
                                            "SELECT DISTINCT name_storage.name_reduced AS name_reduced " +
                                            "FROM name_storage JOIN name_entry ON name_storage.id=name_entry.name_storage_id " +
                                            "WHERE name_storage.environment_id=?"
                            )
            ) {
                statement.setLong(1, environmentId);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getNames failed: returning null");
                List<String> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(rs.getString(1));
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getNames failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("getNames failed: " + e);
        }
    }


    private FollowerService getFollowerService(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "SELECT expires_at, muted_at, spent_for_callbacks, started_callbacks FROM follower_environments WHERE environment_id = ?"
                )
            ) {
                statement.setLong(1, environmentId);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getFollowerService failed: returning null");

                NFollowerService fs = null;
                if (rs.next())
                    fs = new NFollowerService(this,
                        Ut.getTime(rs.getLong(1)),
                        Ut.getTime(rs.getLong(2)),
                        environmentId,
                        rs.getDouble(3),
                        rs.getInt(4));
                rs.close();
                return fs;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getFollowerSubscriptions failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("getFollowerSubscriptions failed: " + e);
        }
    }


    private Long getEnvironmentIdForSmartContractHashId(HashId smartContractHashId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT id FROM environments " +
                    "WHERE ncontract_hash_id=?", smartContractHashId.getDigest()))) {
                if (rs == null)
                    return null;
                return rs.getLong(1);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }


    @Override
    public NImmutableEnvironment getEnvironment(long environmentId) {
        return protect(() -> {
            List<byte[]> smkv = getSmartContractForEnvironmentId(environmentId);
            HashId nContractHashId = HashId.withDigest(smkv.get(2));
            Contract contract = NSmartContract.fromPackedTransaction(smkv.get(0));
            Contract findNContract = contract.getTransactionPack().getSubItem(nContractHashId);
            contract = findNContract == null ? contract : findNContract;
            NSmartContract nSmartContract = (NSmartContract) contract;
            Binder kvBinder = Boss.unpack(smkv.get(1));
            Collection<ContractSubscription> contractSubscriptions = getContractSubscriptions(environmentId);
            Collection<ContractStorage> contractStorages = getContractStorages(environmentId);
            FollowerService followerService = getFollowerService(environmentId);
            List<String> reducedNames = getReducedNames(environmentId);
            List<NameRecord> nameRecords = new ArrayList<>();
            for (String reducedName : reducedNames) {
                NNameRecord nr = getNameRecord(reducedName);
                //nr.setId(nrModel.id);
                nameRecords.add(nr);
            }
            NImmutableEnvironment nImmutableEnvironment = new NImmutableEnvironment(nSmartContract, kvBinder,
                    contractSubscriptions, contractStorages, nameRecords, followerService, this);
            nImmutableEnvironment.setId(environmentId);
            return nImmutableEnvironment;
        });
    }

    @Override
    public NImmutableEnvironment getEnvironment(HashId contractId) {
        Long envId = getEnvironmentIdForSmartContractHashId(contractId);
        if (envId != null)
            return getEnvironment(envId);
        return null;
    }

    @Override
    public NImmutableEnvironment getEnvironment(NSmartContract smartContract) {
        NImmutableEnvironment nim = getEnvironment(smartContract.getId());

        if(nim == null && smartContract.getParent() != null)
            nim = getEnvironment(smartContract.getParent());

        if (nim == null) {
            long envId = saveEnvironmentToStorage(smartContract.getExtendedType(), smartContract.getId(), Boss.pack(new Binder()), smartContract.getPackedTransaction());
            nim = getEnvironment(envId);
        } else {
            nim.setContract(smartContract);
        }
        return nim;
    }

    @Override
    public void updateSubscriptionInStorage(long subscriptionId, ZonedDateTime expiresAt) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "UPDATE contract_subscription SET expires_at = ? WHERE id = ?"
                            )
            ) {
                statement.setLong(1, Ut.unixTime(expiresAt));
                statement.setLong(2, subscriptionId);
                statement.closeOnCompletion();
                statement.executeUpdate();
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("updateSubscriptionInStorage failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("updateSubscriptionInStorage failed: " + e);
        }
    }

    @Override
    public void updateStorageExpiresAt(long storageId, ZonedDateTime expiresAt) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "UPDATE contract_storage SET expires_at = ? WHERE id = ?"
                            )
            ) {
                statement.setLong(1, Ut.unixTime(expiresAt));
                statement.setLong(2, storageId);
                statement.closeOnCompletion();
                statement.executeUpdate();
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("updateStorageExpiresAt failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("updateStorageExpiresAt failed: " + e);
        }
    }

    @Override
    public void saveFollowerEnvironment(long environmentId, ZonedDateTime expiresAt, ZonedDateTime mutedAt, double spent, int startedCallbacks) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "INSERT INTO follower_environments (environment_id, expires_at, muted_at, spent_for_callbacks, started_callbacks) " +
                        "VALUES (?,?,?,?,?) ON CONFLICT (environment_id) DO UPDATE SET expires_at = EXCLUDED.expires_at, " +
                        "muted_at = EXCLUDED.muted_at, spent_for_callbacks = EXCLUDED.spent_for_callbacks, started_callbacks = EXCLUDED.started_callbacks"
                    )
            ) {
                statement.setLong(1, environmentId);
                statement.setLong(2, Ut.unixTime(expiresAt));
                statement.setLong(3, Ut.unixTime(mutedAt));
                statement.setDouble(4, spent);
                statement.setInt(5, startedCallbacks);
                statement.closeOnCompletion();
                statement.executeUpdate();
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("saveFollowerEnvironment failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("saveFollowerEnvironment failed: " + e);
        }
    }

    @Override
    public void updateNameRecord(long nameRecordId, ZonedDateTime expiresAt) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "UPDATE name_storage SET expires_at = ? WHERE id = ?"
                            )
            ) {
                statement.setLong(1, Ut.unixTime(expiresAt));
                statement.setLong(2, nameRecordId);
                statement.closeOnCompletion();
                statement.executeUpdate();
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("updateNameRecord failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("updateNameRecord failed: " + e);
        }
    }

    private Set<HashId> saveEnvironment_getConflicts(NImmutableEnvironment environment) {
        HashId ownSmartContractId = environment.getContract().getId();

        List<String> namesToCheck = new ArrayList<>();
        List<HashId> originsToCheck = new ArrayList<>();
        List<String> addressesToCheck = new ArrayList<>();
        for (NameRecord nameRecord : environment.nameRecords()) {
            namesToCheck.add(nameRecord.getNameReduced());
            for (NameRecordEntry nameRecordEntry : nameRecord.getEntries()) {
                if (nameRecordEntry.getOrigin() != null)
                    originsToCheck.add(nameRecordEntry.getOrigin());
                if (nameRecordEntry.getShortAddress() != null)
                    addressesToCheck.add(nameRecordEntry.getShortAddress());
                if (nameRecordEntry.getLongAddress() != null)
                    addressesToCheck.add(nameRecordEntry.getLongAddress());
            }
        }

        String qpNames = String.join(",", Collections.nCopies(namesToCheck.size(),"?"));
        String qpOrigins = String.join(",", Collections.nCopies(originsToCheck.size(),"?"));
        String qpAddresses = String.join(",", Collections.nCopies(addressesToCheck.size(),"?"));

        String sqlNames = "(SELECT environments.ncontract_hash_id " +
                "FROM name_storage JOIN environments ON name_storage.environment_id=environments.id " +
                "WHERE name_storage.name_reduced IN ("+qpNames+") AND environments.ncontract_hash_id<>?) ";
        String sqlOrigins = "(SELECT environments.ncontract_hash_id " +
                "FROM name_entry JOIN name_storage ON name_entry.name_storage_id=name_storage.id " +
                "JOIN environments ON name_storage.environment_id=environments.id " +
                "WHERE name_entry.origin IN ("+qpOrigins+") AND environments.ncontract_hash_id<>?)";
        String sqlAddresses = "(SELECT environments.ncontract_hash_id " +
                "FROM name_entry JOIN name_storage ON name_entry.name_storage_id=name_storage.id " +
                "JOIN environments ON name_storage.environment_id=environments.id " +
                "WHERE (name_entry.short_addr IN ("+qpAddresses+") OR name_entry.long_addr IN ("+qpAddresses+")) AND environments.ncontract_hash_id<>?)";

        List<String> queries = new ArrayList<>();
        if (namesToCheck.size() > 0)
            queries.add(sqlNames);
        if (originsToCheck.size() > 0)
            queries.add(sqlOrigins);
        if (addressesToCheck.size() > 0)
            queries.add(sqlAddresses);

        if (queries.size() == 0)
            return new HashSet<>();

        String sqlQuery = String.join(" UNION ", queries);

        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(sqlQuery)
            ) {
                int i = 1;
                for (String name : namesToCheck)
                    statement.setString(i++, name);
                if (namesToCheck.size() > 0)
                    statement.setBytes(i++, ownSmartContractId.getDigest());
                for (HashId origin : originsToCheck )
                    statement.setBytes(i++, origin.getDigest());
                if (originsToCheck.size() > 0)
                    statement.setBytes(i++, ownSmartContractId.getDigest());
                for (String address : addressesToCheck)
                    statement.setString(i++, address);
                for (String address : addressesToCheck)
                    statement.setString(i++, address);
                if (addressesToCheck.size() > 0)
                    statement.setBytes(i++, ownSmartContractId.getDigest());
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getNames failed: returning null");
                Set<HashId> result = new HashSet<>();
                while (rs.next()) {
                    result.add(HashId.withDigest(rs.getBytes(1)));
                }
                rs.close();
                return result;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("saveEnvironment_getConflicts failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("saveEnvironment_getConflicts failed: " + e);
        }
    }


    @Override
    public Set<HashId> saveEnvironment(NImmutableEnvironment environment) {
        Set<HashId> conflicts = saveEnvironment_getConflicts(environment);
        if (conflicts.size() == 0) {
            NSmartContract nsc = environment.getContract();
            removeEnvironment(nsc.getId());
            long envId = saveEnvironmentToStorage(nsc.getExtendedType(), nsc.getId(), Boss.pack(environment.getMutable().getKVStore()), nsc.getPackedTransaction());

            for (NameRecord nr : environment.nameRecords()) {
                NNameRecord nnr = (NNameRecord)nr;
                nnr.setEnvironmentId(envId);
                addNameRecord(nnr);
            }

            for (ContractSubscription css : environment.subscriptions())
                saveSubscriptionInStorage(css.getHashId(), css.isChainSubscription(), css.expiresAt(), envId);

            for (ContractStorage cst : environment.storages())
                saveContractInStorage(cst.getContract().getId(), cst.getPackedContract(), cst.expiresAt(), cst.getContract().getOrigin(), envId);

            FollowerService fs = environment.getFollowerService();
            if (fs != null)
                saveFollowerEnvironment(envId, fs.expiresAt(), fs.mutedAt(), fs.getCallbacksSpent(), fs.getStartedCallbacks());
        }
        return conflicts;
    }

    @Override
    public Set<HashId> findBadReferencesOf(Set<HashId> ids) {
        try (PooledDb db = dbPool.db()) {
            String queryPart = String.join(",", Collections.nCopies(ids.size(),"?"));
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "" +
                                            "SELECT " +
                                            "  hash " +
                                            "FROM ledger " +
                                            "WHERE hash IN ("+queryPart+") AND state = " + ItemState.APPROVED.ordinal()
                            )
            ) {
                AtomicInteger idx = new AtomicInteger(1);
                ids.forEach(id -> {
                    try {
                        statement.setBytes(idx.getAndIncrement(), id.getDigest());
                    } catch (SQLException e) {
                        e.printStackTrace();
                        throw new Failure("findBadReferencesOf failed: " + e);
                    }
                });

                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("findBadReferencesOf failed: returning null");
                Set<HashId> res = new HashSet<>(ids);
                while (rs.next()) {
                    res.remove(HashId.withDigest(rs.getBytes(1)));
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("isNameRecordBusy failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("isNameRecordBusy failed: " + e);
        }
    }


    @Override
    public long saveSubscriptionInStorage(HashId hashId, boolean subscriptionOnChain, ZonedDateTime expiresAt, long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement("INSERT INTO contract_subscription (hash_id, subscription_on_chain, expires_at, environment_id) VALUES(?,?,?,?) RETURNING id")
            ) {
                statement.setBytes(1, hashId.getDigest());
                statement.setBoolean(2, subscriptionOnChain);
                statement.setLong(3, Ut.unixTime(expiresAt));
                statement.setLong(4, environmentId);
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
    public void clearExpiredSubscriptions() {
        try (PooledDb db = dbPool.db()) {
            ZonedDateTime now = ZonedDateTime.now();
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM contract_subscription WHERE expires_at < ?"
                            )
            ) {
                statement.setLong(1, Ut.unixTime(now));
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("clearExpiredSubscriptions failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void clearExpiredStorages() {
        try (PooledDb db = dbPool.db()) {
            ZonedDateTime now = ZonedDateTime.now();
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM contract_storage WHERE expires_at < ?"
                            )
            ) {
                statement.setLong(1, Ut.unixTime(now));
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("clearExpiredStorages failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void clearExpiredStorageContractBinaries() {
        //TODO: add trigger for delete expired contracts after deleting all subscriptions, and remove this function
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement = db.statement(
                    "DELETE FROM contract_binary WHERE hash_id NOT IN (SELECT hash_id FROM contract_storage GROUP BY hash_id)"
                )
            ) {
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("clearExpiredStorageContractBinaries failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void updateEnvironment(long id, String ncontractType, HashId ncontractHashId, byte[] kvStorage, byte[] transactionPack) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "UPDATE environments  SET ncontract_type = ?,ncontract_hash_id = ?,kv_storage = ?,transaction_pack = ? WHERE id = ?"
                            )
            ) {
                statement.setString(1, ncontractType);
                statement.setBytes(2, ncontractHashId.getDigest());
                statement.setBytes(3, kvStorage);
                statement.setBytes(4, transactionPack);
                statement.setLong(5, id);
                statement.closeOnCompletion();
                statement.executeUpdate();
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("updateEnvironment failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("updateEnvironment failed: " + e);
        }
    }

    private long saveEnvironmentToStorage(String ncontractType, HashId ncontractHashId, byte[] kvStorage, byte[] transactionPack) {
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
            throw new Failure("addEnvironmentToStorage failed: " + e);
        }
    }


    @Override
    public Set<Long> getSubscriptionEnviromentIds(HashId id) {
        return protect(() -> {
            HashSet<Long> environmentIds = new HashSet<>();

            try (ResultSet rs = inPool(db -> db.queryRow("SELECT environment_id FROM contract_subscription WHERE hash_id = ? GROUP BY environment_id", id.getDigest()))) {
                if (rs == null)
                    return new HashSet<>();
                do {
                    environmentIds.add(rs.getLong(1));
                } while (rs.next());

            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }

            return environmentIds;
        });
    }


    @Override
    public NCallbackService.FollowerCallbackState getFollowerCallbackStateById(HashId id) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("SELECT state FROM follower_callbacks WHERE id = ?", id.getDigest()))) {
                if (rs == null)
                    return NCallbackService.FollowerCallbackState.UNDEFINED;

                return NCallbackService.FollowerCallbackState.values()[rs.getInt(1)];
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        });
    }

    @Override
    public Collection<CallbackRecord> getFollowerCallbacksToResyncByEnvId(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            long now = ZonedDateTime.now().toEpochSecond();
            try (
                PreparedStatement statement =
                    db.statement(
                        "SELECT id, state FROM follower_callbacks WHERE environment_id = ? " +
                        "AND expires_at < ? AND (state = ? OR state = ?)"
                    )
            ) {
                statement.setLong(1, environmentId);
                statement.setLong(2, now);
                statement.setLong(3, NCallbackService.FollowerCallbackState.STARTED.ordinal());
                statement.setLong(4, NCallbackService.FollowerCallbackState.EXPIRED.ordinal());
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getFollowerCallbacksToResyncByEnvId failed: returning null");
                List<CallbackRecord> res = new ArrayList<>();
                while (rs.next()) {
                    CallbackRecord callback = new CallbackRecord(
                            HashId.withDigest(rs.getBytes(1)),
                            environmentId,
                            NCallbackService.FollowerCallbackState.values()[rs.getInt(2)]);
                    res.add(callback);
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getFollowerCallbacksToResyncByEnvId failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("getFollowerCallbacksToResyncByEnvId failed: " + e);
        }
    }

    @Override
    public Collection<CallbackRecord> getFollowerCallbacksToResync() {
        try (PooledDb db = dbPool.db()) {
            long now = ZonedDateTime.now().toEpochSecond();
            try (
                PreparedStatement statement =
                    db.statement(
                        "SELECT id, state, environment_id FROM follower_callbacks " +
                        "WHERE expires_at < ? AND (state = ? OR state = ?)"
                    )
            ) {
                statement.setLong(1, now);
                statement.setLong(2, NCallbackService.FollowerCallbackState.STARTED.ordinal());
                statement.setLong(3, NCallbackService.FollowerCallbackState.EXPIRED.ordinal());
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getFollowerCallbacksToResync failed: returning null");
                List<CallbackRecord> res = new ArrayList<>();
                while (rs.next()) {
                    CallbackRecord callback = new CallbackRecord(
                            HashId.withDigest(rs.getBytes(1)),
                            rs.getLong(3),
                            NCallbackService.FollowerCallbackState.values()[rs.getInt(2)]);
                    res.add(callback);
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getFollowerCallbacksToResync failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("getFollowerCallbacksToResync failed: " + e);
        }
    }

    @Override
    public void addFollowerCallback(HashId id, long environmentId, ZonedDateTime expiresAt, ZonedDateTime storedUntil) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "INSERT INTO follower_callbacks (id, state, environment_id, expires_at, stored_until) VALUES (?,?,?,?,?)"
                    )
            ) {
                statement.setBytes(1, id.getDigest());
                statement.setInt(2, NCallbackService.FollowerCallbackState.STARTED.ordinal());
                statement.setLong(3, environmentId);
                statement.setLong(4, Ut.unixTime(expiresAt));
                statement.setLong(5, Ut.unixTime(storedUntil));
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("follower callback save failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateFollowerCallbackState(HashId id, NCallbackService.FollowerCallbackState state) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "UPDATE follower_callbacks SET state = ? WHERE id = ?"
                    )
            ) {
                statement.setInt(1, state.ordinal());
                statement.setBytes(2, id.getDigest());
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("follower callback update failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeFollowerCallback(HashId id) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "DELETE FROM follower_callbacks WHERE id = ?"
                    )
            ) {
                statement.setBytes(1, id.getDigest());
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("follower callback delete failed:" + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] getSmartContractById(HashId smartContractId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT transaction_pack FROM environments " +
                    "WHERE ncontract_hash_id=?", smartContractId.getDigest()))) {
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
                    "SELECT bin_data FROM contract_binary " +
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
    public byte[] getContractInStorage(HashId slotId, HashId contractId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT bin_data FROM environments " +
                    "LEFT JOIN contract_storage ON environments.id=contract_storage.environment_id " +
                    "LEFT JOIN contract_binary ON contract_binary.hash_id=contract_storage.hash_id " +
                    "WHERE environments.ncontract_hash_id=? AND contract_storage.hash_id=?",
                    slotId.getDigest(), contractId.getDigest()))) {
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
    public List<byte[]> getContractsInStorageByOrigin(HashId slotId, HashId originId) {
        return protect(() -> {
            try (ResultSet rs = inPool(db -> db.queryRow("" +
                    "SELECT bin_data FROM environments " +
                    "LEFT JOIN contract_storage ON environments.id=contract_storage.environment_id " +
                    "LEFT JOIN contract_binary ON contract_binary.hash_id=contract_storage.hash_id " +
                    "WHERE environments.ncontract_hash_id=? AND contract_storage.origin=?",
                    slotId.getDigest(), originId.getDigest()))) {
                List<byte[]> res = new ArrayList<>();
                if (rs == null)
                    return res;
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
    public void removeEnvironmentSubscription(long subscriptionId) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "DELETE FROM contract_subscription WHERE id = ?"
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
            throw new Failure("removeEnvironmentSubscription failed: " + e);
        }
    }

    @Override
    public void removeEnvironmentStorage(long storageId) {
        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement(
                        "DELETE FROM contract_storage WHERE id = ?"
                    )
            ) {
                statement.setLong(1, storageId);
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeEnvironmentStorage failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("removeEnvironmentStorage failed: " + e);
        }
    }

    /*public List<Long> getEnvironmentSubscriptionsByEnvId(long environmentId) {
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
    }*/

    private long removeEnvironmentEx(HashId ncontractHashId) {
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

    @Override
    public long removeEnvironment(HashId ncontractHashId) {
        long envId = getEnvironmentId(ncontractHashId);
        removeSubscriptionsByEnvId(envId);
        removeStorageContractsByEnvId(envId);
        clearExpiredStorageContractBinaries();
        return removeEnvironmentEx(ncontractHashId);
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

    public void removeSubscriptionsByEnvId(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM contract_subscription WHERE environment_id = ?"
                            )
            ) {
                statement.setLong(1, environmentId);
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeSubscriptionsByEnvId failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("removeSubscriptionsByEnvId failed: " + e);
        }
    }


    public void removeStorageContractsByEnvId(long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM contract_storage WHERE environment_id = ?"
                            )
            ) {
                statement.setLong(1, environmentId);
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeStorageContractsByEnvId failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeExpiredStoragesAndSubscriptionsCascade() {
        clearExpiredSubscriptions();
        clearExpiredStorages();
        clearExpiredStorageContractBinaries();
    }

    private long addNameStorage(final NNameRecord nameRecord) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement("" +
                                    "INSERT INTO name_storage (name_reduced,name_full,description,url,expires_at,environment_id) " +
                                    "VALUES (?,?,?,?,?,?) " +
                                    "ON CONFLICT (name_reduced) DO UPDATE SET " +
                                    "  name_full=EXCLUDED.name_full, " +
                                    "  description=EXCLUDED.description, " +
                                    "  url=EXCLUDED.url, " +
                                    "  expires_at=EXCLUDED.expires_at, " +
                                    "  environment_id=EXCLUDED.environment_id " +
                                    "RETURNING id")
            ) {
                statement.setString(1, nameRecord.getNameReduced());
                statement.setString(2, nameRecord.getName());
                statement.setString(3, nameRecord.getDescription());
                statement.setString(4, nameRecord.getUrl());
                statement.setLong(5, Ut.unixTime(nameRecord.expiresAt()));
                statement.setLong(6, nameRecord.getEnvironmentId());
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
            throw new Failure("addNameStorage failed: " + e);
        }
    }

    private long addNameEntry(final NNameRecordEntry nameRecordEntry) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement("" +
                                    "INSERT INTO name_entry (name_storage_id,short_addr,long_addr,origin) " +
                                    "VALUES (?,?,?,?) " +
                                    "RETURNING entry_id")
            ) {
                statement.setLong(1, nameRecordEntry.getNameRecordId());
                statement.setString(2, nameRecordEntry.getShortAddress());
                statement.setString(3, nameRecordEntry.getLongAddress());
                statement.setBytes(4, nameRecordEntry.getOrigin()==null ? null : nameRecordEntry.getOrigin().getDigest());
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
            throw new Failure("addNameEntry failed: " + e);
        }
    }

    @Override
    public void addNameRecord(final NNameRecord nameRecord) {
        long nameStorageId = addNameStorage(nameRecord);
        if (nameStorageId != 0) {
            nameRecord.setId(nameStorageId);
            removeNameRecordEntries(nameStorageId);
            for (NameRecordEntry nameRecordEntry : nameRecord.getEntries()) {
                ((NNameRecordEntry) nameRecordEntry).setNameRecordId(nameStorageId);
                addNameEntry((NNameRecordEntry) nameRecordEntry);
            }
        } else {
            throw new Failure("addNameRecord failed");
        }
    }


    @Override
    public long saveContractInStorage(HashId contractId, byte[] binData, ZonedDateTime expiresAt, HashId origin, long environmentId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "INSERT INTO contract_binary (hash_id, bin_data) VALUES (?,?) " +
                                    "ON CONFLICT (hash_id) DO UPDATE SET bin_data=EXCLUDED.bin_data"
                            )
            ) {
                statement.setBytes(1, contractId.getDigest());
                statement.setBytes(2, binData);
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("saveContractInStorage binary failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }

        try (PooledDb db = dbPool.db()) {
            try (
                PreparedStatement statement =
                    db.statement("INSERT INTO contract_storage (hash_id, origin, expires_at, environment_id) VALUES (?,?,?,?) RETURNING id")
            ) {
                statement.setBytes(1, contractId.getDigest());
                statement.setBytes(2, origin.getDigest());
                statement.setLong(3, Ut.unixTime(expiresAt));
                statement.setLong(4, environmentId);
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
            throw new Failure("removeNameRecord failed: " + e);
        }
    }

    private void removeNameRecordEntries(final long nameStorageId) {
        try (PooledDb db = dbPool.db()) {
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM name_entry WHERE name_storage_id=?"
                            )
            ) {
                statement.setLong(1, nameStorageId);
                db.updateWithStatement(statement);
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("removeNameRecordEntries failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("removeNameRecordEntries failed: " + e);
        }
    }


    @Override
    public List<String> isAllNameRecordsAvailable(final Collection<String> reducedNames) {

        try (PooledDb db = dbPool.db()) {
            String queryPart = String.join(",", Collections.nCopies(reducedNames.size(),"?"));
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "" +
                                            "SELECT " +
                                            "  name_reduced " +
                                            "FROM name_storage " +
                                            "WHERE name_reduced IN ("+queryPart+")"
                            )
            ) {
                AtomicInteger idx = new AtomicInteger(1);
                reducedNames.forEach(s -> {
                    try {
                        statement.setString(idx.getAndIncrement(), s);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        throw new Failure("isNameRecordBusy failed: " + e);
                    }
                });

                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("isNameRecordBusy failed: returning null");
                List<String> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(rs.getString(1));
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("isNameRecordBusy failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("isNameRecordBusy failed: " + e);
        }

    }


    @Override
    public List<String> isAllOriginsAvailable(final Collection<HashId> origins) {
        try (PooledDb db = dbPool.db()) {
            String queryPart = String.join(",", Collections.nCopies(origins.size(),"?"));
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "" +
                                            "SELECT " +
                                            "  origin " +
                                            "FROM name_entry " +
                                            "WHERE origin IN ("+queryPart+")"
                            )
            ) {
                AtomicInteger idx = new AtomicInteger(1);
                origins.forEach(o -> {
                    try {
                        statement.setBytes(idx.getAndIncrement(), o.getDigest());
                    } catch (SQLException e) {
                        e.printStackTrace();
                        throw new Failure("isAllOriginsAvailable failed: " + e);
                    }
                });

                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("isAllOriginsAvailable failed: returning null");
                List<String> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(HashId.withDigest(rs.getBytes(1)).toBase64String());
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("isAllOriginsAvailable failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("isAllOriginsAvailable failed: " + e);
        }
    }


    @Override
    public List<String> isAllAddressesAvailable(final Collection<String> addresses) {
        try (PooledDb db = dbPool.db()) {
            String queryPart = String.join(",", Collections.nCopies(addresses.size(),"?"));
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "" +
                                            "SELECT " +
                                            "  short_addr, " +
                                            "  long_addr " +
                                            "FROM name_entry " +
                                            "WHERE " +
                                            "  short_addr IN ("+queryPart+") " +
                                            "OR " +
                                            "  long_addr IN ("+queryPart+") "
                            )
            ) {
                AtomicInteger idx = new AtomicInteger(1);
                addresses.forEach(s -> {
                    try {
                        statement.setString(idx.get(), s);
                        statement.setString(idx.getAndIncrement()+addresses.size(), s);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        throw new Failure("isAllAddressesAvailable failed: " + e);
                    }
                });
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("isAllAddressesAvailable failed: returning null");
                List<String> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(rs.getString(1));
                    res.add(rs.getString(2));
                }
                rs.close();
                return res;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("isAllAddressesAvailable failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("isAllAddressesAvailable failed: " + e);
        }
    }



    public void clearExpiredNameRecords(Duration holdDuration) {
        try (PooledDb db = dbPool.db()) {
            ZonedDateTime before = ZonedDateTime.now().minus(holdDuration);
            try (
                    PreparedStatement statement =
                            db.statement(
                                    "DELETE FROM name_storage WHERE expires_at < ? "
                            )
            ) {
                statement.setLong(1, Ut.unixTime(before));
                statement.closeOnCompletion();
                statement.executeUpdate();
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("clearExpiredNameRecords failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("clearExpiredNameRecords failed: " + e);
        }
    }



    private NNameRecord getNameBy (String whereQueryPard, Consumer<PreparedStatement> paramsFromLambda) {
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
                                            whereQueryPard
                            )
            ) {
                paramsFromLambda.accept(statement);
                statement.closeOnCompletion();
                ResultSet rs = statement.executeQuery();
                if (rs == null)
                    throw new Failure("getNameBy failed: returning null");

                UnsName unsName = new UnsName();
                long nameRecord_id = 0;
                ZonedDateTime nameRecord_expiresAt = ZonedDateTime.now();
                long nameRecord_environmentId = 0;
                Set<NNameRecordEntry> entries = new HashSet<>();
                boolean firstRow = true;
                int rowsCount = 0;
                while (rs.next()) {
                    ++rowsCount;
                    if (firstRow) {
                        nameRecord_id = rs.getLong("id");
                        unsName.setUnsReducedName(rs.getString("name_reduced"));
                        unsName.setUnsName(rs.getString("name_full"));
                        unsName.setUnsDescription(rs.getString("description"));
                        unsName.setUnsURL(rs.getString("url"));
                        nameRecord_expiresAt = Ut.getTime(rs.getLong("expires_at"));
                        nameRecord_environmentId = rs.getLong("environment_id");
                        firstRow = false;
                    }
                    long entry_id = rs.getLong("entry_id");
                    String short_addr = rs.getString("short_addr");
                    String long_addr = rs.getString("long_addr");
                    byte[] origin = rs.getBytes("origin");
                    NNameRecordEntry nameRecordEntry = new NNameRecordEntry(HashId.withDigest(origin), short_addr, long_addr);
                    nameRecordEntry.setId(entry_id);
                    nameRecordEntry.setNameRecordId(nameRecord_id);
                    entries.add(nameRecordEntry);
                }
                NNameRecord nameRecord = new NNameRecord(unsName, nameRecord_expiresAt, entries, nameRecord_id, nameRecord_environmentId);
                if (rowsCount == 0)
                    nameRecord = null;
                rs.close();
                return nameRecord;
            }
        } catch (SQLException se) {
            se.printStackTrace();
            throw new Failure("getNameBy failed: " + se);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Failure("getNameBy failed: " + e);
        }
    }



    @Override
    public NNameRecord getNameRecord(final String nameReduced) {
        return getNameBy("WHERE name_storage.name_reduced=? ", (statement)-> {
            try {
                statement.setString(1, nameReduced);
            } catch (SQLException e) {
                throw new Failure("getNameRecord failed: " + e);
            }
        });
    }


    @Override
    public NNameRecord getNameByAddress (String address) {
        return getNameBy("WHERE name_storage.id=(SELECT name_storage_id FROM name_entry WHERE short_addr=? OR long_addr=? LIMIT 1) ", (statement)-> {
            try {
                statement.setString(1, address);
                statement.setString(2, address);
            } catch (SQLException e) {
                throw new Failure("getNameByAddress failed: " + e);
            }
        });
    }


    @Override
    public NNameRecord getNameByOrigin (byte[] origin) {
        return getNameBy("WHERE name_storage.id=(SELECT name_storage_id FROM name_entry WHERE origin=?) ", (statement)-> {
            try {
                statement.setBytes(1, origin);
            } catch (SQLException e) {
                throw new Failure("getNameByOrigin failed: " + e);
            }
        });
    }

}
