/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package net.sergeych.farcall;

import net.sergeych.tools.DeferredResult;
import net.sergeych.utils.LogPrinter;
import net.sergeych.utils.Ut;

import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Farcall protocol general implementation.
 * <p>
 * Cross-platform fast, simple and elegant RPC protocol. Supports function calls with typed arguments (any that {@link
 * Connector} supports), as arrays, keywords or both, return values and remote exceptions.
 * <p>
 * Protocol description can be found here: https://github.com/sergeych/farcall/wiki along with the corresponding ruby
 * gem.
 * <p>
 * Created by sergeych on 10.04.16.
 */
@SuppressWarnings("ALL")
public class Farcall {

    static private LogPrinter log = new LogPrinter("FCAL");
    private final Connector connector;
    private Target target;
    //    static private ExecutorService pool = Executors.newSingleThreadExecutor();
//    static private ExecutorService pool = new ThreadPoolExecutor(16, 32, 5, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    static private ExecutorService pool = Executors.newCachedThreadPool();
    private ExecutorService executor;

    public interface Target {
        Object onCommand(Command command)
                throws Exception;
    }

    /**
     * The exception information that was passed from the remote party, while executing some command.
     */
    @SuppressWarnings("unused")
    public static class RemoteException extends Exception {
        private String remoteErrorClass;
        private String remoteErrorText;
        private Map<String, Object> data;

        public RemoteException(String remoteErrorClass, String remoteErrorText) {
            this.remoteErrorClass = remoteErrorClass;
            this.remoteErrorText = remoteErrorText;
        }

        @SuppressWarnings("unchecked")
        static Exception makeException(Object error) {
            if (error instanceof Map) {
                return new RemoteException((Map<String, Object>) error);
            }
            return new ProtocolException("bad remote exception record: " + error);
        }

        RemoteException(Map<String, Object> data) {
            this.data = data;
            remoteErrorClass = (String) data.get("class");
            String text = (String) data.get("text");
            if (text != null)
                remoteErrorText = text;
        }

        public String getRemoteErrorClass() {
            return remoteErrorClass;
        }

        public String getRemoteErrorText() {
            return remoteErrorText;
        }

        /**
         * Additinal data can be supplied with error.
         *
         * @return full remote error data
         */
        public Map<String, Object> getData() {
            return data;
        }

        @Override
        public String toString() {
            return "Farcall.RemoteException " + Ut.mapToString(data);
        }
    }

    /**
     * Create Farcall connected to some endpoint. To start actual working call {@link #start()} or {@link
     * #start(Target)}.
     *
     * @param connector
     */
    public Farcall(Connector connector) {
        this.connector = connector;
    }

    private Thread worker;
    private final Object access = new Object();

    private int inSerial = 0;
    private int outSerial = 0;

    private boolean requestStop = false;

    /**
     * Let farcall execute remote comands in the executor service. It means that {@link Target#onCommand(Command)} will
     * be executed using this service (what means, most often, asynchronouly.
     *
     * @param service to use. Call with null to resore synchronous operations.
     */
    public void asyncCommands(ExecutorService service) {
        this.executor = service;
    }

    /**
     * Allow farcall to use own ExecutorSerice to run commands in parallel. It means that {@link Target#onCommand(Command)} will
     * be executed using this service asynchronouly.
     */
    public void asyncCommands() {
        this.executor = pool;
    }


