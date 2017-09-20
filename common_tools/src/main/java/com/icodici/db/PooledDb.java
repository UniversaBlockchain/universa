package com.icodici.db;

import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.Callable;

public class PooledDb extends Db implements AutoCloseable {
    private final DbPool dbPool;
    volatile boolean isInTransaction = false;

    private static final boolean assertionsEnabled;
    static {
        boolean assertionsEnabledTmp = false;
        assert assertionsEnabledTmp = true;
        assertionsEnabled = assertionsEnabledTmp;
    }

    public PooledDb(DbPool dbPool, String connectionString, Properties properties) throws SQLException {
        super(connectionString, properties);
        this.dbPool = dbPool;
    }

    @Override
    public <T> T transaction(Callable<T> worker) throws Exception {
        // TODO: all the “!isInTransaction”/“isInTransaction” asserts
        // may be uncommented to immediately spot if some external transaction tries to intervene
        // with this one. That will break some unit tests though.
        try {
//            assert !isInTransaction;
            if (assertionsEnabled) { isInTransaction = true; }

            final T result = super.transaction(worker);
//            assert isInTransaction;
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        } finally {
//            assert isInTransaction;
            if (assertionsEnabled) { isInTransaction = false; }
        }
    }

    @Override
    public void close() {
        // important! do NOT call super.close() - we do not close pooled connections!
        dbPool.returnToPool(this);
//            System.out.println("back "+this+" pool " + DbPool.this + " left " + pool.maximumConnections);
    }
}
