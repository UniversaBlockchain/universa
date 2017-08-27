package com.icodici.db;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

public class DbPool implements AutoCloseable {

    private final String connectionString;
    private final Properties properties;
    private final int maximumConnections;
    private int total = 0;
    private ThreadLocal<PooledDb> threadDb = new ThreadLocal<>();

    @Override
    public void close() throws Exception {
        while( !pool.isEmpty())
            pool.take().close();
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
            PooledDb db = threadDb.get();
            // One thread - one connection, e.g. transactions work with the same db and
            // all other calls in the same thread use same pooled instance
            if( db != null )
                return db;

            PooledDb pdb = total >= maximumConnections ? pool.take() : pool.poll();
            if( pdb != null ) {
//                System.out.println("take " + pdb + " pool " + this + " left " + pool.maximumConnections);
            }
            else {
                pdb = new PooledDb(this, connectionString, properties);
                total++;
//                System.out.println("new  " + pdb + " pool " + this + " left " + pool.size()+" total "+total);
            }
            threadDb.set(pdb);
            return pdb;
        } catch (InterruptedException e) {
            throw new SQLException("Pooled operation interrupted");
        }
    }

    void returnToPool(PooledDb db) {
        threadDb.set(null);
        pool.add(db);
    }


    public <T> T execute(DbConsumer<T> consumer) throws Exception {
        try(PooledDb pdb = db()) { return consumer.accept(pdb); }
    }

    public void execute(VoidDbConsumer consumer) throws Exception {
        try(PooledDb pdb = db()) { consumer.accept(pdb); }
    }
}
