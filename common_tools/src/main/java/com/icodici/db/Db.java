/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.db;

import net.sergeych.utils.LogPrinter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.*;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * Database "smart connection" tool. Implements automatic statement caching, migrations, key-value parameters in
 * database, accurate {@link #close()} logic with right support for finalize() and some more neat featires sadly missing
 * in the {@link Connection}.
 * <p>
 * It is advised to have separate {@link Db} instances in in different threads using {@link #instance()}.
 * <p>
 * This is simplfied and enhanced for better performance version of Db class used in other iCodici projects, it does not
 * rely on Record/Table infrastructure anymore so could run faster with plain cached SQL prepared statements. We
 * recommend it where performance is of essense.
 * <p>
 * Created by sergeych on 20.03.16.
 */
public class Db implements Cloneable, AutoCloseable {

    private boolean walMode = false;
    private boolean sqlite = false;
    protected String connectionString;
    private int currentDbVersion = 0;

    private Connection connection;

    public Properties getProperties() {
        return properties;
    }

    protected Properties properties = null;
    private static LogPrinter log = new LogPrinter("Db");

    public void close() {
        synchronized (connectionString) {
            if (connection != null) {
                for (PreparedStatement s : cachedStatements.values()) {
                    try {
                        s.close();
                    } catch (SQLException e) {
                        // connection is closed, we ignore it
//                        log.d("failure closing statemment: " + e);
                    }
                }
                cachedStatements.clear();
                try {
                    connection.close();
                } catch (SQLException e) {
                    log.e("Error closing: " + e);
                    e.printStackTrace();
                }
                connection = null;
            }
        }
    }

    public boolean isClosed() {
        return connection == null;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    public String getConnectionString() {
        return connectionString;
    }

    public Integer getIntParam(String name) throws SQLException {
        return queryOne("SELECT ivalue FROM vars WHERE name=?", name);
    }

    public String getStringParam(String name) throws SQLException {
        return queryOne("SELECT svalue FROM vars WHERE name=?", name);
    }

    public int getIntParam(String name, int defaultValue) throws SQLException {
        Integer x = getIntParam(name);
        return x == null ? defaultValue : x;
    }

    public void setIntParam(String name, int value) throws SQLException {
        update("UPDATE vars SET ivalue=? WHERE name=?", value, name);
        update("INSERT OR IGNORE INTO vars (name, ivalue) VALUES (?, ?); ", name, value);
    }

    public String getStringParam(String name, String defaultValue) throws SQLException {
        String result = getStringParam(name);
        return result == null ? defaultValue : result;
    }

    public void setStringParam(String name, String value) throws SQLException {
        update("UPDATE vars SET svalue=? WHERE name=?", value, name);
        update("INSERT OR IGNORE INTO vars (name, svalue) VALUES (?, ?); ", name, value);
    }

//    public void setIntParam()

    public class Error extends Exception {
        public Error(String text) {
            super(text);
        }

        public Error(String text, Throwable reason) {
            super(text, reason);
        }
    }

    public Db(String connectionString) throws SQLException {
        this(connectionString, null);
    }

    public Db(String connectionString, Properties properties) throws SQLException {
        this(connectionString, properties, null);
    }

    public Db(String connectionString, Properties properties, String migrationsResource) throws SQLException {
        this.connectionString = connectionString;
        if (properties != null)
            connection = DriverManager.getConnection(connectionString, properties);
        else
            connection = DriverManager.getConnection(connectionString);
        this.properties = properties;
//        System.out.println("creating db instance for " + Thread.currentThread().getId());
        this.walMode = true;
        connection.setAutoCommit(true);
        if (connectionString.contains("sqlite")) {
            sqlite = true;
            queryOne("PRAGMA journal_mode=WAL");
            update("PRAGMA synchronous=OFF");
            try {
                update("PRAGMA mmap_size=268435456");
            } catch (java.sql.SQLException e) {
                queryOne("PRAGMA mmap_size=268435456");
            }
        }
        if (migrationsResource != null)
            setupDatabase(migrationsResource);
    }

    public Db clone() {
        try {
            return new Db(connectionString, properties);
        } catch (SQLException e) {
            throw new RuntimeException("failed to clone Db", e);
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Db && connectionString.equals(((Db) obj).connectionString);
    }

    @Override
    public int hashCode() {
        return connectionString.hashCode();
    }

    public void clearDB() {
        // TODO: DROP ALL TABLES - how?
    }


    public <T> T transaction(Callable<T> worker) throws Exception {
        connection.setAutoCommit(false);
        try {
            T result = worker.call();
            connection.commit();
            return result;
        } catch (RollbackException e) {
            connection.rollback();
        } catch (Exception e) {
            log.e("Exception in transaction: %s", e);
            connection.rollback();
            throw (e);
        } finally {
            connection.setAutoCommit(true);
        }
        return null;
    }

    static public class RollbackException extends Exception {
    }

    private int myVersion = 0;

    private void preMigrate(int version) {

    }

    private void postMigrate(int version) {

    }

    public void createDB(final String migrationResource) {
        if (migrationResource == null)
            return;
        try {
            myVersion = 0;
            try {
                Integer v = queryOne("SELECT ivalue FROM vars WHERE name = 'version'");
                if (v != null)
                    myVersion = v;
            } catch (SQLException e) {
                if (e.getMessage().indexOf("vars") < 0)
                    e.printStackTrace();
            }
            currentDbVersion = detectMaxMigrationVersion(migrationResource);
            log.d("My db version is %d current is %d", myVersion, currentDbVersion);
            while (myVersion < currentDbVersion) transaction(() -> {
                log.d("Migrating to %d", myVersion + 1);
                preMigrate(myVersion);
                executeFile(migrationResource + myVersion + ".sql");
                postMigrate(myVersion);
                myVersion++;
                update("update vars set ivalue=? where name='version'", myVersion);
                return null;
            });
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("migrations failed", e);
        }
        log.d("Db migrated successfully");
    }

    private int detectMaxMigrationVersion(String migrationResource) {
        if (migrationResource == null || migrationResource.length() == 0)
            return 0;
        int i = 0;
        InputStream is;
        while ((is = getClass().getResourceAsStream(migrationResource + i + ".sql")) != null) {
            i++;
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return i;
    }

    public void setupDatabase(String migrationsResource) {
        createDB(migrationsResource);
    }

    private HashMap<String, PreparedStatement> cachedStatements = new HashMap<>();

    public PreparedStatement statement(String sqlText, Object... args) throws SQLException {
//        log.d("statement: |" + sqlText + "|  " + Arrays.toString(args));
//        System.out.println("statement: |" + sqlText + "|  " + Arrays.toString(args));
//        PreparedStatement statement = cachedStatements.get(sqlText);
        PreparedStatement statement = null;
//        if (statement == null) {
        statement = connection.prepareStatement(sqlText);
//            cachedStatements.put(sqlText, statement);
//        } else {
//            statement.clearParameters();
//        }
        int index = 1;
        for (Object arg : args) {
            statement.setObject(index, arg);
            index++;
        }
        return statement;
    }

    public PreparedStatement statementReturningKeys(String sqlText, Object... args) throws SQLException {
        PreparedStatement statement = null;
        statement = connection.prepareStatement(sqlText, Statement.RETURN_GENERATED_KEYS);
        int index = 1;
        for (Object arg : args) {
            statement.setObject(index, arg);
            index++;
        }
        return statement;
    }

    public ResultSet queryRow(String sqlText, Object... args) throws SQLException {
        PreparedStatement s = statement(sqlText, args);
        s.closeOnCompletion();
        ResultSet rs = s.executeQuery();
        if (rs.next()) {
            return rs;
        } else {
            if (sqlite)
                s.close();
            return null;
        }
    }

    /**
     * Execute sqlText using cached prepared statement and args and return its first row first column if any, nd casts
     * it to desired type.
     *
     * @param sqlText sql text string with '?' for parameters
     * @param args    query parameters
     *
     * @return retreived data or null
     *
     * @throws SQLException
     */
    public <T> T queryOne(String sqlText, Object... args) throws SQLException {
        try (PreparedStatement s = statement(sqlText, args);
             ResultSet rs = s.executeQuery();
        ) {
            if (rs.next()) {
                return (T) rs.getObject(1);
            }
        }
        return null;
    }

    private Object writeLock = new Object();

    /**
     * Perform sql update on created/cached prepared statement
     *
     * @param sqlText
     * @param args
     *
     * @throws SQLException
     */
    public void update(String sqlText, Object... args) throws SQLException {
        try (PreparedStatement s = statement(sqlText, args)) {
            s.executeUpdate();
        }
    }

    public void executeFile(String name) {
        StringBuilder sb = new StringBuilder();
        int counter = 0;

        try {
            try (Statement statement = connection.createStatement()) {
                InputStream resourceStream = getClass().getResourceAsStream(name);
                if (resourceStream == null)
                    throw new RuntimeException("failed to get migration: " + name);
                BufferedReader r = new BufferedReader(new InputStreamReader(resourceStream));
                String line;
                boolean inFunction = false;
                while ((line = r.readLine()) != null) {
                    counter += 1;
                    line = line.trim();
                    sb.append(line + "\n");
                    if (!inFunction) {
                        if (line.endsWith("$$")) {
                            inFunction = true;
                        } else {
                            if (line.endsWith(";")) {
                                statement.executeUpdate(sb.toString());
                                sb = new StringBuilder();
                                counter = 0;
                            }
                        }
                    } else {
                        if (line.endsWith("$$"))
                            inFunction = false;
                    }
                }
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            e.printStackTrace();
            log.e("Eror executing file: " + e + "\nIn line " + counter + " of:\n" + sb.toString());
            throw new RuntimeException("Failed to migrate", e);
        }
    }
}
