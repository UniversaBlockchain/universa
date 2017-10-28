/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.Ledger;
import com.icodici.universa.node.StateRecord;
import com.icodici.universa.node2.network.Network;
import net.sergeych.tools.AsyncEvent;
import net.sergeych.tools.Do;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * The v2 Node.
 * <p>
 * In v2 node is always the local node. All the rest takes {@link com.icodici.universa.node2.network.Network} class.
 */
public class Node {

    private static LogPrinter log = new LogPrinter("NODE");

    private final Config config;
    private final NodeInfo myInfo;
    private final Ledger ledger;
    private final Network network;
    private final ItemCache cache;

    private ConcurrentHashMap<HashId, ItemProcessor> processors = new ConcurrentHashMap();

    private static ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(64);

    public Node(Config config, NodeInfo myInfo, Ledger ledger, Network network) {
        this.config = config;
        this.myInfo = myInfo;
        this.ledger = ledger;
        this.network = network;
        cache = new ItemCache(config.getMaxCacheAge());
        network.subscribe(myInfo, notification -> onNotification(notification));
    }

    /**
     * Asynchronous (non blocking) check/register item state. IF the item is new and eligible to process with the
     * consensus, the processing will be started immediately. If it is already processing, the current state will be
     * returned.
     *
     * @param item to register/check state
     *
     * @return current (or last known) item state
     */
    public @NonNull ItemResult registerItem(Approvable item) {
        Object x = checkItemInternal(item.getId(), item, true);
        return (x instanceof ItemResult) ? (ItemResult) x : ((ItemProcessor) x).getResult();
    }

    /**
     * Check the state of the item. This method does not start elections and can be safely called from a client.
     *
     * @param itemId item to check
     *
     * @return last known state
     */
    public @NonNull ItemResult checkItem(HashId itemId) {
        Object x = checkItemInternal(itemId, null, false);
        return (x instanceof ItemResult) ? (ItemResult) x : ((ItemProcessor) x).getResult();
    }

    /**
     * Test use only. It the item is being elected, block until the item is processed with the consenus. Otherwise
     * returns state immediately.
     *
     * @param itemId item ti check or wait for
     *
     * @return item state
     */
    public ItemResult waitItem(HashId itemId, long millisToWait) throws TimeoutException, InterruptedException {
        Object x = checkItemInternal(itemId, null, false);
        if (x instanceof ItemProcessor) {
            ((ItemProcessor) x).doneEvent.await(millisToWait);
            return ((ItemProcessor) x).getResult();
        }
        return (ItemResult) x;
    }

    private final void onNotification(Notification notification) {
        if (notification instanceof ItemNotification) {
            ItemNotification in = (ItemNotification) notification;
            // get processor, create if need
            // register my vote
            Object x = checkItemInternal(in.getItemId(), null, true);
            NodeInfo from = in.getFrom();
            if (x instanceof ItemResult) {
                ItemResult r = (ItemResult) x;
                // we have solution and need not answer, we answer if requested:
                if (in.answerIsRequested()) {
                    network.deliver(
                            from,
                            new ItemNotification(myInfo, in.getItemId(), r, false)
                    );
                }
                return;
            }
            if (x instanceof ItemProcessor) {
                ItemProcessor ip = (ItemProcessor) x;
                ItemResult result = in.getItemResult();
                ip.lock( ()-> {
                    debug("notification from " + in.getFrom() + ": " + in.getItemId() + ": " + in.getItemResult() + ", " + in.answerIsRequested());
                    debug("my state in it " + ip.getState() + " and I have a copy: " + (ip.item != null));
                    // we might still need to download and process it
                    if (result.haveCopy) {
//                    debug("reported source for "+ip.itemId+": "+in.getFrom());
                        ip.addToSources(from);
                    }
                    if (result.state != ItemState.PENDING)
                        ip.vote(from, result.state);
                    else
                        log.e("-- pending vote on " + in.getItemId() + " from " + from);
                    // We answer only if (1) answer is requested and (2) we have position on the subject:
                    if (in.answerIsRequested() && ip.record.getState() != ItemState.PENDING) {
                        network.deliver(
                                from,
                                new ItemNotification(myInfo,
                                                     in.getItemId(),
                                                     ip.getResult(),
                                                     ip.needsVoteFrom(from))
                        );
                    }
                    return null;
                });
                return;
            }
            debug("impossible state: onNotification can't have invalid state from local check\n" + x);
        }
    }

