package com.icodici.db;

import java.sql.SQLException;
import java.util.Properties;

public class PooledDb extends Db implements AutoCloseable {
    private DbPool dbPool;

    public PooledDb(DbPool dbPool, String connectionString, Properties properties) throws SQLException {
        super(connectionString, properties);
        this.dbPool = dbPool;
    }

    @Override
    public void close() {
        // important! do NOT call super.close() - we do not close pooled connections!
        dbPool.returnToPool(this);
//            System.out.println("back "+this+" pool " + DbPool.this + " left " + pool.maximumConnections);
    }
}
