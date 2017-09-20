/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node.benchmark;

import com.icodici.db.Db;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.node.PostgresLedger;
import fi.iki.elonen.NanoHTTPD;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.tools.Do;
import net.sergeych.tools.JsonTool;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

public class TPSBenchmarkServer {

    private final BufferedLogger logger;
    private final String token;

    public class Server extends NanoHTTPD {

        public Server(int port) {
            super(port);
            logger.log("listening at port " + port);
            Do.later(() -> start());
        }

        @Override
        public Response serve(IHTTPSession session) {
            Map<String, String> filesMap = new HashMap<>();
            try {
                Binder params = Binder.from(session.getParms());
                if (!params.getStringOrThrow("token").equals(token))
                    return errorResponse("invalid token");
//                logger.log("Serving " + session.getUri() + ": " + params);
                List<BufferedLogger.Entry> result;
                switch (session.getUri()) {
                    case "/log/last": {
                        int max = params.getInt("max", 10);
                        result = logger.getLast(max);
                        break;
                    }
                    case "/log/slice": {
                        long id = params.getLongOrThrow("id");
                        int max = params.getInt("max", 10);
                        result = logger.slice(id, max);
                        break;
                    }
                    default:
                        return errorResponse("unknown command");
                }
                return makeResponse(Response.Status.OK,
                                    Binder.fromKeysValues(
                                            "log",
                                            result.stream()
                                                    .map(e -> e.toBinder())
                                                    .toArray()
                                    ));
            } catch (Exception e) {
                try {
                    return errorResponse(e);
                } catch (Throwable t) {
                    t.printStackTrace();
                    throw t;
                }
            }
        }

        class JsonResponse extends Response {
            public JsonResponse(IStatus status, byte[] data) {
                super(status,
                      "application/json",
                      new ByteArrayInputStream(data),
                      data.length
                );
            }
        }

        private Response errorResponse(Throwable t) {
            t.printStackTrace();
            return errorResponse(Response.Status.NOT_ACCEPTABLE,
                                 new ErrorRecord(Errors.FAILURE, "", t.getMessage())
            );
        }

        private Response errorResponse(String error) {
            return errorResponse(Response.Status.NOT_ACCEPTABLE,
                                 new ErrorRecord(Errors.FAILURE, "", error)
            );
        }

        private Response reponseKeysValues(Response.Status status, Object... data) {
            return makeResponse(status, Binder.fromKeysValues(data));
        }


        private Response errorResponse(Response.Status code, ErrorRecord er) {
            return reponseKeysValues(code, "errors",
                                     asList(Binder.from(er))
            );
        }

        private Response makeResponse(Response.Status status, Object o) {
            Binder sourceData = Binder.from(o);
            return new JsonResponse(status, JsonTool.toJsonString(sourceData).getBytes());
        }
    }

    public TPSBenchmarkServer(BufferedLogger logger, String token, int port) {
        this.logger = logger;
        this.token = token;
        new Server(port);
    }

    public void setupTest(String basicPath) {
        try {
            logger.log("Setting up tests from " + basicPath);
            Yaml yaml = new Yaml();
            Binder params = Binder.from(
                    yaml.load(new FileInputStream(basicPath + "/config/config.yaml"))
            );
            System.out.println("config loaded, " + params.size() + " entries");
            String dbPath = params.getStringOrThrow("database");
            System.out.println("database connection string found");
            TPSTest test = new TPSTest(10000, dbPath, 128, 10, null);
//            try(Db db = test.getLedger().getDb() ) {
//                db.update("DELETE FROM ledger");
//            }
            test.setLogger(logger);
            PostgresLedger pl = test.getLedger();
            while (true) {
                logger.log("statring benchmark seqience");
                test.run();
                logger.log("sequence fininshed, cooling down CPU ;)");
                Thread.sleep(4000);
                if (pl.countRecords() > 100000000) {
                    try (Db db = pl.getDb()) {
                        db.update("delete from ledger where id in (select id from ledger order by id limit 50000);");
                    } catch (SQLException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to start tests: " + e);
            e.printStackTrace();
        }
    }

}
