/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Approvable;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.utils.LogPrinter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * The business-logic of the network consensus. To be used from the LocalNode. Not for direct usage.
 * <p>
 * This class implements networking parading: spreading {@link Approvable} items across netowkr and forming the
 * consensus.
 */
class Elections {

    static private LogPrinter log = new LogPrinter("ELS");
    private final long electionsStartedMillis = System.currentTimeMillis();

    private Ledger ledger;
    private Network network;
    private Approvable item;
    private final HashId itemId;
    private AsyncEvent<Void> itemDownloaded = new AsyncEvent<>();
    private Object startLock = new Object();

    public ItemState getState() {
        return record.getState();
    }

    private boolean stop = false;

    // Important. number of threads in the pool must be at least 2 to allow download thread to wait for sources
    // otherwise it can block forever. In the test environment it should be greater than the number of voting
    // local nodes + 1
    static ScheduledThreadPoolExecutor pool;

    private BlockingQueue<Node> itemSources = new LinkedBlockingQueue<>();
    private Set<Poller> pollers = Collections.newSetFromMap(new ConcurrentHashMap<Poller, Boolean>());
    private Set<Node> positiveNodes = Collections.newSetFromMap(new ConcurrentHashMap<Node, Boolean>());
    private Set<Node> negativeNodes = Collections.newSetFromMap(new ConcurrentHashMap<Node, Boolean>());
    private Future<?> downloader;
    private LocalNode localNode;
    private volatile StateRecord record;
    private Object itemLock = new Object();
    private HashSet<StateRecord> lockedToRevoke = new HashSet<>();
    private HashSet<StateRecord> lockedToCreate = new HashSet<>();
    AsyncEvent<ItemResult> doneEvent = new AsyncEvent<>();
    private boolean started = false;

    static {
        pool = new ScheduledThreadPoolExecutor(256);
        pool.setMaximumPoolSize(256);
        pool.allowsCoreThreadTimeOut();
        pool.setKeepAliveTime(1, TimeUnit.SECONDS);
    }

    public Elections(LocalNode localNode, HashId itemId) throws Error {
        this.itemId = itemId;
        this.localNode = localNode;
    }

    public Elections(LocalNode localNode, Approvable item) throws Error {
        this.itemId = item.getId();
        this.item = item;
        itemDownloaded.fire(null);
        this.localNode = localNode;
    }

    public void ensureStarted() throws Error {
        synchronized (startLock) {
            if (started)
                return;
            initWithNode(localNode);
            started = true;
        }
    }

    /**
     * Perform main initialization. It is critical to set {@link #itemId} and, if present, {@link #item} fields PRIOR TO
     * CALL UP THE INITIALIZATION. It strongly relies on these fields.
     *
     * @param localNode node that creates elections.
     */
    private void initWithNode(LocalNode localNode) throws Error {
//        log.showDebug(true);
        this.localNode = localNode;
        this.ledger = localNode.getLedger();
        this.network = localNode.getNetwork();

        record = ledger.findOrCreate(itemId);
        if (record == null) {
            throw new RuntimeException("ledger failed to findOrCreate");
        }
        if (record.getExpiresAt() == null)
            record.setExpiresAt(LocalDateTime.now().plus(network.getMaxElectionsTime()));
        if (record.getState() != ItemState.PENDING)
            throw new Error("ledger already has a record for " + itemId + " with state " + record.getState());
        if (item != null) {
            checkItem();
            log.d("starting existing item voting");
            startVoting();
        } else {
            record.save();
            startDownload();
        }
    }