    /**
     * Start a worker thread that reads connector data, and executes recevied commamds using specified interface.
     *
     * @param target callback to process incoming commands
     */
    public void start(Target target) {
        synchronized (access) {
            if (worker != null)
                throw new IllegalStateException("farcall instance is already started");
            requestStop = false;
            this.target = target;
            worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    Farcall.this.receiveCommands();
                }
            });
            worker.setName("farcall receiver for "+Farcall.this);
        }
        worker.start();
    }

    /**
     * Start the protocol in one-way mode: this instance only issues commands. Any commands from remote will return
     * error 'unknown_command'
     */
    public void start() {
        start(new Target() {
            @Override
            public Object onCommand(Command command) throws Exception {
                throw new RemoteException("unknown_command", "command is not known");
            }
        });
    }

    private void receiveCommands() {
        while (!requestStop) {
            try {
                Map<String, Object> input = connector.receive();
                if (input == null)
                    break;
                int serial = ((Number) input.get("serial")).intValue();
                if (inSerial++ != serial)
                    throw new ProtocolException("farcall sync lost");
                Number ref = (Number) input.get("ref");
                if (ref != null) {
                    processReply(input, ref.intValue());
                } else {
                    processCommand(input, serial);
                }
            } catch (EOFException e) {
                log.i("closing farcall instance on eof encountered");
                close();
            }
            catch (IOException e) {
                log.wtf("internal error", e);
                break;
            }
        }
        close();
    }

    private void processCommand(Map<String, Object> input, int serial) throws IOException {
        if (executor != null)
            executor.submit(() -> {
                doCall(input, serial);
            });
        else
            doCall(input, serial);
    }

    private void doCall(Map<String, Object> input, int serial) {
        try {
            Object result = target.onCommand(new Command(input));
            sendToRemote("ref", serial, "result", result);
        } catch (RemoteException e) {
            sendErrorNoExceptions(serial, e.getRemoteErrorClass(), e.getRemoteErrorText());
        } catch (Exception e) {
            sendErrorNoExceptions(serial, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void sendError(int serial, String remoteErrorClass, String remoteErrorText) throws
            IOException {
        sendToRemote("ref", serial,
                "error", Ut.mapFromArray(
                        "class", remoteErrorClass,
                        "text", remoteErrorText));
    }

    private void sendErrorNoExceptions(int serial, String remoteErrorClass, String remoteErrorText) {
        try {
            sendError(serial, remoteErrorClass, remoteErrorText);
        } catch (IOException e) {
            log.wtf("failed to send asynchronous answer: " + remoteErrorText, e);
        }
    }

    private void processReply(Map<String, Object> input, Integer ref) {
        CommandResult dr;
//        synchronized (resultQueue) {
        dr = resultQueue.remove(ref);
//        }
        if (dr != null) {
            Object error = input.get("error");
            if (error != null) {
                dr.sendFailure(RemoteException.makeException(error));
            } else {
                Object result = input.get("result");
                dr.sendSuccess(result);
            }
        }
    }

    public boolean isClosed() {
        return worker == null;
    }

    /**
     * Send command without parameters
     *
     * @param name of the command
     * @return deferred result
     */
    public CommandResult send(String name) {
        return send(name, null, null);
    }

    /**
     * Send remote command with all kind of parameters
     *
     * @param name      of the command
     * @param params    array parameters or null
     * @param keyParams map parameters or null
     * @return deferred result
     */
    public CommandResult send(String name, ArrayList<Object> params, HashMap<String, Object>
            keyParams) {
        if (worker == null)
            throw new IllegalStateException("farcall instance must be started");
        return sendToRemote("cmd", name, "args", params, "kwargs", keyParams);
    }

    private final Map<Integer, CommandResult> resultQueue = new ConcurrentHashMap<>();

    private CommandResult sendToRemote(Object... keysValues) {
        HashMap<String, Object> packet = Ut.mapFromArray(keysValues);
        synchronized (access) {
            if (isClosed()) {
                CommandResult closedResult = new CommandResult(0);
                closedResult.sendFailure(new EOFException("farcall is closed"));
                return closedResult;
            }
            packet.put("serial", outSerial);
            CommandResult result = new CommandResult(outSerial);
//            synchronized (resultQueue) {
            resultQueue.put(outSerial, result);
//            }
            try {
                connector.send(packet);
            } catch (IOException e) {
//                synchronized (resultQueue) {
                resultQueue.remove(outSerial);
//                }
                result.sendFailure(e);
                close();
                return result;
            }
            outSerial++;
            return result;
        }
    }

    /**
     * Send Farcall command to the remote with only array parameters. To read remote answer, use returned {@link
     * DeferredResult} instance.
     *
     * @param name   command name
     * @param params array parameters
     * @return deferred result
     */
    public CommandResult sendParams(String name, Object... params) {
        return send(name, Ut.arrayToList(params), null);
    }

    /**
     * Send remote command with only keyed parameters. The arguments shoul be sequence of key, value pairs, where keys
     * must be String. For example:
     * <code><pre>
     *     farcall.sendKeyParams("foo", "param1", 10, "param2", 20, "bar", "baz")
     * </pre></code>
     * The return value being passed via {@link DeferredResult} is either data returned by the remote party, e.g. {@link
     * java.util.List}, {@link Map}, simple types, strings, or whatever else {@link Connector} allows, or, in the case
     * the remote throws and error, instance of the {@link RemoteException} class.
     *
     * @param name
     * @param keysAndValues key1, value1, key2, value2... sequence
     * @return deferred result
     */
    public CommandResult sendKeyParams(String name, Object... keysAndValues) {
        return send(name, null, Ut.mapFromArray(keysAndValues));
    }

    public void close() {
        Thread t = null;
        // This code avoids deadlock of calling close() from different threads
        // and avoid multiple closes
        synchronized (access) {
            if (worker != null) {
                t = worker;
                worker = null;
            }
        }
        // Then, releasing the lock, we perform closing, only once:
        if (t != null) {
            requestStop = true;
            t.interrupt();
            // While the worker closes, we can free up the queue:
            EOFException eof = new EOFException();
//            synchronized (resultQueue) {
            for (CommandResult dr : resultQueue.values()) {
                dr.sendFailure(eof);
            }
            resultQueue.clear();
//            }
//            try {
//                t.join();
//            } catch (InterruptedException ignored) {
//            }
            connector.close();
        }
    }

    /**
     * Deferred result of the Farcall command execution, to be used inside the farcall package. Provides serial-based
     * {@link #equals(Object)} and {@link #hashCode()} to be used in maps and sets. Pease see {@link DeferredResult} for
     * information.
     */
    public class CommandResult extends DeferredResult {

        private int commandSerial;

        CommandResult(int outSerial) {
            commandSerial = outSerial;
        }

        @Override
        public boolean equals(Object obj) {
            //noinspection SimplifiableIfStatement
            if (obj instanceof CommandResult)
                return ((CommandResult) obj).commandSerial == commandSerial;
            return false;
        }

        @Override
        public int hashCode() {
            return commandSerial;
        }

    }
}