    /**
     * Optimized for various usages, check the item, start processing as need, return object depending on the current
     * state. Note that actuall error codes are set to the item itself.
     *
     * @param itemId    item to check the state
     * @param item      provide item if any, can be null
     * @param autoStart
     *
     * @return instance od {@link ItemProcessor} if the item is being processed (also if it was started by the call),
     *         {@link ItemResult} if it is already processed or can't be processed, say, created_at field is too far in
     *         the past, in which case result state will be {@link ItemState#DISCARDED}.
     */
    protected Object checkItemInternal(@NonNull HashId itemId, Approvable item, boolean autoStart) {
        try {
            // first, let's lock to the item id:
            return ItemLock.synchronize(itemId, (lock) -> {
                ItemProcessor ip = processors.get(itemId);
                if (ip != null) {
                    debug("existing IP found for "+itemId);
                    return ip;
                }

                StateRecord r = ledger.getRecord(itemId);
                // if it is not pending, it means it is already processed:
                if (r != null) {
                    // it is, and we may still have it cached - we do not put it again:
                    return new ItemResult(r, cache.get(itemId) != null);
                }

                debug("no record in ledger found for "+itemId);

                // we have no consensus on it. We might need to find one, after some precheck.
                // The contract should not be too old to process:
                if (item != null &&
                        item.getCreatedAt().isBefore(ZonedDateTime.now().minus(config.getMaxItemCreationAge()))) {
                    // it is too old - client must manually check other nodes. For us it's unknown
                    item.addError(Errors.EXPIRED, "created_at", "too old");
                    return ItemResult.DISCARDED;
                }

                if (autoStart) {
                    if (item != null)
                        cache.put(item);
                    ItemProcessor processor = new ItemProcessor(itemId, item, lock);
                    processors.put(itemId, processor);
                    return processor;
                } else {
                    return ItemResult.UNDEFINED;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("failed to checkItem", e);
        }
    }

    protected void debug(String str) {
        log.d(toString() + ": " + str);
    }

    @Override
    public String toString() {
        return "Node(" + myInfo.getNumber() + ")";
    }

    /**
     * Get the cached item.
     *
     * @param itemId
     *
     * @return cached item or null if it is missing
     */
    public Approvable getItem(HashId itemId) {
        return cache.get(itemId);
    }

    public int countElections() {
        return processors.size();
    }

    private class ItemProcessor {

        private Approvable item;
        private final StateRecord record;
        private final HashId itemId;
        private Set<NodeInfo> sources = new HashSet<>();
        private Instant expiresAt;

        private Set<NodeInfo> positiveNodes = new HashSet<>();
        private Set<NodeInfo> negativeNodes = new HashSet<>();
        private List<StateRecord> lockedToRevoke = new ArrayList<>();
        private List<StateRecord> lockedToCreate = new ArrayList<>();
        private boolean consensusFound;
        private final AsyncEvent<Void> downloadedEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> doneEvent = new AsyncEvent<>();

        private final Object mutex;
        private ScheduledFuture<?> poller;
        private ScheduledFuture<?> downloader;

        public ItemProcessor(HashId itemId, Approvable item,Object lock) {
            mutex = lock;
            this.itemId = itemId;
            if (item == null)
                item = cache.get(itemId);
            this.item = item;
            record = ledger.findOrCreate(itemId);
            expiresAt = Instant.now().plus(config.getMaxElectionsTime());
            consensusFound = false;
            if (this.item != null)
                executorService.submit(() -> itemDownloaded());
        }

        private boolean isExpired() {
            return expiresAt.isBefore(Instant.now());
        }

        private long getMillisLeft() {
            return expiresAt.toEpochMilli() - Instant.now().toEpochMilli();
        }

        private void download() {
            while (!isExpired() && item == null) {
                if (sources.isEmpty()) {
                    log.e("empty sources for download taks, stopping");
                    return;
                } else {
                    try {
                        // first we have to wait for sources
                        NodeInfo source;
                        // Important: it could be disturbed by notifications
                        synchronized (sources) {
                            source = Do.sample(sources);
                        }
                        item = network.getItem(itemId, source, config.getMaxGetItemTime());
                        if (item != null) {
                            debug("downloaded " + itemId + " from " + source);
                            itemDownloaded();
                            return;
                        } else {
                            debug("failed to download " + itemId + " from " + source);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private final void itemDownloaded() {
            cache.put(item);
            checkItem();
            downloadedEvent.fire();
            startPolling();
        }

        private void pulseDownload() {
            synchronized (mutex) {
                if (item == null && (downloader == null || downloader.isDone())) {
                    debug("submitting download");
                    downloader = (ScheduledFuture<?>) executorService.submit(() -> download());
                }
            }
        }

        private final void startPolling() {
            // at this poing the item is with us, so we can start
            synchronized (mutex) {
                if (!consensusFound) {
                    long millis = config.getPollTime().toMillis();
                    poller = executorService.scheduleAtFixedRate(() -> poll(), millis, millis, TimeUnit.MILLISECONDS);
                }
            }
        }

        private boolean checkStarted = false;

        private final void checkItem() {
            if (checkStarted)
                throw new RuntimeException("double check!");

            ItemState newState;
            debug("Checking " + itemId + " state was " + record.getState());
            // Check the internal state
            // Too bad if basic check isn't passed, we will not process it further
            if (item.check()) {
                // check the referenced items
                for (HashId id : item.getReferencedItems()) {
                    if (!ledger.isApproved(id)) {
                        item.addError(Errors.BAD_REF, id.toString(), "reference not approved");
                    }
                }
                // check revoking items
                for (Approvable a : item.getRevokingItems()) {
                    StateRecord r = record.lockToRevoke(a.getId());
                    if (r == null) {
                        item.addError(Errors.BAD_REVOKE, a.getId().toString(), "can't revoke");
                    } else
                        lockedToRevoke.add(r);
                }
                // check new items
                for (Approvable newItem : item.getNewItems()) {
                    if (!newItem.check()) {
                        item.addError(Errors.BAD_NEW_ITEM, newItem.getId().toString(), "bad new item: not passed check");
                    } else {
                        StateRecord r = record.createOutputLockRecord(newItem.getId());
                        if (r == null) {
                            item.addError(Errors.NEW_ITEM_EXISTS, newItem.getId().toString(), "new item existst in ledger");
                        } else {
                            lockedToCreate.add(r);
                        }
                    }
                }
            }
            boolean checkPassed = item.getErrors().isEmpty();
            synchronized (mutex) {
                if (record.getState() == ItemState.PENDING) {
                    newState = checkPassed ? ItemState.PENDING_POSITIVE : ItemState.PENDING_NEGATIVE;
                    setState(newState);
                }
            }
            record.setExpiresAt(item.getExpiresAt());
            record.save();
            vote(myInfo, record.getState());
            broadcastMyState();
        }

        private final void poll() {
            synchronized (mutex) {
                if (consensusFound)
                    return;
                if (isExpired()) {
                    // cancel by timeout expired
                    debug("consensus not found in maximum allowed time, cancelling " + itemId);
                    consensusFound = true;
                    rollbackChanges(ItemState.UNDEFINED);
                    poller.cancel(false);
                    if (downloader != null)
                        downloader.cancel(false);
                    close();
                    return;
                }
            }
            // at this point we should requery the nodes that did not yet answered us
            Notification notification = new ItemNotification(myInfo, itemId, getResult(), true);
            network.eachNode(node -> {
                if (!positiveNodes.contains(node) && !negativeNodes.contains(node))
                    network.deliver(node, notification);
            });
        }

        private final void broadcastMyState() {
            network.broadcast(myInfo, new ItemNotification(myInfo, itemId, getResult(), true));
        }

        private final void vote(NodeInfo node, ItemState state) {
            boolean positiveConsenus = false;
            boolean negativeConsenus = false;
            synchronized (mutex) {
                if (consensusFound)
                    return;
                Set<NodeInfo> add, remove;
                if (state.isPositive()) {
                    add = positiveNodes;
                    remove = negativeNodes;
                } else {
                    add = negativeNodes;
                    remove = positiveNodes;
                }
                add.add(node);
                remove.remove(node);
                if (negativeNodes.size() >= config.getNegativeConsensus()) {
                    consensusFound = true;
                    negativeConsenus = true;
                } else if (positiveNodes.size() >= config.getPositiveConsensus()) {
                    consensusFound = positiveConsenus = true;
                }
                debug("vote for " + itemId + " from " + node + ": " + state + " > " + positiveNodes.size() + "/" +
                              negativeNodes.size() + " consFound=" + consensusFound + ": positive=" + positiveConsenus);
                if (!consensusFound)
                    return;
            }
            if (positiveConsenus) {
                approveAndCommit();
            } else if (negativeConsenus) {
                rollbackChanges(ItemState.DECLINED);
            } else
                throw new RuntimeException("error: consensus reported without consensus");
        }

        private final void approveAndCommit() {
            // todo: fix logic to surely copy approving item dependency. e.g. download original or at least dependencies
            // first we need to flag our state as approved
            setState(ItemState.APPROVED);
            executorService.submit(() -> downloadAndCommit());
        }

        private void downloadAndCommit() {
            // it may happen that consensus is found earlier than item is download
            // we still need item to fix all its relations:
            try {
                if (item == null) {
                    // If positive consensus os found, we can spend more time for final download, and can try
                    // all the network as the source:
                    expiresAt = Instant.now().plus(config.getMaxDownloadOnApproveTime());
                    downloadedEvent.await(getMillisLeft());
                }
                // We use the caching capability of ledger so we do not get records from
                // lockedToRevoke/lockedToCreate, as, due to conflicts, these could differ from what the item
                // yields. We just clean them up afterwards:
                for (Approvable a : item.getRevokingItems()) {
                    // The record may not exist due to ledger desync, so we create it if need
                    StateRecord r = ledger.findOrCreate(a.getId());
                    r.setState(ItemState.REVOKED);
                    r.setExpiresAt(ZonedDateTime.now().plus(config.getRevokedItemExpiration()));
                    r.save();
                }
                for (Approvable item : item.getNewItems()) {
                    // The record may not exist due to ledger desync too, so we create it if need
                    StateRecord r = ledger.findOrCreate(item.getId());
                    r.setState(ItemState.APPROVED);
                    r.setExpiresAt(item.getExpiresAt());
                    r.save();
                }
                lockedToCreate.clear();
                lockedToRevoke.clear();
                record.save();
                if (record.getState() != ItemState.APPROVED) {
                    log.e("record is not approved2 " + record.getState());
                }
                debug("approval done for " + itemId+ " : "+getState() + " have copy " + (item == null));
            } catch (TimeoutException | InterruptedException e) {
                debug("commit: failed to load item " + itemId + " ledger will not be altered, the record will be destroyed");
                setState(ItemState.UNDEFINED);
                record.destroy();
            }
            close();
        }

        private void close() {
            debug("closing "+itemId+" : "+getState());
            doneEvent.fire();
            if (poller != null)
                poller.cancel(false);
            processors.remove(itemId);
            debug("closed "+itemId);
        }

        private ItemState getState() {
            return record.getState();
        }

        private final void setState(ItemState newState) {
            synchronized (mutex) {
                record.setState(newState);
            }
        }

        private void rollbackChanges(ItemState newState) {
            debug(" rollbacks to: " + itemId + " as " + newState + " consensus: " + positiveNodes.size() + "/" + negativeNodes.size());
            ledger.transaction(() -> {
                for (StateRecord r : lockedToRevoke)
                    r.unlock().save();
                lockedToRevoke.clear();
                // form created records, we touch only these that we have actually created
                for (StateRecord r : lockedToCreate)
                    r.unlock().save();
                lockedToCreate.clear();
                setState(newState);
                ZonedDateTime expiration = ZonedDateTime.now()
                        .plus(newState == ItemState.REVOKED ?
                                      config.getRevokedItemExpiration() : config.getDeclinedItemExpiration());
                record.setExpiresAt(expiration);
                record.save(); // TODO: current implementation will cause an inner dbPool.db() invocation
                return null;
            });
            close();
        }


        public @NonNull ItemResult getResult() {
            return new ItemResult(record, item != null);
        }

        /**
         * true if we need to get vote from a node
         *
         * @param node we might need vote from
         *
         * @return
         */
        private final boolean needsVoteFrom(NodeInfo node) {
            return record.getState().isPending() && !positiveNodes.contains(node) && !negativeNodes.contains(node);
        }

        private final void addToSources(NodeInfo node) {
            if (item != null)
                return;
            synchronized (sources) {
                if (sources.add(node)) {
                    debug("added source: " + sources);
                    pulseDownload();
                }
            }
        }

        public <T> T lock(Supplier<T> c) {
            synchronized (mutex) {
                return (T) c.get();
            }
        }
    }
}