    /**
     * Perform local check of the item and change state accordingly. Note that even if some checks are not passed, the
     * ledger should be updated accordingly, as the consensus can later approve the item that looks illegal, and we will
     * have to update the ledger then.
     */
    private void checkItem() {
        synchronized (itemLock) {
            // Skip check if we're closing of if the network has found the consensus
            if (stop || getState() == ItemState.APPROVED)
                return;
            assert (item != null);

            boolean checkPassed = true;


            // Check the internal state
            if (!item.check()) {
                // Too bad: we will not process it further
                checkPassed = false;
            } else {
                // check the referenced items
                for (HashId id : item.getReferencedItems()) {
                    if (!ledger.isApproved(id)) {
                        item.addError(Errors.BAD_REF, id.toString(), "reference not approved");
                        checkPassed = false;
                    }
                }

                // check revoking items
                for (Approvable a : item.getRevokingItems()) {
                    StateRecord r = record.lockToRevoke(a.getId());
                    if (r == null) {
                        checkPassed = false;
                        item.addError(Errors.BAD_REVOKE, a.getId().toString(), "can't revoke");
                    } else
                        lockedToRevoke.add(r);
                }

                // check new items
                for (Approvable newItem : item.getNewItems()) {
                    if (!newItem.check()) {
                        checkPassed = false;
                        item.addError(Errors.BAD_NEW_ITEM, newItem.getId().toString(), "bad new item: not passed check");
                    } else {
                        StateRecord r = record.createOutputLockRecord(newItem.getId());
                        if (r == null) {
                            checkPassed = false;
                            item.addError(Errors.NEW_ITEM_EXISTS, newItem.getId().toString(), "new item existst in ledger");
                        } else {
                            lockedToCreate.add(r);
                        }
                    }
                }
            }

            record.setState(checkPassed ? ItemState.PENDING_POSITIVE : ItemState.PENDING_NEGATIVE);
            record.save();
            registerVote(localNode, checkPassed);
//            log.d(localNode.toString()+" checked item "+itemId+" : "+getState());
        }
    }


    private void startDownload() {
        if (downloader != null)
            return;
        downloader = pool.submit(() -> {
            log.d(localNode + " starts download thread");
            while (item == null && !stop && getMillisLeft() > 0) {
//                log.d(localNode.toString()+" attempt to download "+itemId);
                Node node = itemSources.poll(getMillisLeft(), TimeUnit.MILLISECONDS);
                if (node == null)
                    continue;
                log.d(localNode + " has a source: " + node);
                if (localNode.lateDownload) {
                    log.d(localNode + "--------------------------------------------------- late download active");
                    Thread.sleep(200);
                    localNode.lateDownload = false;
                    log.d(localNode + "---------------------------------------------------  we can download");
                }
                try {
                    item = node.getItem(itemId);
                    // null means this node has no item at hand, so we check others
                    if (item != null) {
                        log.d(localNode + " downloaded " + itemId + " from " + node);
                        itemDownloaded.fire(null);
                        checkItem();
                        log.d("starting downloaded item voting, my state is " + getState());
                        startVoting();
                        break;
                    } else {
                        log.i("strange: item not found at " + node + ", in queue: " + itemSources.size());
                        // We should retry it anyway:
                        itemSources.add(node);
                    }
                } catch (IOException ex) {
                    // IOException means that we can retry
                    log.wtf("exception loading item: " + node + ": ", ex);
                    itemSources.add(node);
                    log.i("our list of sources: " + itemSources);
                }
            }
            if (item == null)
                checkElectionsFailed();
            return null;
        });
    }

    synchronized public void close() {
        synchronized (itemLock) {
            if (!stop) {
                stop = true;
                if (downloader != null)
                    downloader.cancel(true);
                // the pollers collection can not be mutated at this point as all pollers are created in the constructor.
                // If later this behavior will be changed, this should be rewritten to sync with stop field changes
                pool.execute(() -> pollers.forEach(x -> x.close()));
                // Notify network about my solution
                if (getState() == ItemState.APPROVED) {
                    log.d("Informing rest of the nodes about my solution");
                    network.getAllNodes().forEach(node -> {
                        try {
                            node.checkItem(localNode, itemId, getState(), item != null);
                        } catch (Exception e) {
                            // this time it does not matter
                        }
                    });
                }
//                try {
//                    pool.awaitTermination(500,TimeUnit.MILLISECONDS);
//                } catch (InterruptedException e) {
//                }
                fireOnDone();
            }
        }
    }

    private void fireOnDone() {
        ItemResult result = new ItemResult(record);
        doneEvent.fire(result);
    }

    private void startVoting() {
        // Shuffle nodes and start polling
        List<Node> nodes = new ArrayList<>(network.getAllNodes());
        Collections.shuffle(nodes);
        // Importand order! first, fill in all pollers pool
        nodes.forEach(node -> {
            if (node != localNode) new Poller(node);
        });
        // only now we can start them to avoid racing conditions
        // that could detect false no quorum error
        pollers.forEach(p -> p.start());
        // Actually this is a test situation which should be modelled somehow else
        checkElectionsFailed();
    }

