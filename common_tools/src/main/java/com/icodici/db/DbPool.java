package com.icodici.db;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

public class DbPool implements AutoCloseable {

    private final String connectionString;
    private final Properties properties;
    private final int maximumConnections;
    private volatile int total = 0;
    private int cached = 0;

    /**
     * This the per-thread cache of the DB connection.
     * TODO: should the connection be blindly taken from this cache?
     * Or only if it is not used already in some other transaction?
     */
    private ThreadLocal<PooledDb> threadDb = new ThreadLocal<>();

    @Override
    public void close() throws Exception {
        threadDb.set(null);
        while( !pool.isEmpty())
            pool.take().destroy();
    }

    public interface DbConsumer<R> {
        R accept(PooledDb db) throws Exception;
    }

    public interface VoidDbConsumer {
        void accept(PooledDb db) throws Exception;
    }

    public DbPool(String connectionString, Properties properties, int maxConnections) throws SQLException {
        this.connectionString = connectionString;
        this.properties = properties;
        this.maximumConnections = maxConnections;
    }

    private LinkedBlockingQueue<PooledDb> pool = new LinkedBlockingQueue<>();

    public PooledDb db() throws SQLException {
        try {
//            PooledDb db = threadDb.get();
            // One thread - one connection, e.g. transactions work with the same db and
            // all other calls in the same thread use same pooled instance
//            if( db != null ) {
//                // TODO: uncomment the next assert line to spot every transaction-inside-transaction.
//                // This will definitely break some unit tests until the code is change to never cause
//                // transaction-inside-transaction DB access!
//                // assert !db.isInTransaction;
//                System.out.println("get cached " + db.index + " pool " + this + " left " + pool.size() + " total " + total);
//                cached++;
//                return db;
//            }

            PooledDb pdb = total >= maximumConnections ? pool.take() : pool.poll();
            if( pdb != null ) {
//                System.out.println("take left " + pool.size() + " total " + total + " cached " + cached);
            } else {
                pdb = new PooledDb(this, connectionString, properties);
//                System.out.println("new  left " + pool.size() + " total " + total + " cached " + cached);
            }
//            threadDb.set(pdb);
            return pdb;
        } catch (InterruptedException e) {
            throw new SQLException("Pooled operation interrupted");
        } catch (Exception e) {
            throw new SQLException("Pooled operation interrupted: " + e.getMessage());
        }
    }

    void returnToPool(PooledDb db) {
//        System.out.println("return " + db.index + " left " + pool.size() + " total " + total + " cached " + cached);
//      threadDb.set(null);
        pool.add(db);
    }


    public <T> T execute(DbConsumer<T> consumer) throws Exception {
        try (PooledDb pdb = db()) { return consumer.accept(pdb); }
    }

    public void execute(VoidDbConsumer consumer) throws Exception {
        try (PooledDb pdb = db()) { consumer.accept(pdb); }
    }
}
