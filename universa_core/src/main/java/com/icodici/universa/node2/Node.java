/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.ReferenceModel;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.Ledger;
import com.icodici.universa.node.StateRecord;
import com.icodici.universa.node2.network.Network;
import net.sergeych.tools.*;
import net.sergeych.utils.LogPrinter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final Object ledgerRollbackLock = new Object();
    private final Network network;
    private final ItemCache cache;
    private final ItemInformer informer = new ItemInformer();

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

        Object x = checkItemInternal(itemId);
        ItemResult ir = (x instanceof ItemResult) ? (ItemResult) x : ((ItemProcessor) x).getResult();
        ItemInformer.Record record = informer.takeFor(itemId);
        if (record != null)
            ir.errors = record.errorRecords;
        return ir;
    }

    public @NonNull Binder extendedCheckItem(HashId itemId) {

        ItemResult ir = checkItem(itemId);
        Binder result = Binder.of("itemResult", ir);
        if (ir != null && ir.state == ItemState.LOCKED) {
            ir.lockedById = ledger.getLockOwnerOf(itemId).getId();
//            result.put("lockedBy", ledger.getRecord(itemId).getOwner());
        }
        return result;
    }


    /**
     * Resync the item. This method launch resync process, call to network to know what consensus is or hasn't consensus for the item.
     *
     * @param id item to resync
     *
     */
    public void resync(HashId id) throws Exception {

        Object x = checkItemInternal(id, null, true, true);
        // todo: prevent double launch of resync and break another processes
        if (x instanceof ItemProcessor) {
            ((ItemProcessor) x).pulseResync(true);
        } else {
            log.e("ItemProcessor hasn't found or created for " + id.toBase64String());
        }
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
        Object x = checkItemInternal(itemId);
        if (x instanceof ItemProcessor) {
            ((ItemProcessor) x).doneEvent.await(millisToWait);

            return ((ItemProcessor) x).getResult();
        }
        debug("it is not processor: " + x);
        return (ItemResult) x;
    }

    /**
     * Notification handler. Checking type of notification and call needed obtainer.
     *
     */
    private final void onNotification(Notification notification) {

        if (notification instanceof ItemResyncNotification) {
            obtainResyncNotification((ItemResyncNotification) notification);
        } else if (notification instanceof ItemNotification) {
            obtainCommonNotification((ItemNotification) notification);
        }
    }


    /**
     * Obtain got resync notification: looking for result or item processor and register resync vote
     *
     * @param notification resync notification
     *
     */
    private final void obtainResyncNotification(ItemResyncNotification notification) {

        debug("got " + notification);

        HashMap<HashId, ItemState> itemsToResync = notification.getItemsToResync();
        HashMap<HashId, ItemState> answersForItems = new HashMap<>();

        NodeInfo from = notification.getFrom();

        // get processor, do not create new if not exist
        // register resync vote for waiting processor or deliver resync vote
        Object itemObject = checkItemInternal(notification.getItemId());

        if (itemObject instanceof ItemResult) {
            debug("found ItemResult for parent item with state: " + ((ItemResult) itemObject).state);

            if (notification.answerIsRequested()) {
                // iterate on subItems of parent item that need to resync (stored at ItemResyncNotification.getItemsToResync())
                for (HashId hid : itemsToResync.keySet()) {
                    Object subitemObject = checkItemInternal(hid);
                    ItemResult subItemResult;
                    ItemState subItemState;

                    if (subitemObject instanceof ItemResult) {
                        // we have solution for resyncing subitem:
                        subItemResult = (ItemResult) subitemObject;
                    } else if (subitemObject instanceof ItemProcessor) {
                        // resyncing subitem is still processing, but may be has solution:
                        subItemResult = ((ItemProcessor) subitemObject).getResult();
                    } else {
                        // we has not solution:
                        subItemResult = null;
                    }

                    debug("for subitem " + hid + " found state: " + (subItemResult == null ? null : subItemResult.state));
                    // we answer only states with consensus, in other cases we answer ItemState.UNDEFINED
                    if (subItemResult != null) {
                        subItemState = subItemResult.state.isConsensusFound() ? subItemResult.state : ItemState.UNDEFINED;
                    } else {
                        subItemState = ItemState.UNDEFINED;
                    }

                    debug("answer for subitem: " + hid + " will be : " + subItemState);
                    answersForItems.put(hid, subItemState);
                }

                network.deliver(
                        from,
                        new ItemResyncNotification(myInfo, notification.getItemId(), answersForItems, false)
                );
            }

        } else if (itemObject instanceof ItemProcessor) {
            ItemProcessor ip = (ItemProcessor) itemObject;
            debug("found ItemProcessor for parent item with state: " + ip.getState() + ", and it processing state is: " + ip.processingState);
            if(ip.processingState.isResyncing()) {
                ip.lock(() -> {
                    for (HashId hid : itemsToResync.keySet()) {
                        ip.resyncVote(hid, from, itemsToResync.get(hid));
                    }

                    return null;
                });
            }

        }
    }


    /**
     * Obtain got common item notification: looking for result or item processor and register vote
     *
     * @param notification common item notification
     *
     */
    private final void obtainCommonNotification(ItemNotification notification) {

        debug("got " + notification);

        // get processor, create if need
        // register my vote
        Object x = checkItemInternal(notification.getItemId(), null, true);
        NodeInfo from = notification.getFrom();

        if (x instanceof ItemResult) {
            ItemResult r = (ItemResult) x;
            // we have solution and need not answer, we answer if requested:
            if (notification.answerIsRequested()) {
                network.deliver(
                        from,
                        new ItemNotification(myInfo, notification.getItemId(), r, false)
                );
            }
        } else if (x instanceof ItemProcessor) {
            ItemProcessor ip = (ItemProcessor) x;
            ItemResult result = notification.getItemResult();
            ip.lock(() -> {
                debug("found ItemProcessor for item " + notification.getItemId() + "  with state: " + ip.getState()
                        + ", it processing state is: " + ip.processingState
                        + ", have a copy: " + (ip.item != null));

                // we might still need to download and process it
                if (result.haveCopy) {
                    ip.addToSources(from);
                }
                if (result.state != ItemState.PENDING)
                    ip.vote(from, result.state);
                else
                    log.e("pending vote on " + notification.getItemId() + " from " + from);

                // We answer only if (1) answer is requested and (2) we have position on the subject:
                if (notification.answerIsRequested() && ip.record.getState() != ItemState.PENDING) {
                    network.deliver(
                            from,
                            new ItemNotification(myInfo,
                                    notification.getItemId(),
                                    ip.getResult(),
                                    ip.needsVoteFrom(from))
                    );
                }
                return null;
            });
        } else {
            debug("impossible state: onNotification can't have invalid state from local check\n" + x);
        }
    }

    protected Object checkItemInternal(@NonNull HashId itemId) {
        return checkItemInternal(itemId, null);
    }

    protected Object checkItemInternal(@NonNull HashId itemId, Approvable item) {
        return checkItemInternal(itemId, item, false);
    }

    protected Object checkItemInternal(@NonNull HashId itemId, Approvable item, boolean autoStart) {
        return checkItemInternal(itemId, item, autoStart, false);
    }

    /**
     * Optimized for various usages, check the item, start processing as need, return object depending on the current
     * state. Note that actuall error codes are set to the item itself.
     *
     * @param itemId    item to check the state.
     * @param item      provide item if any, can be null. Default is null.
     * @param autoStart - create new ItemProcessor if not exist. Default is false.
     * @param ommitItemResult - do not return ItemResult for processed item,
     *                        create new ItemProcessor instead (if autoStart is true). Default is false.
     *
     * @return instance of {@link ItemProcessor} if the item is being processed (also if it was started by the call),
     *         {@link ItemResult} if it is already processed or can't be processed, say, created_at field is too far in
     *         the past, in which case result state will be {@link ItemState#DISCARDED}.
     */
    protected Object checkItemInternal(@NonNull HashId itemId, Approvable item, boolean autoStart, boolean ommitItemResult) {
        try {
            // first, let's lock to the item id:
            return ItemLock.synchronize(itemId, (lock) -> {
                ItemProcessor ip = processors.get(itemId);
                if (ip != null) {
                    debug("existing IP found for " + itemId);
                    return ip;
                }

                // if we want to get already processed result for item
                if(!ommitItemResult) {
                    StateRecord r = ledger.getRecord(itemId);
                    // if it is not pending, it means it is already processed:
                    if (r != null && !r.isPending()) {
                        debug("record for " + itemId + " is already processed: " + r.getState());
                        // it is, and we may still have it cached - we do not put it again:
                        return new ItemResult(r, cache.get(itemId) != null);
                    }

                    debug("no record in ledger found for " + itemId.toBase64String());

                    // we have no consensus on it. We might need to find one, after some precheck.
                    // The contract should not be too old to process:
                    if (item != null &&
                            item.getCreatedAt().isBefore(ZonedDateTime.now().minus(config.getMaxItemCreationAge()))) {
                        // it is too old - client must manually check other nodes. For us it's unknown
                        item.addError(Errors.EXPIRED, "created_at", "too old");
                        return ItemResult.DISCARDED;
                    }
                }

                // if we want to create new ItemProcessor
                if (autoStart) {
                    if (item != null) {
                        synchronized (cache) {
                            cache.put(item);
                        }
                    }
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
        synchronized (cache) {
            @Nullable Approvable i = cache.get(itemId);
            if (i == null) {
                debug("cache miss: ");
            }
            return i;
        }
//        return cache.get(itemId);
    }

    public int countElections() {
        return processors.size();
    }

    public ItemCache getCache() {
        return cache;
    }

    public Ledger getLedger() {
        return ledger;
    }

    private class ItemProcessor {

        private final HashId itemId;
        private Approvable item;
        private final StateRecord record;
        private final ItemState stateWas;
        private ItemProcessingState processingState;
        private Set<NodeInfo> sources = new HashSet<>();

        /**
         * Set true if you resyncing item itself (item will be rollbacked with ItemProcessor if resync will failed).
         */
        private boolean resyncItselfOnly;

        private Set<NodeInfo> positiveNodes = new HashSet<>();
        private Set<NodeInfo> negativeNodes = new HashSet<>();

        private HashMap<HashId, ResyncingItem> resyncingItems = new HashMap<>();

        private List<StateRecord> lockedToRevoke = new ArrayList<>();
        private List<StateRecord> lockedToCreate = new ArrayList<>();

        private Instant pollingExpiresAt;
        private Instant consensusReceivedExpiresAt;
        private Instant resyncExpiresAt;

        private boolean alreadyChecked;

        private final AsyncEvent<Void> downloadedEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> doneEvent = new AsyncEvent<>();

        private final Object mutex;
        private final Object resyncMutex;

        private ScheduledFuture<?> downloader;
        private ScheduledFuture<?> poller;
        private ScheduledFuture<?> consensusReceivedChecker;
        private ScheduledFuture<?> resyncer;

        public ItemProcessor(HashId itemId, Approvable item, Object lock) {

            mutex = lock;
            resyncMutex = new Object();

            processingState = ItemProcessingState.INIT;
            this.itemId = itemId;
            if (item == null)
                item = cache.get(itemId);
            this.item = item;

            StateRecord recordWas = ledger.getRecord(itemId);
            if (recordWas != null) {
                stateWas = recordWas.getState();
            } else {
                stateWas = ItemState.UNDEFINED;
            }
            debug("Create ItemProcessor for " + itemId + " state was: " + stateWas);

            record = ledger.findOrCreate(itemId);
            debug("and state became: " + record.getState());

            pollingExpiresAt = Instant.now().plus(config.getMaxElectionsTime());
            consensusReceivedExpiresAt = Instant.now().plus(config.getMaxConsensusReceivedCheckTime());
            resyncExpiresAt = Instant.now().plus(config.getMaxResyncTime());

            alreadyChecked = false;

            if (this.item != null)
                executorService.submit(() -> itemDownloaded());
        }

        //////////// download section /////////////

        private void pulseDownload() {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    processingState = ItemProcessingState.DOWNLOADING;

                    synchronized (mutex) {
                        if (item == null && (downloader == null || downloader.isDone())) {
                            debug("submitting download");
                            downloader = (ScheduledFuture<?>) executorService.submit(() -> download());
                        }
                    }
                }
            }
        }

        private void download() {
            if(processingState.canContinue()) {
                while (!isPollingExpired() && item == null) {
                    if (sources.isEmpty()) {
                        log.e("empty sources for download tasks, stopping");
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
                                Thread.sleep(100);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        private final void itemDownloaded() {
            if(processingState.canContinue()) {
                synchronized (cache) {
                    cache.put(item);
                }
                checkItem();
                downloadedEvent.fire();
            }
        }

        private void stopDownloader() {
            if (downloader != null)
                downloader.cancel(false);
        }

        //////////// check state section /////////////

        private final synchronized void checkItem() {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    if (alreadyChecked) {
                        throw new RuntimeException("Check already processed");
                    }

                    processingState = ItemProcessingState.CHECKING;

                    debug("Checking " + itemId + " state is " + record.getState());

                    // Check the internal state
                    // Too bad if basic check isn't passed, we will not process it further
                    HashMap<HashId, StateRecord> itemsToResync = new HashMap<>();
                    boolean needToResync = false;

                    try {
                        debug("Contract limit: " + Contract.getTestQuantaLimit());
                        if (item.check()) {

                            itemsToResync = isNeedToResync(true);
                            needToResync = !itemsToResync.isEmpty();

                            // If no need to resync subItems, check them
                            if (!needToResync) {
                                checkSubItems();
                            }
                        } else {
                            debug("Found " + item.getErrors() + " errors:");
                            Collection<ErrorRecord> errors = item.getErrors();
                            errors.forEach(e -> debug("Found error: " + e));
                        }
                    } catch (Quantiser.QuantiserException e) {
                        emergencyBreak();
                        return;
                    }
                    alreadyChecked = true;

                    debug("Checking for resync, item " + itemId + " is need to resync: " + needToResync);

                    if (!needToResync) {
                        commitCheckedAndStartPolling();
                    } else {
                        debug("Some of sub-contracts was not approved, its should be resync");
                        for (HashId hid : itemsToResync.keySet()) {
                            addItemToResync(hid, itemsToResync.get(hid));
                        }

                        pulseResync();
                    }
                }
            }
        }

        private final void pulseCheckIfItemsResynced() {
            if(processingState.canContinue()) {
                synchronized (resyncMutex) {
                    for (HashId hid : resyncingItems.keySet()) {
                        resyncingItems.get(hid).finishEvent.addConsumer(i -> onResyncItemFinished(i));
                    }
                }
            }
        }

        private final void onResyncItemFinished(ResyncingItem ri) {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    debug("resync finished for subitem " + ri.hashId + ", state: " + ri.getItemState() + " own (parent) state: " + getState());

                    int numFinished = 0;
                    synchronized (resyncMutex) {
                        for (ResyncingItem rit : resyncingItems.values()) {
                            if (rit.isCommitFinished())
                                numFinished++;
                        }
                    }
                    debug("Num finished resync items " + numFinished + "/" + resyncingItems.size() + ", processingState is " + processingState);

                    if (resyncingItems.size() == numFinished && processingState.isGotResyncedState()) {
                        debug("All subItems resynced and states committed");

                        processingState = ItemProcessingState.CHECKING;

                        // if we was resyncing itself (not own subitems) and state from network was undefined - rollback state
                        if (resyncItselfOnly) {
                            if (itemId.equals(ri.hashId)) {
                                if (ri.getResyncingState() == ResyncingItemProcessingState.COMMIT_FAILED) {
                                    rollbackChanges(stateWas);
                                    return;
                                }
                            }
                            processingState = ItemProcessingState.FINISHED;
                            close();
                        } else {
                            try {
                                checkSubItems();
                            } catch (Exception e) {
                                debug("resync finishing error: " + e.getMessage());
                                e.printStackTrace();
                            }
                            commitCheckedAndStartPolling();
                        }
                    }
                }
            }
        }

        private final void checkSubItems() {// check the referenced items
            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {
                    for (ReferenceModel refModel : item.getReferencedItems()) {
                        HashId id = refModel.contract_id;
                        if(refModel.type == ReferenceModel.TYPE_EXISTING && id != null) {
                            if (!ledger.isApproved(id)) {
                                item.addError(Errors.BAD_REF, id.toString(), "reference not approved");
                            }
                        }
                    }
                    // check revoking items
                    for (Approvable a : item.getRevokingItems()) {
                        StateRecord r = record.lockToRevoke(a.getId());
                        if (r == null) {
                            item.addError(Errors.BAD_REVOKE, a.getId().toString(), "can't revoke");
                        } else {
                            if (!lockedToRevoke.contains(r))
                                lockedToRevoke.add(r);
                        }
                    }
                    // check new items
//                    try {
                        for (Approvable newItem : item.getNewItems()) {
                            if (!newItem.getErrors().isEmpty()) {
                                item.addError(Errors.BAD_NEW_ITEM, newItem.getId().toString(), "bad new item: not passed check");
                            } else {
                                StateRecord r = record.createOutputLockRecord(newItem.getId());
                                if (r == null) {
                                    item.addError(Errors.NEW_ITEM_EXISTS, newItem.getId().toString(), "new item exists in ledger");
                                } else {
                                    if (!lockedToCreate.contains(r))
                                        lockedToCreate.add(r);
                                }
                            }
                        }
//                    } catch (Quantiser.QuantiserException e) {
//                        emergencyBreak();
//                        return;
//                    }

                    debug("Checking subitems of item " + itemId
                            + ", state: " + record.getState() +
                            ", errors: " + item.getErrors().size());
                }
            }
        }

        private final void commitCheckedAndStartPolling() {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    boolean checkPassed = item.getErrors().isEmpty();

                    debug("Checked item " + itemId + ", checkPassed: " + checkPassed
                            + ", state: " + record.getState() +
                            ", errors: " + item.getErrors().size());

                    if (!checkPassed) {
                        informer.inform(item);
                    }

                    synchronized (mutex) {
                        if (record.getState() == ItemState.PENDING) {
                            if (checkPassed) {
                                setState(ItemState.PENDING_POSITIVE);
                            } else {
                                setState(ItemState.PENDING_NEGATIVE);
                            }
                        }
                    }

                    record.setExpiresAt(item.getExpiresAt());
                    record.save();

                    vote(myInfo, record.getState());
                    broadcastMyState();
                    pulseStartPolling();
                }
            }
        }

        public HashMap<HashId, StateRecord> isNeedToResync(boolean baseCheckPassed) {
            if(processingState.canContinue()) {
                HashMap<HashId, StateRecord> unknownParts = new HashMap<>();
                HashMap<HashId, StateRecord> knownParts = new HashMap<>();
                if (baseCheckPassed) {
                    // check the referenced items
                    for (ReferenceModel refModel : item.getReferencedItems()) {
                        HashId id = refModel.contract_id;
                        if(refModel.type == ReferenceModel.TYPE_EXISTING && id != null) {
                            StateRecord r = ledger.getRecord(id);
                            debug(">> referenced subitem " + id + " is " + (r != null ? r.getState() : null));

                            if (r == null || !r.getState().isConsensusFound()) {
                                unknownParts.put(id, r);
                            } else {
                                knownParts.put(id, r);
                            }
                        }
                    }
                    // check revoking items
                    for (Approvable a : item.getRevokingItems()) {
                        StateRecord r = ledger.getRecord(a.getId());
                        debug(">> revoking subitem " + a.getId() + " is " + (r != null ? r.getState() : null));

                        if (r == null || !r.getState().isConsensusFound()) {
                            unknownParts.put(a.getId(), r);
                        } else {
                            knownParts.put(a.getId(), r);
                        }
                    }
                } else {
                    debug("Found " + item.getErrors().size() + " errors:");
                    Collection<ErrorRecord> errors = item.getErrors();
                    errors.forEach(e -> debug("Found error: " + e));
                }
                boolean needToResync = false;
                // contract is complex and consist from parts
                if (unknownParts.size() + knownParts.size() > 0) {
                    needToResync = baseCheckPassed &&
                            unknownParts.size() > 0 &&
                            knownParts.size() >= config.getKnownSubContractsToResync();
                }
                debug("is item " + itemId + " need to resync: " + needToResync + ", state: " + record.getState() +
                        ", errors: " + item.getErrors().size() +
                        ", unknownParts: " + unknownParts.size() +
                        ", knownParts: " + knownParts.size() +
                        ", num knowns to resync (>=): " + config.getKnownSubContractsToResync());

                if (needToResync)
                    return unknownParts;
            }
            return new HashMap<>();
        }

        //////////// polling section /////////////

        private final void pulseStartPolling() {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    processingState = ItemProcessingState.POLLING;

                    // at this point the item is with us, so we can start
                    synchronized (mutex) {
                        if (!processingState.isProcessedToConsensus()) {
                            long millis = config.getPollTime().toMillis();
                            poller = executorService.scheduleAtFixedRate(() -> sendStartPollingNotification(), millis, millis, TimeUnit.MILLISECONDS);
                        }
                    }
                }
            }
        }

        private final void sendStartPollingNotification() {
            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {
                    synchronized (mutex) {
                        if (isPollingExpired()) {
                            // cancel by timeout expired
                            debug("WARNING: consensus not found in maximum allowed time, cancelling " + itemId);

                            processingState = ItemProcessingState.GOT_CONSENSUS;

                            stopPoller();
                            stopDownloader();
                            rollbackChanges(ItemState.UNDEFINED);
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
            }
        }

        private final void vote(NodeInfo node, ItemState state) {
            if(processingState.canContinue()) {
                boolean positiveConsensus = false;
                boolean negativeConsensus = false;
                synchronized (mutex) {
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

                    if (processingState.isProcessedToConsensus()) {
                        debug("consensus already found, but vote for " + itemId + " from " + node + ": " + state + " > "
                                + positiveNodes.size() + "/" + negativeNodes.size());
                        checkIfAllReceivedConsensus();
                        removeSelf();
                        return;
                    }

                    if (negativeNodes.size() >= config.getNegativeConsensus()) {
                        negativeConsensus = true;
                        processingState = ItemProcessingState.GOT_CONSENSUS;
                    } else if (positiveNodes.size() >= config.getPositiveConsensus()) {
                        positiveConsensus = true;
                        processingState = ItemProcessingState.GOT_CONSENSUS;
                    }
                    debug("vote for " + itemId + " from " + node + ": " + state + " > "
                            + positiveNodes.size() + "/" + negativeNodes.size() +
                            ", consFound=" + processingState.isProcessedToConsensus() + ": positive=" + positiveConsensus +
                            ", processingState=" + processingState);
                    if (!processingState.isProcessedToConsensus())
                        return;
                }
                if (positiveConsensus) {
                    approveAndCommit();
                } else if (negativeConsensus) {
                    rollbackChanges(ItemState.DECLINED);
                } else
                    throw new RuntimeException("error: consensus reported without consensus");
            }
        }

        private final void approveAndCommit() {
            if(processingState.canContinue()) {
                // todo: fix logic to surely copy approving item dependency. e.g. download original or at least dependencies
                // first we need to flag our state as approved
                setState(ItemState.APPROVED);
                executorService.submit(() -> downloadAndCommit());
            }
        }

        private void downloadAndCommit() {
            if(processingState.canContinue()) {
                // it may happen that consensus is found earlier than item is download
                // we still need item to fix all its relations:
                try {
                    debug("download and commit item " + itemId + " state: " + getState());
                    if (item == null) {
                        // If positive consensus os found, we can spend more time for final download, and can try
                        // all the network as the source:
                        pollingExpiresAt = Instant.now().plus(config.getMaxDownloadOnApproveTime());
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
                    debug("approval done for " + itemId + " : " + getState() + " have a copy " + (item == null));
                } catch (TimeoutException | InterruptedException e) {
                    debug("commit: failed to load item " + itemId + " ledger will not be altered, the record will be destroyed");
                    setState(ItemState.UNDEFINED);
                    record.destroy();
                }
                close();
            }
        }

        private void rollbackChanges(ItemState newState) {
            synchronized (ledgerRollbackLock) {
                debug("Rollbacks to: " + itemId + " as " + newState + " consensus: " + positiveNodes.size() + "/" + negativeNodes.size());
                ledger.transaction(() -> {
                    debug("unlocking to revoke");
                    for (StateRecord r : lockedToRevoke)
                        r.unlock().save();
                    debug("unlocked to revoke");
                    lockedToRevoke.clear();
                    debug("locked to revoke cleared");
                    // form created records, we touch only these that we have actually created
                    debug("unlocking to create");
                    for (StateRecord r : lockedToCreate) {
                        debug("unlocking to create, item: " + r.getId() + " state: " + r.getState());
                        r.unlock().save();
                    }
                    debug(" unlocked to create");
                    // todo: concurrent modification can happen here!
                    lockedToCreate.clear();
                    debug("locked to create cleared");
                    debug("setting state: " + newState.name());
                    setState(newState);
                    ZonedDateTime expiration = ZonedDateTime.now()
                            .plus(newState == ItemState.REVOKED ?
                                    config.getRevokedItemExpiration() : config.getDeclinedItemExpiration());
                    record.setExpiresAt(expiration);
                    debug("saving ");
                    record.save(); // TODO: current implementation will cause an inner dbPool.db() invocation
                    debug("saved ");
                    return null;
                });
                close();
            }
        }

        private void stopPoller() {
            if (poller != null)
                poller.cancel(false);
        }

        private boolean isPollingExpired() {
            return pollingExpiresAt.isBefore(Instant.now());
        }

        //////////// sending new state section /////////////

        private final void pulseSendNewConsensus() {
            if(processingState.canContinue()) {

                processingState = ItemProcessingState.SENDING_CONSENSUS;

                synchronized (mutex) {
                    long millis = config.getConsensusReceivedCheckTime().toMillis();
                    consensusReceivedChecker = executorService.scheduleAtFixedRate(() -> sendNewConsensusNotification(),
                            millis, millis, TimeUnit.MILLISECONDS);
                }
            }
        }

        private final void sendNewConsensusNotification() {
            if(processingState.canContinue()) {
                synchronized (mutex) {
                    if (processingState.isConsensusSentAndReceived())
                        return;
                    if (isConsensusReceivedExpired()) {
                        // cancel by timeout expired
                        debug("WARNING: Checking if all nodes got consensus is timed up, cancelling " + itemId);
                        processingState = ItemProcessingState.FINISHED;
                        stopConsensusReceivedChecker();
                        return;
                    }
                }
                // at this point we should requery the nodes that did not yet answered us
                Notification notification = new ItemNotification(myInfo, itemId, getResult(), true);
                network.eachNode(node -> {
                    if (!positiveNodes.contains(node) && !negativeNodes.contains(node)) {
                        debug("Item: " + itemId + " Unknown consensus on the node " + node.getNumber() + " , deliver new consensus with result: " + getResult());
                        network.deliver(node, notification);
                    }
                });
            }
        }

        private final Boolean checkIfAllReceivedConsensus() {
            if(processingState.canContinue()) {
                Boolean allReceived = network.allNodes().size() == positiveNodes.size() + negativeNodes.size();

                debug("is for item " + itemId + " all got consensus: " + allReceived + " : " + network.allNodes().size() + "/" + positiveNodes.size() + "+" + negativeNodes.size());
                if (allReceived) {
                    processingState = ItemProcessingState.FINISHED;
                    stopConsensusReceivedChecker();
                }

                return allReceived;
            }

            return true;
        }

        private boolean isConsensusReceivedExpired() {
            return consensusReceivedExpiresAt.isBefore(Instant.now());
        }

        private void stopConsensusReceivedChecker() {
            if(consensusReceivedChecker != null)
                consensusReceivedChecker.cancel(false);
        }

        //////////// resync section /////////////

        public final void pulseResync() {
            pulseResync(false);
        }

        /**
         * Start resyncing.
         *
         * @param resyncItself - set true if you want to resync item itself.
         */
        public final void pulseResync(boolean resyncItself) {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    this.resyncItselfOnly = resyncItself;
                    if (resyncItself) {
                        addItemToResync(itemId, record);
                    }

                    processingState = ItemProcessingState.RESYNCING;

                    pulseCheckIfItemsResynced();

                    for (ResyncingItem ri : resyncingItems.values()) {
                        // vote itself
                        if (ri.needsResyncVoteFrom(myInfo)) {
                            if (ri.getItemState().isConsensusFound())
                                resyncVote(ri.getId(), myInfo, ri.getItemState());
                            else
                                resyncVote(ri.getId(), myInfo, ItemState.UNDEFINED);
                        }
                    }

                    synchronized (mutex) {
                        long millis = config.getResyncTime().toMillis();
                        resyncer = executorService.scheduleAtFixedRate(() -> sendResyncNotification(),
                                millis, millis, TimeUnit.MILLISECONDS);
                    }
                }
            }
        }

        private final void sendResyncNotification() {
            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {
                    synchronized (mutex) {
                        if (processingState.isGotResyncedState())
                            return;
                        if (isResyncExpired()) {
                            // cancel by timeout expired
                            debug("WARNING: Resyncing is timed up, cancelling " + itemId);
                            processingState = ItemProcessingState.GOT_RESYNCED_STATE;
                            for (ResyncingItem ri : resyncingItems.values()) {
                                ri.closeByTimeout();
                            }
                            stopResync();
                            return;
                        }
                    }
                    network.eachNode(node -> {
                        HashMap<HashId, ItemState> itemsToResync = new HashMap<>();
                        for (HashId hid : resyncingItems.keySet()) {
                            if (resyncingItems.get(hid).needsResyncVoteFrom(node)) {
                                itemsToResync.put(hid, resyncingItems.get(hid).getItemState());
                            }
                        }
                        if (itemsToResync.size() > 0) {
                            ItemResyncNotification notification = new ItemResyncNotification(myInfo, itemId, itemsToResync, true);
                            debug("Resync at the " + node.getNumber() + " for " + itemId + ", and subitems: " + itemsToResync);
                            network.deliver(node, notification);
                        }
                    });
                } else {
                    stopResync();
                }
            }
        }

        private final void resyncVote(HashId hid, NodeInfo node, ItemState state) {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    synchronized (resyncMutex) {
                        if (resyncingItems.containsKey(hid))
                            resyncingItems.get(hid).resyncVote(node, state);

                        boolean isResyncPollingFinished = true;
                        for (ResyncingItem ri : resyncingItems.values()) {
                            if (!ri.isResyncPollingFinished()) {
                                isResyncPollingFinished = false;
                                break;
                            }
                        }

                        if (isResyncPollingFinished) {
                            processingState = ItemProcessingState.GOT_RESYNCED_STATE;
                            stopResync();
                        }
                    }
                } else {
                    stopResync();
                }
            }
        }

        private boolean isResyncExpired() {
            return resyncExpiresAt.isBefore(Instant.now());
        }

        private void stopResync() {
            if (resyncer != null)
                resyncer.cancel(false);
        }

        public void addItemToResync(HashId hid, StateRecord record) {
            if(processingState.canContinue()) {
                try {
                    synchronized (resyncMutex) {
                        if (!resyncingItems.containsKey(hid)) {
                            debug("item " + hid + " will be resynced, state: " + (record != null ? record.getState() : null));
                            resyncingItems.put(hid, new ResyncingItem(hid, record));
                        } else {
                            debug("item " + hid + " already resyncing");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        //////////// common section /////////////

        private long getMillisLeft() {
            return pollingExpiresAt.toEpochMilli() - Instant.now().toEpochMilli();
        }

        private final void broadcastMyState() {
            if(processingState.canContinue()) {
                network.broadcast(myInfo, new ItemNotification(myInfo, itemId, getResult(), true));
            }
        }

        private void close() {
            debug("closing " + itemId + " : " + getState() + " it was self-resync: " + resyncItselfOnly);

            stopPoller();

            doneEvent.fire();

            if(processingState.canContinue()) {
                // If we not just resynced itslef
                if (!resyncItselfOnly) {
                    checkIfAllReceivedConsensus();
                    debug("after check if all received consensus for item " + itemId + " processing state: " + processingState);
                    if (processingState.isGotConsensus()) {
                        pulseSendNewConsensus();
                    } else {
                        removeSelf();
                    }
                } else {
                    removeSelf();
                }
            } else {
                removeSelf();
            }
        }


        /**
         * Emergency break all processes and remove self.
         */
        private void emergencyBreak() {

            debug("Emergency break of all processes for item " + itemId);

            processingState = ItemProcessingState.EMERGENCY_BREAK;

            stopDownloader();
            stopPoller();
            stopConsensusReceivedChecker();
            stopResync();
            rollbackChanges(stateWas);
        }

        private ItemState getState() {
            return record.getState();
        }

        private final void setState(ItemState newState) {
            synchronized (mutex) {
                record.setState(newState);
            }
        }

        private final void removeSelf() {
            debug("Continue closing, processing state: " + processingState + " for item " + itemId);
            if(processingState.canRemoveSelf()) {
                processors.remove(itemId);

                stopDownloader();
                stopPoller();
                stopConsensusReceivedChecker();
                stopResync();

                debug("closed " + itemId.toBase64String() + " processing state: " + processingState);
            }
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


    public enum ItemProcessingState {
        INIT,
        DOWNLOADING,
        CHECKING,
        RESYNCING,
        GOT_RESYNCED_STATE,
        POLLING,
        GOT_CONSENSUS,
        SENDING_CONSENSUS,
        FINISHED,
        EMERGENCY_BREAK;


        /**
         * Status should break other processes and possibility to launch processes.
         *
         * @return
         */
        public boolean isProcessedToConsensus() {
            switch (this) {
                case GOT_CONSENSUS:
                case SENDING_CONSENSUS:
                case FINISHED:
                    return true;
            }
            return false;
        }

        public boolean isConsensusSentAndReceived() {
            return this == FINISHED;
        }

        public boolean isGotConsensus() {
            return this == GOT_CONSENSUS;
        }

        public boolean isGotResyncedState() {
            return this == GOT_RESYNCED_STATE;
        }

        public boolean isResyncing() {
            return this == RESYNCING;
        }

        public boolean canContinue() {
            return this != EMERGENCY_BREAK;
        }

        public boolean canRemoveSelf() {
            switch (this) {
                case EMERGENCY_BREAK:
                case FINISHED:
                    return true;
            }
            return false;
        }
    }


    /**
     * Class for resyncing item, used at the ItemProcessor for subItems of main (parent) item.
     */
    private class ResyncingItem {

        private HashId hashId;
        private StateRecord record;
        private final ItemState stateWas;
        private ResyncingItemProcessingState resyncingState;

        private final AsyncEvent<ResyncingItem> finishEvent = new AsyncEvent<>();

        private HashMap<ItemState, Set<NodeInfo>> resyncNodes = new HashMap<>();

        private final Object mutex = new Object();

        public ResyncingItem(HashId hid, StateRecord record) {
            resyncingState = ResyncingItemProcessingState.WAIT_FOR_VOTES;

            this.hashId = hid;
            this.record = record;

            StateRecord recordWas = ledger.getRecord(hid);
            if (recordWas != null) {
                stateWas = recordWas.getState();
            } else {
                stateWas = ItemState.UNDEFINED;
            }

            resyncNodes.put(ItemState.APPROVED, new HashSet<>());
            resyncNodes.put(ItemState.REVOKED, new HashSet<>());
            resyncNodes.put(ItemState.DECLINED, new HashSet<>());
            resyncNodes.put(ItemState.UNDEFINED, new HashSet<>());
        }

        private final void resyncVote(NodeInfo node, ItemState state) {
            boolean approvedConsenus = false;
            boolean revokedConsenus = false;
            boolean declinedConsenus = false;
            boolean undefinedConsenus = false;
            synchronized (mutex) {
                for (ItemState is : resyncNodes.keySet()) {
                    resyncNodes.get(is).remove(node);
                }
                if(!resyncNodes.containsKey(state)) {
                    resyncNodes.put(state, new HashSet<>());
                }
                resyncNodes.get(state).add(node);

                if (isResyncPollingFinished()) {
                    return;
                }

                if (resyncNodes.get(ItemState.REVOKED).size() >= config.getPositiveConsensus()) {
                    revokedConsenus = true;
                    resyncingState = ResyncingItemProcessingState.PENDING_TO_COMMIT;
                } else if (resyncNodes.get(ItemState.DECLINED).size() >= config.getPositiveConsensus()) {
                    declinedConsenus = true;
                    resyncingState = ResyncingItemProcessingState.PENDING_TO_COMMIT;
                } else if (resyncNodes.get(ItemState.APPROVED).size() >= config.getPositiveConsensus()) {
                    approvedConsenus = true;
                    resyncingState = ResyncingItemProcessingState.PENDING_TO_COMMIT;
                } else if (resyncNodes.get(ItemState.UNDEFINED).size() >= config.getResyncBreakConsensus()) {
                    undefinedConsenus = true;
                    resyncingState = ResyncingItemProcessingState.PENDING_TO_COMMIT;
                }
                debug("resync vote for " + hashId + " from " + node + ": " + state + " (APPROVED/REVOKED/DECLINED/UNDEFINED)> " +
                        resyncNodes.get(ItemState.APPROVED).size() + "/" +
                        resyncNodes.get(ItemState.REVOKED).size() +  "/" +
                        resyncNodes.get(ItemState.DECLINED).size() +  "/" +
                        resyncNodes.get(ItemState.UNDEFINED).size() + " resync state=" + resyncingState +
                        ": approvedConsenus=" + approvedConsenus +
                        ": revokedConsenus=" + revokedConsenus +
                        ": declinedConsenus=" + declinedConsenus +
                        ": undefinedConsenus=" + undefinedConsenus);
                if (!isResyncPollingFinished())
                    return;
            }
            if (revokedConsenus) {
                executorService.submit(() -> resyncAndCommit(ItemState.REVOKED));
            } else if (declinedConsenus) {
                executorService.submit(() -> resyncAndCommit(ItemState.DECLINED));
            } else if (approvedConsenus) {
                executorService.submit(() -> resyncAndCommit(ItemState.APPROVED));
            } else if (undefinedConsenus) {
                executorService.submit(() -> resyncAndCommit(ItemState.UNDEFINED));
            } else
                throw new RuntimeException("error: resync consensus reported without consensus");
        }

        private final void resyncAndCommit(ItemState committingState) {

            resyncingState = ResyncingItemProcessingState.IS_COMMITTING;

            final AtomicInteger latch = new AtomicInteger(config.getResyncThreshold());
            final AtomicInteger rest = new AtomicInteger(config.getResyncBreakConsensus());

            final Average startDateAvg = new Average();
            final Average expiresAtAvg = new Average();

            executorService.submit(()->{
                Set<NodeInfo> rNodes = new HashSet<>();
                Set<NodeInfo> nowNodes = resyncNodes.get(committingState);

                // make local set of nodes to prevent changing set of nodes while commiting
                synchronized (resyncNodes) {
                    for (NodeInfo ni : nowNodes) {
                        rNodes.add(ni);
                    }
                }
                debug("After resync for " + getId() + "try commit state: " + committingState);
                debug("resync latch is set to " + latch);
                debug("resync rest is set to " + rest);
                for (NodeInfo ni : rNodes) {
                    if (ni != null) {
                        try {
                            ItemResult r = network.getItemState(ni, hashId);
                            debug("from " + ni + " for " + getId() + " got result: " + r);
                            if (r != null && r.state == committingState && r.state.isConsensusFound()) {

                                startDateAvg.update(r.createdAt.toEpochSecond());
                                expiresAtAvg.update(r.expiresAt.toEpochSecond());

                                int count = latch.decrementAndGet();
                                if (count < 1) {
                                    debug("resyncing calculations finished for " + hashId);
                                    ZonedDateTime createdAt = ZonedDateTime.ofInstant(
                                            Instant.ofEpochSecond((long) startDateAvg.average()), ZoneId.systemDefault());
                                    ZonedDateTime expiresAt = ZonedDateTime.ofInstant(
                                            Instant.ofEpochSecond((long) expiresAtAvg.average()), ZoneId.systemDefault());

                                    ledger.findOrCreate(hashId).setState(committingState)
                                            .setCreatedAt(createdAt)
                                            .setExpiresAt(expiresAt)
                                            .save();

                                    debug("resyncing commit success for " + hashId + " with state " + committingState);

                                    resyncingState = ResyncingItemProcessingState.COMMIT_SUCCESSFUL;
                                    break;
                                }
                            } else {
                                debug("item " + hashId + " has not approved from " + ni);
                                if (rest.decrementAndGet() < 1) {
                                    debug("No resync consensus for " + hashId + ", fail. State was: " + stateWas);

                                    resyncingState = ResyncingItemProcessingState.COMMIT_FAILED;
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            debug("failed to get state for " + getId() + " from " + ni + ": " + e);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                finishEvent.fire(this);
            });
        }

        public void closeByTimeout() {
            resyncingState = ResyncingItemProcessingState.COMMIT_FAILED;
            finishEvent.fire(this);
        }

        /**
         * true if we need to get resync vote from a node
         *
         * @param node we might need vote from
         *
         * @return
         */
        public boolean needsResyncVoteFrom(NodeInfo node) {
            return !resyncNodes.get(ItemState.APPROVED).contains(node) &&
                    !resyncNodes.get(ItemState.REVOKED).contains(node) &&
                    !resyncNodes.get(ItemState.DECLINED).contains(node) &&
                    !resyncNodes.get(ItemState.UNDEFINED).contains(node);
        }

        public ResyncingItemProcessingState getResyncingState() {
            return resyncingState;
        }

        /**
         * true if number of needed answers is got (for consensus or for break resyncing)
         * @return
         */
        public boolean isResyncPollingFinished() {
            return resyncingState != ResyncingItemProcessingState.WAIT_FOR_VOTES;
        }

        /**
         * true if item resynced and commit finished (with successful or fail).
         * @return
         */
        public boolean isCommitFinished() {
            return resyncingState == ResyncingItemProcessingState.COMMIT_SUCCESSFUL || resyncingState == ResyncingItemProcessingState.COMMIT_FAILED;
        }

        public HashId getId() {
            return hashId;
        }

        public ItemState getItemState() {
            if(record != null)
                return record.getState();

            return ItemState.UNDEFINED;
        }
    }


    public enum ResyncingItemProcessingState {
        WAIT_FOR_VOTES,
        PENDING_TO_COMMIT,
        IS_COMMITTING,
        COMMIT_SUCCESSFUL,
        COMMIT_FAILED
    }
}