    /**
     * Count vote from a node. If this vote makes consensus, stop elections, change ledger and report the result.
     *
     * @param node    that gives a vote
     * @param approve true it the node approves the item, false if it does not.
     */
    public void registerVote(Node node, boolean approve) {
        if (!stop) {
            // process only if the set has been changed

            boolean checkConsensus, voteChanged;
            if (approve) {
                voteChanged = negativeNodes.remove(node);
                checkConsensus = positiveNodes.add(node);
            } else {
                voteChanged = positiveNodes.remove(node);
                checkConsensus = negativeNodes.add(node);
            }

            if (voteChanged)
                log.w("" + localNode + ": vote changed from " + node + " now is " + approve);

            log.d("" + localNode + " register vote from " + node + ": " + approve + ": " +
                          positiveNodes.size() + " / " + negativeNodes.size() + " consensus: " + network.getPositiveConsensus());

            if (checkConsensus) {
                boolean conesnusFound = false;
                boolean positive = false;
                if (negativeNodes.size() >= network.getNegativeConsensus()) {
                    positive = false;
                    conesnusFound = true;
                } else if (positiveNodes.size() >= network.getPositiveConsensus()) {
                    positive = true;
                    conesnusFound = true;
                }
                if (conesnusFound) {
                    // close elections, save the result.
                    if (positive)
                        commitAndApprove();
                    else
                        rollbackChanges(ItemState.DECLINED, LocalDateTime.now().plus(network.getDeclinedExpiration()));
                    close();
                }
            }
        }
    }

    /**
     * Mark this record as apporved, revoke and create all referenced items in a transaction. Thread-safe method. There
     * is a trick: some records could be in conflict state, these should be overriden. It tries to maintain ledger
     * "iterable integrity", e.g. each new consenus should bring local ledger closer to the state shared by the
     * network.
     */
    private void commitAndApprove() {
        synchronized (itemLock) {
            // todo: fix logic to surely copy approving item dependency. e.g. download original or at least dependencies
            log.d(localNode.toString() + " approved: " + itemId);
            // first we need to flag our state as approved
            record.setState(ItemState.APPROVED);
            // it may happen that consensus is found earlier than item is download
            // we still need item to fix all its relations:
            if (item == null) {
                try {
                    long millisLeft = getMillisLeft();
                    if (millisLeft > 0)
                        itemDownloaded.await(millisLeft);
                } catch (TimeoutException e) {
                    log.e("Failed downloading item from "+localNode.getId());
                    // TODO: very inlikely case. We should somehow try to restore links
                    // actually it should not happen
                    e.printStackTrace();
                }
            }
            if (item != null) {
                record.save();
                // We use the caching capability of ledger so we do not get records from
                // lockedToRevoke/lockedToCreate, as, due to conflicts, these could differ from what the item
                // yields. We jsu clean them up afterwards:
                for (Approvable a : item.getRevokingItems()) {
                    // The record may not exist due to ledger desync, so we create it if need
                    StateRecord r = ledger.findOrCreate(a.getId());
                    r.setState(ItemState.REVOKED);
                    r.setExpiresAt(LocalDateTime.now().plus(network.getArchiveExpiration()));
                    r.save();
                }
                for (Approvable item : item.getNewItems()) {
                    // The record may not exist due to ledger desync too, so we create it if need
                    StateRecord r = ledger.findOrCreate(item.getId());
                    r.setState(ItemState.APPROVED);
                    r.setExpiresAt(LocalDateTime.now().plus(network.getApprovedExpiration()));
                    r.save();
                }
                lockedToCreate.clear();
                lockedToRevoke.clear();
            }
        }
        fireOnDone();
    }

    /**
     * Mark this item as {@link ItemState#DECLINED} and update ledger, unlocking and removing connected records in a
     * transaction. Thread safe method.
     */
    private void rollbackChanges(ItemState newState, LocalDateTime expiration) {
        log.d(localNode.toString() + " rollbacks to: " + itemId + " as " + newState + " consensus: " + positiveNodes.size() + "/" + negativeNodes.size());
        synchronized (itemLock) {
            ledger.transaction(() -> {
                for (StateRecord r : lockedToRevoke)
                    r.unlock().save();
                lockedToRevoke.clear();
                // form created records, we touch only these that we have actually created
                for (StateRecord r : lockedToCreate)
                    r.unlock().save();
                lockedToCreate.clear();
                record.setState(newState);
                record.setExpiresAt(expiration);
                record.save(); // TODO: current implementation will cause an inner dbPool.db() invokation
                return null;
            });
        }
        fireOnDone();
    }


    public void addSourceNode(Node caller) {
        // todo: do not add same node twice - backup nodes that were once added
        itemSources.add(caller);
    }

    public StateRecord getRecord() {
        return record;
    }

    public Approvable getItem() {
        return item;
    }

    /**
     * Add consumer called when processing is done - either by consensuns found (positive or negative) or elections
     * failed. See {@link ItemResult} for data passed to te consumers.
     *
     * @param consumer
     */
    public void onDone(Consumer<ItemResult> consumer) {
        doneEvent.addConsumer(consumer);
    }

    /**
     * Blocks the caller tree until the election is fininshed (either by consensus found or error)
     *
     * @return result
     *
     * @throws InterruptedException
     */
    public ItemResult waitDone() throws InterruptedException {
        return doneEvent.waitFired();
    }

    private long getMillisLeft() {
        return network.getMaxElectionsTime().toMillis() - (System.currentTimeMillis() - electionsStartedMillis);
    }

    private void checkElectionsFailed() {
        if ((pollers.size() == 0 && getState().isPending()) || getMillisLeft() <= 0 || LocalDateTime.now().isAfter(record.getExpiresAt())) {
            stopOnFailure();
        }
    }

    private void stopOnFailure() {
        log.d(localNode.toString() + " failing elections, pollers: " + pollers.size());
        rollbackChanges(ItemState.UNDEFINED, LocalDateTime.now().plusSeconds(5));
        Elections.this.close();
    }

    /**
     * Poller implements step-retry loginc on polling one node for decision. To avoid occupying thread with retry
     * waiting, Poller implements one poll step then reschedules itself if need.
     * <p>
     * Poller could be used as hash key, it behaves like Id of the connected node in this role.
     */
    private class Poller implements Runnable {

        private Node node;
        private Future<?> future;

        Poller(Node node) {
            this.node = node;
            pollers.add(this);
        }

        public void start() {
            future = pool.submit(this);
        }


        @Override
        public int hashCode() {
            return node.getId().hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Poller)
                return node.getId().equals(((Poller) obj).node.getId());
            return false;
        }

        /**
         * Close poller, unregister it from outer class and cancel it's future (e.g. stop polling)
         */
        public void close() {
            synchronized (future) {
                // get rid of node != null
                if (future != null) {
                    future.cancel(false);
                    future = null;
                    pollers.remove(this);
                }
            }
        }

        /**
         * A step of polling a node. Call node, process the answer, check the consensus, reschedule if need, check the
         * timeuot and quorum errors, cancel if need.
         */
        @Override
        public void run() {
            if (!stop) {
                try {
                    if (positiveNodes.contains(node) || negativeNodes.contains(node)) {
                        // the node has already voted, don't poll it
                        pollers.remove(this);
                        return;
                    }
                    ItemResult result = node.checkItem(localNode, itemId, getState(), item != null);
                    if (result == null) {
                        throw new IOException("failed to read checkitem result");
                    }

                    if (item == null && result.haveCopy)
                        addSourceNode(node);
                    switch (result.state) {
                        case PENDING:
                        case UNDEFINED:
                            // no result, being processed/wait, not removing self from pollers:
                            reschedule();
                            break;
                        case PENDING_POSITIVE:
                        case LOCKED:
                        case APPROVED:
                            // positive decision found
                            registerVote(node, true);
                            pollers.remove(this);
                            break;
                        default:
                            // decision found but it is not positive
                            registerVote(node, false);
                            pollers.remove(this);
                            break;
                    }
                } catch (InterruptedException e) {
                    // We just silenlty close
                    log.d(localNode.toString() + " stop polling, interrupted, pollers size: " + pollers.size());
                    pollers.remove(this);
                } catch (Exception e) {
//                    log.e("failed to check item " + itemId + " from node " + node + ": " + e.getMessage() + ", retrying");
//                    e.printStackTrace();
                    reschedule();
                }
                checkElectionsFailed();
            }
        }

        private void reschedule() {
            if (!stop) {
                long rt = network.getRequeryPause().toMillis();
                if (rt < getMillisLeft())
                    future = pool.schedule(this, network.getRequeryPause().toMillis(), TimeUnit.MILLISECONDS);
                else {
                    // Elections timed out
                    stopOnFailure();
                }
            }
        }
    }

    public class Failure extends RuntimeException {
        public Failure() {
        }

        public Failure(String message) {
            super(message);
        }

        public Failure(String message, Throwable cause) {
            super(message, cause);
        }

        public Elections getElections() {
            return Elections.this;
        }
    }

    public class Error extends IOException {
        public Error() {
        }

        public Error(String message) {
            super(message);
        }

        public Error(String message, Throwable cause) {
            super(message, cause);
        }

        public Elections getElections() {
            return Elections.this;
        }
    }

}
