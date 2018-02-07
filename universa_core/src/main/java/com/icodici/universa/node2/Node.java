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
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.Reference;
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
import java.text.SimpleDateFormat;
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
    private final ParcelCache parcelCache;
    private final ItemInformer informer = new ItemInformer();

    private ConcurrentHashMap<HashId, ItemProcessor> processors = new ConcurrentHashMap();
    private ConcurrentHashMap<HashId, ParcelProcessor> parcelProcessors = new ConcurrentHashMap();

    private static ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(64);

    public Node(Config config, NodeInfo myInfo, Ledger ledger, Network network) {
        this.config = config;
        this.myInfo = myInfo;
        this.ledger = ledger;
        this.network = network;
        cache = new ItemCache(config.getMaxCacheAge());
        parcelCache = new ParcelCache(config.getMaxCacheAge());
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

        nodeDebug("register item: " + item.getId());
        Object x = checkItemInternal(item.getId(), null, item, true, true);
        return (x instanceof ItemResult) ? (ItemResult) x : ((ItemProcessor) x).getResult();
    }

    /**
     * Synchronous (blocking) parcel register.
     *
     * @param parcel to register/check state
     *
     * @return current (or last known) item state
     */
    public boolean registerParcel(Parcel parcel) {

        try {
            checkParcelInternal(parcel.getId(), parcel, true);
            return true;

        } catch (Exception e) {
            throw new RuntimeException("failed to process parcel", e);
        }
    }

    /**
     * Check the state of the item. This method does not start elections and can be safely called from a client.
     *
     * @param itemId item to check
     *
     * @return last known state
     */
    public @NonNull ItemResult checkItem(HashId itemId) {

        nodeDebug("check item: " + itemId);
        Object x = checkItemInternal(itemId);
        ItemResult ir = (x instanceof ItemResult) ? (ItemResult) x : ((ItemProcessor) x).getResult();
        ItemInformer.Record record = informer.takeFor(itemId);
        if (record != null)
            ir.errors = record.errorRecords;
        return ir;
    }

    public @NonNull ItemResult checkParcel(HashId parcelId) {

        nodeDebug("check parcel: " + parcelId);
        Object x = checkParcelInternal(parcelId);
        ItemResult ir = (x instanceof ItemResult) ? (ItemResult) x : ((ParcelProcessor) x).getPayloadResult();
        return ir;
    }

    public @NonNull Binder extendedCheckItem(HashId itemId) {

        ItemResult ir = checkItem(itemId);
        Binder result = Binder.of("itemResult", ir);
        if (ir != null && ir.state == ItemState.LOCKED) {
            ir.lockedById = ledger.getLockOwnerOf(itemId).getId();
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

        Object x = checkItemInternal(id, null, null, true, true, true);
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

        Object x = null;

        nodeDebug("wait item: " + itemId);
        x = checkItemInternal(itemId);
        if (x instanceof ItemProcessor) {
            if(!((ItemProcessor) x).isDone()) {
                ((ItemProcessor) x).doneEvent.await(millisToWait);
            }

            return ((ItemProcessor) x).getResult();
        }
        nodeDebug("it is not processor: " + x);
        return (ItemResult) x;
    }

    /**
     * Test use only. It the item is being elected, block until the item is processed with the consenus. Otherwise
     * returns state immediately.
     *
     * @param itemId parcel to check or wait for
     *
     * @return item state
     */
    public void waitParcel(HashId itemId, long millisToWait) throws TimeoutException, InterruptedException {

        Object x = null;

        // first check if item is processing as part of parcel
        x = checkParcelInternal(itemId);
        if (x instanceof ParcelProcessor) {
            nodeDebug("wait parcel " + itemId + " processingState: " + ((ParcelProcessor) x).processingState);
            if(!((ParcelProcessor) x).isDone())
            {
                ((ParcelProcessor) x).doneEvent.await(millisToWait);
            }
        }
    }

    /**
     * Notification handler. Checking type of notification and call needed obtainer.
     *
     */
    private final void onNotification(Notification notification) {

        if (notification instanceof ItemResyncNotification) {
            obtainResyncNotification((ItemResyncNotification) notification);
        }
        else if (notification instanceof ParcelNotification) {
            obtainParcelCommonNotification((ParcelNotification) notification);
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

        nodeDebug("got " + notification);

        HashMap<HashId, ItemState> itemsToResync = notification.getItemsToResync();
        HashMap<HashId, ItemState> answersForItems = new HashMap<>();

        NodeInfo from = notification.getFrom();

        // get processor, do not create new if not exist
        // register resync vote for waiting processor or deliver resync vote
        Object itemObject = checkItemInternal(notification.getItemId());

        if (itemObject instanceof ItemResult) {
            nodeDebug("found ItemResult for parent item with state: " + ((ItemResult) itemObject).state);

            if (notification.answerIsRequested()) {
                // iterate on subItems of parent item that need to resync (stored at ItemResyncNotification.getItemsToResync())
                for (HashId hid : itemsToResync.keySet()) {
                    nodeDebug("checking subitem : " + hid);
                    Object subitemObject = checkItemInternal(hid);
                    ItemResult subItemResult;
                    ItemState subItemState;
                    nodeDebug("checking subitem : " + hid + " object is: " + subitemObject);

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

                    nodeDebug("for subitem " + hid + " found state: " + (subItemResult == null ? null : subItemResult.state));
                    // we answer only states with consensus, in other cases we answer ItemState.UNDEFINED
                    if (subItemResult != null) {
                        subItemState = subItemResult.state.isConsensusFound() ? subItemResult.state : ItemState.UNDEFINED;
                    } else {
                        subItemState = ItemState.UNDEFINED;
                    }

                    nodeDebug("answer for subitem: " + hid + " will be : " + subItemState);
                    answersForItems.put(hid, subItemState);
                }

                network.deliver(
                        from,
                        new ItemResyncNotification(myInfo, notification.getItemId(), answersForItems, false)
                );
            }

        } else if (itemObject instanceof ItemProcessor) {
            ItemProcessor ip = (ItemProcessor) itemObject;
            nodeDebug("found ItemProcessor for parent item with state: " + ip.getState() + ", and it processing state is: " + ip.processingState);
            if(ip.processingState.isResyncing()) {
                ip.lock(() -> {
                    for (HashId hid : itemsToResync.keySet()) {
                        ip.resyncVote(hid, from, itemsToResync.get(hid));
                    }

                    return null;
                });
            } else {
                if (notification.answerIsRequested()) {
                    // iterate on subItems of parent item that need to resync (stored at ItemResyncNotification.getItemsToResync())
                    for (HashId hid : itemsToResync.keySet()) {
                        nodeDebug("checking subitem : " + hid);
                        Object subitemObject = checkItemInternal(hid);
                        ItemResult subItemResult;
                        ItemState subItemState;
                        nodeDebug("checking subitem : " + hid + " object is: " + subitemObject);

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

                        nodeDebug("for subitem " + hid + " found state: " + (subItemResult == null ? null : subItemResult.state));
                        // we answer only states with consensus, in other cases we answer ItemState.UNDEFINED
                        if (subItemResult != null) {
                            subItemState = subItemResult.state.isConsensusFound() ? subItemResult.state : ItemState.UNDEFINED;
                        } else {
                            subItemState = ItemState.UNDEFINED;
                        }

                        nodeDebug("answer for subitem: " + hid + " will be : " + subItemState);
                        answersForItems.put(hid, subItemState);
                    }

                    network.deliver(
                            from,
                            new ItemResyncNotification(myInfo, notification.getItemId(), answersForItems, false)
                    );
                }
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

        nodeDebug("got " + notification);

        // get processor, create if need
        // register my vote
        Object x = checkItemInternal(notification.getItemId(), null, null, true, true);
        NodeInfo from = notification.getFrom();

        if (x instanceof ItemResult) {
            ItemResult r = (ItemResult) x;
            // we have solution and need not answer, we answer if requested:
            if (notification.answerIsRequested()) {
                network.deliver(
                        from,
                        new ParcelNotification(myInfo,
                                notification.getItemId(),
                                null,
                                r,
                                false,
                                ParcelNotification.ParcelNotificationType.PAYMENT)
                );
            }
        } else if (x instanceof ItemProcessor) {
            ItemProcessor ip = (ItemProcessor) x;
            ItemResult result = notification.getItemResult();
            ip.lock(() -> {
                nodeDebug("found ItemProcessor for item " + notification.getItemId() + "  with state: " + ip.getState()
                        + ", it processing state is: " + ip.processingState
                        + ", have a copy: " + (ip.item != null));

                // we might still need to download and process it
                if (result.haveCopy) {
                    ip.addToSources(from);
                }
                if (result.state != ItemState.PENDING)
                    ip.vote(from, result.state);
                else
                    log.e("pending vote on item " + notification.getItemId() + " from " + from);

                // We answer only if (1) answer is requested and (2) we have position on the subject:
                if (notification.answerIsRequested() && ip.record.getState() != ItemState.PENDING) {
                    network.deliver(
                            from,
                            new ParcelNotification(myInfo,
                                    notification.getItemId(),
                                    null,
                                    ip.getResult(),
                                    ip.needsVoteFrom(from),
                                    ParcelNotification.ParcelNotificationType.PAYMENT)
                    );
                }
                return null;
            });
        } else {
            nodeDebug("impossible state: onNotification can't have invalid state from local check\n" + x);
        }
    }


    /**
     * Obtain got common item notification: looking for result or item processor and register vote
     *
     * @param notification common item notification
     *
     */
    private final void obtainParcelCommonNotification(ParcelNotification notification) {

        nodeDebug("got " + notification);

        // get processor, create if need
        // register my vote
        if(notification.getParcelId() == null) {
            obtainCommonNotification(notification);
        } else {
            Object x = checkParcelInternal(notification.getParcelId(), null, true);
            NodeInfo from = notification.getFrom();

            if (x instanceof ItemResult) {
                ItemResult r = (ItemResult) x;
                // we have solution and need not answer, we answer if requested:
                if (notification.answerIsRequested()) {
                    network.deliver(
                            from,
                            new ParcelNotification(myInfo,
                                    notification.getItemId(),
                                    notification.getParcelId(),
                                    r,
                                    false,
                                    notification.getType())
                    );
                }
            } else if (x instanceof ParcelProcessor) {
                ParcelProcessor pp = (ParcelProcessor) x;
                ItemResult resultVote = notification.getItemResult();
                pp.lock(() -> {
                    nodeDebug("found ParcelProcessor for parcel " + notification.getParcelId()
                            + " and item " + notification.getItemId()
                            + "  with payment state: " + pp.getPaymentState()
                            + "  with payload state: " + pp.getPayloadState()
                            + ", and vote for: " + resultVote.state
                            + ", payment processing state is: " + pp.getPaymentProcessingState()
                            + ", payload processing state is: " + pp.getPayloadProcessingState());

                    // we might still need to download and process it
                    if (resultVote.haveCopy) {
                        pp.addToSources(from);
                    }
                    if (resultVote.state != ItemState.PENDING)
                        pp.vote(from, resultVote.state, notification.getType().isTU());
                    else
                        log.e("pending vote on parcel " + notification.getParcelId()
                                + " and item " + notification.getItemId() + " from " + from);

//                 We answer only if (1) answer is requested and (2) we have position on the subject:
                    if (notification.answerIsRequested()) {

                        if (notification.getType().isTU()) {
                            // parcel for payment
                            if (pp.getPaymentState() != ItemState.PENDING) {
                                network.deliver(
                                        from,
                                        new ParcelNotification(myInfo,
                                                notification.getItemId(),
                                                notification.getParcelId(),
                                                pp.getPaymentResult(),
                                                pp.needsPaymentVoteFrom(from),
                                                notification.getType())
                                );
                            }
                        } else {
                            // parcel for payload
                            if (pp.getPayloadState() != ItemState.PENDING) {
                                network.deliver(
                                        from,
                                        new ParcelNotification(myInfo,
                                                notification.getItemId(),
                                                notification.getParcelId(),
                                                pp.getPayloadResult(),
                                                pp.needsPayloadVoteFrom(from),
                                                notification.getType())
                                );
                            }
                        }
                    }
                    return null;
                });
            } else {
                nodeDebug("impossible state: onNotification can't have invalid state from local check\n" + x);
            }
        }
    }

    protected Object checkItemInternal(@NonNull HashId itemId) {
        return checkItemInternal(itemId, null, null, false, false);
    }

    protected Object checkItemInternal(@NonNull HashId itemId, HashId parcelId, Approvable item, boolean autoStart, boolean forceChecking) {

        return checkItemInternal(itemId, parcelId, item, autoStart, forceChecking, false);
    }

    /**
     * Optimized for various usages, check the item, start processing as need, return object depending on the current
     * state. Note that actual error codes are set to the item itself.
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
    protected Object checkItemInternal(@NonNull HashId itemId, HashId parcelId, Approvable item,
                                       boolean autoStart, boolean forceChecking, boolean ommitItemResult) {
        try {
            // first, let's lock to the item id:
            return ItemLock.synchronize(itemId, (lock) -> {
                ItemProcessor ip = processors.get(itemId);
                if (ip != null) {
                    nodeDebug("existing IP found for " + itemId);
                    return ip;
                }

                // if we want to get already processed result for item
                if(!ommitItemResult) {
                    StateRecord r = ledger.getRecord(itemId);
                    // if it is not pending, it means it is already processed:
                    if (r != null && !r.isPending()) {
                        nodeDebug("record for " + itemId + " is already processed: " + r.getState());
                        // it is, and we may still have it cached - we do not put it again:
                        return new ItemResult(r, cache.get(itemId) != null);
                    }

                    nodeDebug("no record in ledger found for " + itemId.toBase64String());

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
                    ItemProcessor processor = new ItemProcessor(itemId, parcelId, item, lock, forceChecking);
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

    protected Object checkParcelInternal(@NonNull HashId parcelId) {
        return checkParcelInternal(parcelId, null, false);
    }

    /**
     * Optimized for various usages, check the parcel, start processing as need, return object depending on the current
     * state. Note that actual error codes are set to the item itself.
     *
     * @param parcelId    parcel's id.
     * @param parcel      provide parcel if need, can be null. Default is null.
     * @param autoStart - create new ParcelProcessor if not exist. Default is false.
     *
     * @return instance of {@link ParcelProcessor} if the parcel is being processed (also if it was started by the call),
     *         {@link ItemResult} if it is already processed or can't be processed.
     */
    protected Object checkParcelInternal(@NonNull HashId parcelId, Parcel parcel, boolean autoStart) {
        try {
            return ParcelLock.synchronize(parcelId, (lock) -> {
                ParcelProcessor processor = parcelProcessors.get(parcelId);
                if (processor != null) {
                    nodeDebug("existing parcel processor found for " + parcelId
                            + ", payment state is " + processor.getPaymentState()
                            + ", payment result is " + processor.getPaymentResult()
                            + ", payload state is " + processor.getPayloadState()
                            + ", payload result is " + processor.getPayloadResult()
                            + ", processingState is " + processor.processingState
                            + ", payment is " + (processor.payment == null ? null : processor.payment.getId())
                            + ", payload is " + (processor.payload == null ? null : processor.payload.getId()));
                    return processor;
                }

                nodeDebug("no parcel processor found for " + parcelId + ", will be created: " + autoStart);

                if (autoStart) {
                    if (parcel != null) {
                        synchronized (parcelCache) {
                            parcelCache.put(parcel);
                        }
                    }
                    processor = new ParcelProcessor(parcelId, parcel, lock);
                    parcelProcessors.put(parcelId, processor);

                    return processor;
                } else {
                    return ItemResult.UNDEFINED;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException("failed to checkItem", e);
        }
    }

    protected void nodeDebug(String str) {
        log.d(toString() + ": " + str);
    }

    @Override
    public String toString() {
        return "[" + new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date()) + "] Node(" + myInfo.getNumber() + ")";
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
                nodeDebug("cache miss: ");
            }
            return i;
        }
//        return cache.get(parcelId);
    }

    /**
     * Get the cached parcel.
     *
     * @param itemId
     *
     * @return cached item or null if it is missing
     */
    public Parcel getParcel(HashId itemId) {
        synchronized (parcelCache) {
            @Nullable Parcel i = parcelCache.get(itemId);
            if (i == null) {
                nodeDebug("parcelCache miss: ");
            }
            return i;
        }
//        return cache.get(parcelId);
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

    public void shutdown() {
        for (ItemProcessor ip : processors.values()) {
            ip.emergencyBreak();
        }
    }


    /// ParcelProcessor ///

    private class ParcelProcessor {

        private final HashId parcelId;
        private Parcel parcel;
        private Contract payment;
        private Contract payload;
        private ItemProcessor paymentProcessor;
        private ItemProcessor payloadProcessor;
        private ItemResult paymentResult = null;
        private ItemResult payloadResult = null;
        private Set<NodeInfo> sources = new HashSet<>();
        private HashMap<NodeInfo, ItemState> paymentDelayedVotes = new HashMap<>();
        private HashMap<NodeInfo, ItemState> payloadDelayedVotes = new HashMap<>();
        private ParcelProcessingState processingState;

        private final Object mutex;

        private ScheduledFuture<?> downloader;
        private ScheduledFuture<?> processSchedule;

        private final AsyncEvent<Void> downloadedEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> doneEvent = new AsyncEvent<>();

        public ParcelProcessor(HashId parcelId, Parcel parcel, Object lock) {
            mutex = lock;

            this.parcelId = parcelId;
            this.parcel = parcel;
            if (parcel == null)
                parcel = parcelCache.get(parcelId);
            this.parcel = parcel;
            if(parcel != null) {
                payment = parcel.getPaymentContract();
                payload = parcel.getPayloadContract();
            }

            processingState = ParcelProcessingState.INIT;

            if (this.parcel != null)
                parcelDownloaded();
        }

        //////////// processing section /////////////

        private void pulseProcessing() {
            if(processingState.canContinue()) {
                synchronized (mutex) {
                    if (processSchedule == null || processSchedule.isDone()) {
                        processSchedule = (ScheduledFuture<?>) executorService.submit(() -> process());
                    }
                }
            }
        }

        private void process() {
            if(processingState.canContinue()) {

                processingState = ParcelProcessingState.PREPARING;
                try {
                    debug("checking payment");

//                    Object x = checkItemInternal(payment.getId(), parcelId, payment, true, true);
//                    if (x instanceof ItemProcessor) {
//                        paymentProcessor = ((ItemProcessor) x);
//                    } else {
//                        paymentResult = (ItemResult) x;
//                    }

                    if (paymentResult == null) {
                        processingState = ParcelProcessingState.PAYMENT_CHECKING;
                        debug("parcel's payment processor for " + payment.getId() + ", state is " + paymentProcessor.getState() + ", processingState is " + paymentProcessor.processingState);
//                        if(paymentProcessor.processingState.notCheckedYet()) {
//                            paymentProcessor.pollingReadyEvent.await();
//                        }

                        debug("parcel payment processor for " + payment.getId()
                                + " is ready for polling, state is " + paymentProcessor.getState()
                                + " processingState is " + paymentProcessor.processingState);

                        synchronized (mutex) {
                            for (NodeInfo ni : paymentDelayedVotes.keySet())
                                paymentProcessor.vote(ni, paymentDelayedVotes.get(ni));
                            paymentDelayedVotes.clear();
                        }
                        processingState = ParcelProcessingState.PAYMENT_POLLING;
                        if(!paymentProcessor.isDone()) {
                            paymentProcessor.doneEvent.await();
                        }
                        paymentResult = paymentProcessor.getResult();
                    }


                    if (paymentResult.state.isApproved()) {
                        debug("payment " + payment.getId() + " has approved for payload " + payload.getId());

//                        x = checkItemInternal(payload.getId(), parcelId, payload, true, true);
//                        if (x instanceof ItemProcessor) {
//                            payloadProcessor = ((ItemProcessor) x);
//                        } else {
//                            payloadResult = (ItemResult) x;
//                        }

                        if (payloadResult == null) {

                            processingState = ParcelProcessingState.PAYLOAD_CHECKING;
                            Contract parent = null;
                            for(Contract c : payment.getRevoking()) {
                                if(c.getId().equals(payment.getParent())) {
                                    parent = c;
                                    break;
                                }
                            }
                            if(parent != null) {
                                int limit = Quantiser.quantaPerUTN * (parent.getStateData().getIntOrThrow("transaction_units") - payment.getStateData().getIntOrThrow("transaction_units"));
                                payload.getQuantiser().reset(limit);
                                debug( "payload " + payload.getId() + " processing limit is " + payload.getQuantiser().getQuantaLimit());
                                payloadProcessor.forceChecking(true);

//                                if (payloadProcessor.processingState.notCheckedYet()) {
//
//                                    payloadProcessor.pollingReadyEvent.await();
//                                }
                                debug("parcel payload processor for " + payload.getId()
                                        + " is ready for polling, state is " + payloadProcessor.getState()
                                        + " processingState is " + payloadProcessor.processingState);

                                synchronized (mutex) {
                                    for (NodeInfo ni : payloadDelayedVotes.keySet())
                                        payloadProcessor.vote(ni, payloadDelayedVotes.get(ni));
                                    payloadDelayedVotes.clear();
                                }

                                processingState = ParcelProcessingState.PAYLOAD_POLLING;
                                if(!payloadProcessor.isDone()) {
                                    payloadProcessor.doneEvent.await();
                                }
                                payloadResult = payloadProcessor.getResult();
                            } else {
                                debug("payload " + payloadProcessor.itemId + " parent == null");
                                payloadProcessor.emergencyBreak();
                                payloadProcessor.doneEvent.await();
                            }
                        } else {
                            debug("payload consensus already got for " + payload.getId());
                        }
                    } else {
                        if(payloadProcessor != null) {
                            debug("payload " + payloadProcessor.itemId + " payment result not approved");
                            payloadProcessor.emergencyBreak();
                            payloadProcessor.doneEvent.await();
                        }
                    }

                    processingState = ParcelProcessingState.FINISHED;
                    doneEvent.fire();

                    if(paymentProcessor != null && paymentProcessor.processingState != ItemProcessingState.FINISHED) {
                        paymentProcessor.removedEvent.await();
                    }
                    if(payloadProcessor != null && payloadProcessor.processingState != ItemProcessingState.FINISHED) {
                        payloadProcessor.removedEvent.await();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    processingState = ParcelProcessingState.FINISHED;
                    doneEvent.fire();
                }

                removeSelf();
            }
        }

        //////////// download section /////////////

        private void pulseDownload() {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    processingState = ParcelProcessingState.DOWNLOADING;

                    synchronized (mutex) {
                        if (parcel == null && (downloader == null || downloader.isDone())) {
                            downloader = (ScheduledFuture<?>) executorService.submit(() -> download());
                        }
                    }
                }
            }
        }

        private void download() {
            if(processingState.canContinue()) {

                while (!isPayloadPollingExpired() && parcel == null) {
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
                            parcel = network.getParcel(parcelId, source, config.getMaxGetItemTime());
                            if (parcel != null) {
                                debug("downloaded from " + source);
                                parcelDownloaded();
                                return;
                            } else {
                                debug("failed to download " + parcelId + " from " + source);
                                Thread.sleep(100);
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        private final void parcelDownloaded() {
            if(processingState.canContinue()) {
                synchronized (parcelCache) {
                    parcelCache.put(parcel);
                }

                payment = parcel.getPaymentContract();
                payload = parcel.getPayloadContract();

                synchronized (mutex) {
                    Object x = checkItemInternal(payment.getId(), parcelId, payment, true, true);
                    if (x instanceof ItemProcessor) {
                        paymentProcessor = ((ItemProcessor) x);
                    } else {
                        paymentResult = (ItemResult) x;
                    }
                    x = checkItemInternal(payload.getId(), parcelId, payload, true, false);
                    if (x instanceof ItemProcessor) {
                        payloadProcessor = ((ItemProcessor) x);
                    } else {
                        payloadResult = (ItemResult) x;
                    }
                }

                pulseProcessing();
                downloadedEvent.fire();
            }
        }

        private void stopDownloader() {
            if (downloader != null)
                downloader.cancel(false);
        }


        //////////// polling section /////////////

        private final void vote(NodeInfo node, ItemState state, boolean isTU) {
            if(processingState.canContinue()) {
                debug("vote is TU: " + isTU
                        + " vote for: " + state
                        + " and from: " + node
                        + " paymentProcessor is " + paymentProcessor
                        + " paymentProcessor state is " + (paymentProcessor == null ? null : paymentProcessor.processingState)
                        + " payloadProcessor is " + payloadProcessor
                        + " payloadProcessor state is " + (payloadProcessor == null ? null : payloadProcessor.processingState));

                if(isTU){
                    synchronized (mutex) {
                        if (paymentProcessor != null
//                                && !paymentProcessor.processingState.notCheckedYet()
                                )
                            paymentProcessor.vote(node, state);
                        else {
                            paymentDelayedVotes.put(node, state);
                        }
                    }
                } else {
                    synchronized (mutex) {
                        if (payloadProcessor != null
//                                && !payloadProcessor.processingState.notCheckedYet()
                                )
                            payloadProcessor.vote(node, state);
                        else {
                            payloadDelayedVotes.put(node, state);
                        }
                    }
                }
            }
        }


        //////////// common section /////////////

        public ItemResult getPayloadResult() {
            if(payloadResult != null)
                return payloadResult;
            if(payloadProcessor != null)
                return payloadProcessor.getResult();
            return ItemResult.UNDEFINED;
        }

        private ItemState getPayloadState() {
            if(payloadResult != null)
                return payloadResult.state;
            if(payloadProcessor != null)
                return payloadProcessor.getState();
            return ItemState.PENDING;
        }

        public ItemResult getPaymentResult() {
            if(paymentResult != null)
                return paymentResult;
            if(paymentProcessor != null)
                return paymentProcessor.getResult();
            return ItemResult.UNDEFINED;
        }

        private ItemState getPaymentState() {
            if(paymentResult != null)
                return paymentResult.state;
            if(paymentProcessor != null)
                return paymentProcessor.getState();
            return ItemState.PENDING;
        }

        private ItemProcessingState getPaymentProcessingState() {
            if(paymentProcessor != null)
                return paymentProcessor.processingState;
            return ItemProcessingState.NOT_EXIST;
        }

        private ItemProcessingState getPayloadProcessingState() {
            if(payloadProcessor != null)
                return payloadProcessor.processingState;
            return ItemProcessingState.NOT_EXIST;
        }

        /**
         * true if we need to get vote from a node
         *
         * @param node we might need vote from
         *
         * @return
         */
        private final boolean needsPayloadVoteFrom(NodeInfo node) {
            if(payloadProcessor != null)
                return payloadProcessor.needsVoteFrom(node);
            return false;
        }

        /**
         * true if we need to get vote from a node
         *
         * @param node we might need vote from
         *
         * @return
         */
        private final boolean needsPaymentVoteFrom(NodeInfo node) {
            if(paymentProcessor != null)
                return paymentProcessor.needsVoteFrom(node);
            return false;
        }

        private final void addToSources(NodeInfo node) {
            if (parcel != null)
                return;

            synchronized (sources) {
                if (sources.add(node)) {
                    debug("added source to parcel: " + sources);
                    pulseDownload();
                }
            }

            if(paymentProcessor != null)
                paymentProcessor.addToSources(node);

            if(payloadProcessor != null)
                payloadProcessor.addToSources(node);
        }

        private final void removeSelf() {
            debug("removing parcel");
            if(processingState.canRemoveSelf()) {
                parcelProcessors.remove(parcelId);

                stopDownloader();

                doneEvent.fire();

                debug("parcel removed");
            }
        }

        private boolean isPayloadPollingExpired() {
            if(payloadProcessor != null)
                return payloadProcessor.isPollingExpired();
            return false;
        }

        private boolean isDone() {
            return processingState == ParcelProcessingState.FINISHED;
        }

        public <T> T lock(Supplier<T> c) {
            synchronized (mutex) {
                return (T) c.get();
            }
        }

        protected void debug(String str) {
            nodeDebug("pp -> parcel: " + parcelId + ", processing state: " + processingState + ": " + str);
        }
    }


    /// ItemProcessor ///

    private class ItemProcessor {

        private final HashId itemId;
        private final HashId parcelId;
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
        private boolean isCheckingForce = false;

        private final AsyncEvent<Void> downloadedEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> doneEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> pollingReadyEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> removedEvent = new AsyncEvent<>();

        private final Object mutex;
        private final Object resyncMutex;

        private ScheduledFuture<?> downloader;
        private ScheduledFuture<?> poller;
        private ScheduledFuture<?> consensusReceivedChecker;
        private ScheduledFuture<?> resyncer;

        public ItemProcessor(HashId itemId, HashId parcelId, Approvable item, Object lock, boolean isCheckingForce) {

            mutex = lock;
            resyncMutex = new Object();
            this.isCheckingForce = isCheckingForce;

            processingState = ItemProcessingState.INIT;
            this.itemId = itemId;
            this.parcelId = parcelId;
            if (item == null)
                item = cache.get(itemId);
            this.item = item;

            StateRecord recordWas = ledger.getRecord(itemId);
            if (recordWas != null) {
                stateWas = recordWas.getState();
            } else {
                stateWas = ItemState.UNDEFINED;
            }
            debug("create ItemProcessor, state was: " + stateWas);

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
                    if(!processingState.isProcessedToConsensus()) {
                        processingState = ItemProcessingState.DOWNLOADING;
                    }

                    synchronized (mutex) {
                        if (item == null && (downloader == null || downloader.isDone())) {
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
                                debug("downloaded from " + source);
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
                if(!processingState.isProcessedToConsensus()) {
                    processingState = ItemProcessingState.DOWNLOADED;
                }
                if(isCheckingForce) {
                    debug("item downloaded, is need to run checkItem: " + isCheckingForce);
                    checkItem();
                }
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

                if (!processingState.isProcessedToConsensus() && processingState != ItemProcessingState.CHECKING) {
                    if (alreadyChecked) {
                        throw new RuntimeException("Check already processed");
                    }

                    if(!processingState.isProcessedToConsensus()) {
                        processingState = ItemProcessingState.CHECKING;
                    }

                    debug("checking, state is " + record.getState());

                    // Check the internal state
                    // Too bad if basic check isn't passed, we will not process it further
                    HashMap<HashId, StateRecord> itemsToResync = new HashMap<>();
                    boolean needToResync = false;

                    try {
                        debug("item is TU: " + item.isTU());
                        boolean checkPassed = false;

                        if(item.isTU()) {
                            checkPassed = item.paymentCheck(config.getTransactionUnitsIssuerKey());
                        } else {
                            checkPassed = item.check();
                        }
                        if(item instanceof Contract) {
                            debug("check cost: " + ((Contract) item).getQuantiser().getQuantaSum() + "/" + ((Contract) item).getQuantiser().getQuantaLimit());
                        }
                        debug("check is passed: " + checkPassed);
                        if (checkPassed) {

                            itemsToResync = isNeedToResync(true);
                            needToResync = !itemsToResync.isEmpty();

                            // If no need to resync subItems, check them
                            if (!needToResync) {
                                checkSubItems();
                            }
                        } else {
                            debug("found " + item.getErrors());
//                            Collection<ErrorRecord> errors = item.getErrors();
//                            errors.forEach(e -> debug("Found error: " + e));
                        }
                    } catch (Quantiser.QuantiserException e) {
                        debug("Quantiser limit");

                        if(item instanceof Contract) {
                            debug("Quantiser limit " + ((Contract) item).getQuantiser().getQuantaSum() + "/" + ((Contract) item).getQuantiser().getQuantaLimit());
                        }
                        emergencyBreak();
                        return;
                    }
                    alreadyChecked = true;

                    debug("checking for resync, item is need to resync: " + needToResync);

                    if (!needToResync) {
                        commitCheckedAndStartPolling();
                    } else {
                        debug("some of sub-contracts was not approved, its should be resync");
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
                    debug("num finished resync items " + numFinished + "/" + resyncingItems.size());

                    if (resyncingItems.size() == numFinished && processingState.isGotResyncedState()) {
                        debug("all subItems resynced and states committed");


                        if(!processingState.isProcessedToConsensus()) {
                            processingState = ItemProcessingState.CHECKING;
                        }

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

        // check subitems of main item and lock subitems in the ledger
        private final void checkSubItems() {
            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {
                    checkSubItemsOf(item);
                }
            }
        }

        // check subitems of given item recursively (down for newItems line)
        private final void checkSubItemsOf(Approvable checkingItem) {
            debug("checking subitems of : " + checkingItem.getId());
            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {
                    for (Reference refModel : checkingItem.getReferencedItems()) {
                        HashId id = refModel.contract_id;
                        if (refModel.type != Reference.TYPE_TRANSACTIONAL) {
                            if (!ledger.isApproved(id)) {
                                checkingItem.addError(Errors.BAD_REF, id.toString(), "reference not approved");
                            }
                        }
                    }
                    // check revoking items
                    for (Approvable a : checkingItem.getRevokingItems()) {
                        debug("checking revoke of item of : " + checkingItem.getId() + " -> " + a.getId());
                        StateRecord r = record.lockToRevoke(a.getId());
                        debug("checking revoke of item of : " + checkingItem.getId() + " -> " + a.getId() + " record is " + r);
                        if (r == null) {
                            checkingItem.addError(Errors.BAD_REVOKE, a.getId().toString(), "can't revoke");
                        } else {
                            if (!lockedToRevoke.contains(r))
                                lockedToRevoke.add(r);
                        }
                    }
                    // check new items
                    for (Approvable newItem : checkingItem.getNewItems()) {

                        checkSubItemsOf(newItem);

                        if (!newItem.getErrors().isEmpty()) {
                            checkingItem.addError(Errors.BAD_NEW_ITEM, newItem.getId().toString(), "bad new item: not passed check");
                        } else {
                            StateRecord r = record.createOutputLockRecord(newItem.getId());
                            if (r == null) {
                                checkingItem.addError(Errors.NEW_ITEM_EXISTS, newItem.getId().toString(), "new item exists in ledger");
                            } else {
                                if (!lockedToCreate.contains(r))
                                    lockedToCreate.add(r);
                            }
                        }
                    }

                    debug("checking subitems of item, state: " + record.getState() +
                            ", errors: " + checkingItem.getErrors().size());
                }
            }
        }

        private final void commitCheckedAndStartPolling() {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    boolean checkPassed = item.getErrors().isEmpty();

                    debug("item checked, checkPassed: " + checkPassed
                            + ", state: " + record.getState() +
                            ", errors: " + item.getErrors().size());

                    debug("found " + item.getErrors().size() + " errors");
//                    Collection<ErrorRecord> errors = item.getErrors();
//                    errors.forEach(e -> debug("found error: " + e));

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

                    if(!processingState.isProcessedToConsensus()) {
                        processingState = ItemProcessingState.POLLING;
                    }
                    vote(myInfo, record.getState());
                    broadcastMyState();
                    pulseStartPolling();
                    pollingReadyEvent.fire();
                }
            }
        }

        public HashMap<HashId, StateRecord> isNeedToResync(boolean baseCheckPassed) {
            if(processingState.canContinue()) {
                HashMap<HashId, StateRecord> unknownParts = new HashMap<>();
                HashMap<HashId, StateRecord> knownParts = new HashMap<>();
                if (baseCheckPassed) {
                    // check the referenced items
                    for (Reference refModel : item.getReferencedItems()) {
                        HashId id = refModel.contract_id;
                        if(refModel.type == Reference.TYPE_EXISTING && id != null) {
                            StateRecord r = ledger.getRecord(id);
                            debug("referenced subitem " + id + " is " + (r != null ? r.getState() : null));

                            if (r == null || !r.getState().isConsensusFound()) {
                                unknownParts.put(id, r);
                            } else {
                                knownParts.put(id, r);
                            }
                        }
                    }
                    debug("checking revoking subitems");
                    // check revoking items
                    for (Approvable a : item.getRevokingItems()) {
                        StateRecord r = ledger.getRecord(a.getId());
                        debug("checking revoking subitem " + a.getId());

//                        int numIterations = 0;
//                        while(r == null) {
//                            debug("revoking subitem " + a.getId() + " is null, iteration " + numIterations);
//                            try {
//                                wait(100);
//                            } catch (InterruptedException e) {
//                                e.printStackTrace();
//                            }
//                            debug(">revoking subitem " + a.getId() + " is null, iteration " + numIterations);
//                            r = ledger.getRecord(a.getId());
//                            debug(">>revoking subitem " + a.getId() + " is null, iteration " + numIterations);
//                            numIterations ++;
//                            if(numIterations > 10) {
//                                break;
//                            }
//                        }

//                        if (r != null && !r.getState().isConsensusFound()) {
//                            try {
//                                r.reload();
//                            } catch (StateRecord.NotFoundException e) {
//                                e.printStackTrace();
//                            }
//                        }

                        debug("revoking subitem " + a.getId() + " is " + (r != null ? r.getState() : null));

                        if (r == null || !r.getState().isConsensusFound()) {
                            unknownParts.put(a.getId(), r);
                        } else {
                            knownParts.put(a.getId(), r);
                        }
                    }
                } else {
                    debug("found " + item.getErrors().size() + " errors");
//                    Collection<ErrorRecord> errors = item.getErrors();
//                    errors.forEach(e -> debug("found error: " + e));
                }
                boolean needToResync = false;
                // contract is complex and consist from parts
                if (unknownParts.size() + knownParts.size() > 0) {
                    needToResync = baseCheckPassed &&
                            unknownParts.size() > 0 &&
                            knownParts.size() >= config.getKnownSubContractsToResync();
                }
                debug("is item need to resync: " + needToResync + ", state: " + record.getState() +
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
            debug("start send poll notifications");

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
                    Notification notification;
                    debug("send poll notifications");
                    ParcelNotification.ParcelNotificationType notificationType;
                    if(item.isTU()) {
                        notificationType = ParcelNotification.ParcelNotificationType.PAYMENT;
                    } else {
                        notificationType = ParcelNotification.ParcelNotificationType.PAYLOAD;
                    }
                    notification = new ParcelNotification(myInfo, itemId, parcelId, getResult(), true, notificationType);
                    network.eachNode(node -> {
                        if (!positiveNodes.contains(node) && !negativeNodes.contains(node))
                            network.deliver(node, notification);
                    });
                }
            }
        }

        private final void vote(NodeInfo node, ItemState state) {
            debug("vote from " + node + ": " + state);
            if(processingState.canContinue()) {
                boolean positiveConsensus = false;
                boolean negativeConsensus = false;
                ItemProcessingState stateWas;
                synchronized (mutex) {
                    debug("inside mutex -> vote from " + node + ": " + state);
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

                    stateWas = processingState;

                    if (processingState.isProcessedToConsensus()) {
                        debug("consensus already found, but vote for " + itemId + " from " + node + ": " + state + " > "
                                + positiveNodes.size() + "/" + negativeNodes.size());
                        if(processingState.isDone()) {
//                            checkIfAllReceivedConsensus();
//                            removeSelf();
                            close();
                        }
                        return;
                    }

                    if (negativeNodes.size() >= config.getNegativeConsensus()) {
                        negativeConsensus = true;
                        processingState = ItemProcessingState.GOT_CONSENSUS;
                    } else if (positiveNodes.size() >= config.getPositiveConsensus()) {
                        positiveConsensus = true;
                        processingState = ItemProcessingState.GOT_CONSENSUS;
                    }
                    debug("vote from " + node + ": " + state + " > "
                            + positiveNodes.size() + "/" + negativeNodes.size() +
                            ", consFound=" + processingState.isProcessedToConsensus() + ": positive=" + positiveConsensus);
                    if (!processingState.isProcessedToConsensus())
                        return;
                }
                debug("voting should be finished > "+ positiveNodes.size() + "/" + negativeNodes.size() +
                        ", consFound=" + processingState.isProcessedToConsensus()
                        + ": positive=" + positiveConsensus
                        + ": negative=" + negativeConsensus +
                        ", processingState=" + processingState +
                        ", stateWas=" + stateWas);

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

        // commit subitems of given item to the ledger
        private void downloadAndCommitSubItemsOf(Approvable commitingItem) {
            if(processingState.canContinue()) {
                for (Approvable revokingItem : commitingItem.getRevokingItems()) {
                    // The record may not exist due to ledger desync, so we create it if need
                    StateRecord r = ledger.findOrCreate(revokingItem.getId());
                    r.setState(ItemState.REVOKED);
                    r.setExpiresAt(ZonedDateTime.now().plus(config.getRevokedItemExpiration()));
                    r.save();
                    debug("revoking subitem " + revokingItem.getId() + " saved");
                }
                for (Approvable newItem : commitingItem.getNewItems()) {
                    // The record may not exist due to ledger desync too, so we create it if need
                    StateRecord r = ledger.findOrCreate(newItem.getId());
                    r.setState(ItemState.APPROVED);
                    r.setExpiresAt(newItem.getExpiresAt());
                    r.save();
                    debug("new subitem " + newItem.getId() + " saved");

                    downloadAndCommitSubItemsOf(newItem);
                }
            }
        }

        private void downloadAndCommit() {
            if(processingState.canContinue()) {
                // it may happen that consensus is found earlier than item is download
                // we still need item to fix all its relations:
                synchronized (mutex) {
                    try {
                        debug("download and commit item, state: " + getState() + ", item is " + item);
                        if (item == null) {
                            // If positive consensus os found, we can spend more time for final download, and can try
                            // all the network as the source:
                            pollingExpiresAt = Instant.now().plus(config.getMaxDownloadOnApproveTime());
                            downloadedEvent.await(getMillisLeft());
                        }
                        // We use the caching capability of ledger so we do not get records from
                        // lockedToRevoke/lockedToCreate, as, due to conflicts, these could differ from what the item
                        // yields. We just clean them up afterwards:

                        debug("download and commit subitems, state: " + getState());
                        downloadAndCommitSubItemsOf(item);

                        debug("clearing ledger locks");
                        lockedToCreate.clear();
                        lockedToRevoke.clear();
                        debug("saving record");
                        record.save();
                        debug("record saved");
                        if (record.getState() != ItemState.APPROVED) {
                            log.e("record is not approved " + record.getState());
                        }
                        debug("approval done, state: " + getState() + ", have a copy " + (item == null));
                    } catch (TimeoutException | InterruptedException e) {
                        debug("commit: failed to load item, ledger will not be altered, the record will be destroyed");
                        setState(ItemState.UNDEFINED);
                        record.destroy();
                    }
                }
                close();
            }
        }

        private void rollbackChanges(ItemState newState) {
            synchronized (ledgerRollbackLock) {
                debug("rollbacks to: " + newState + " consensus: " + positiveNodes.size() + "/" + negativeNodes.size());
                ledger.transaction(() -> {
                    for (StateRecord r : lockedToRevoke)
                        r.unlock().save();
                    lockedToRevoke.clear();

                    // form created records, we touch only these that we have actually created
                    for (StateRecord r : lockedToCreate) {
                        debug("unlocking to create, item: " + r.getId() + " state: " + r.getState());
                        r.unlock().save();
                    }
                    // todo: concurrent modification can happen here!
                    lockedToCreate.clear();

                    debug("setting state: " + newState.name());
                    setState(newState);
                    debug("rollback state is set ");
                    ZonedDateTime expiration = ZonedDateTime.now()
                            .plus(newState == ItemState.REVOKED ?
                                    config.getRevokedItemExpiration() : config.getDeclinedItemExpiration());
                    record.setExpiresAt(expiration);
                    record.save(); // TODO: current implementation will cause an inner dbPool.db() invocation
                    debug("changes rolled back and saved ");
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
                Notification notification;
//                debug("send new consensus notifications");
                ParcelNotification.ParcelNotificationType notificationType;
                if(item.isTU()) {
                    notificationType = ParcelNotification.ParcelNotificationType.PAYMENT;
                } else {
                    notificationType = ParcelNotification.ParcelNotificationType.PAYLOAD;
                }
                notification = new ParcelNotification(myInfo, itemId, parcelId, getResult(), true, notificationType);
                network.eachNode(node -> {
                    if (!positiveNodes.contains(node) && !negativeNodes.contains(node)) {
//                        debug("unknown consensus on the node " + node.getNumber() + " , deliver new consensus with result: " + getResult());
                        if(!myInfo.equals(node)) {
                            network.deliver(node, notification);
                        } else {
                            if(processingState.isProcessedToConsensus()) {
                                vote(myInfo, record.getState());
                            }
                        }
                    }
                });
            }
        }

        private final Boolean checkIfAllReceivedConsensus() {
            if(processingState.canContinue()) {
                Boolean allReceived = network.allNodes().size() == positiveNodes.size() + negativeNodes.size();

                debug("is all got consensus: " + allReceived + " : " + network.allNodes().size() + "=" + positiveNodes.size() + "+" + negativeNodes.size());
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

                    if(!processingState.isProcessedToConsensus()) {
                        processingState = ItemProcessingState.RESYNCING;
                    }

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
                            debug("resync at the " + node.getNumber() + " for " + itemId + ", and subitems: " + itemsToResync);
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
                Notification notification;
                debug("broadcast own state notifications");

                ParcelNotification.ParcelNotificationType notificationType;
                if(item.isTU()) {
                    notificationType = ParcelNotification.ParcelNotificationType.PAYMENT;
                } else {
                    notificationType = ParcelNotification.ParcelNotificationType.PAYLOAD;
                }
                notification = new ParcelNotification(myInfo, itemId, parcelId, getResult(), true, notificationType);
                network.broadcast(myInfo, notification);
            }
        }

        private void forceChecking(boolean isCheckingForce) {
            this.isCheckingForce = isCheckingForce;
            if(processingState.canContinue()) {
                if (processingState == ItemProcessingState.DOWNLOADED) {
                    executorService.submit(() -> {
                        checkItem();
                    });
                }
            }
        }

        private void close() {
            debug("closing, state: " + getState() + ", it was self-resync: " + resyncItselfOnly);

            stopPoller();

            // fire all event to release possible listeners
            processingState = ItemProcessingState.DONE;
            downloadedEvent.fire();
            pollingReadyEvent.fire();
            doneEvent.fire();
            debug("doneEvent.fire");

            if(processingState.canContinue()) {
                // If we not just resynced itslef
                if (!resyncItselfOnly) {
                    checkIfAllReceivedConsensus();
                    debug("check if all received consensus finished");
                    if (processingState == ItemProcessingState.DONE) {
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

            debug("WARNING: emergency break of all processes" );

            processingState = ItemProcessingState.EMERGENCY_BREAK;

            stopDownloader();
            stopPoller();
            stopConsensusReceivedChecker();
            stopResync();
            rollbackChanges(stateWas);

            processingState = ItemProcessingState.FINISHED;
        }

        private ItemState getState() {
            return record.getState();
        }

        private final void setState(ItemState newState) {
            synchronized (mutex) {
                debug("set state: " + newState);
                record.setState(newState);
                debug("state is set");
            }
        }

        private final void removeSelf() {
            if(processingState.canRemoveSelf()) {
                debug("removing item, is ip exist: " + processors.containsKey(itemId));
                processors.remove(itemId);

                stopDownloader();
                stopPoller();
                stopConsensusReceivedChecker();
                stopResync();

                debug("closed and removed");

                // fire all event to release possible listeners
                downloadedEvent.fire();
                pollingReadyEvent.fire();
                doneEvent.fire();
                removedEvent.fire();

                debug("doneEvent.fire");
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

        private boolean isDone() {
            return processingState.isDone();
        }

        public <T> T lock(Supplier<T> c) {
            synchronized (mutex) {
                return (T) c.get();
            }
        }

        protected void debug(String str) {
            nodeDebug("ip -> parcel: " + parcelId + ", item: " + itemId + ", processing state: " + processingState + ": " + str);
        }
    }


    public enum ParcelProcessingState {
        INIT,
        DOWNLOADING,
        PREPARING,
        PAYMENT_CHECKING,
        PAYLOAD_CHECKING,
        RESYNCING,
        GOT_RESYNCED_STATE,
        PAYMENT_POLLING,
        PAYLOAD_POLLING,
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


    public enum ItemProcessingState {
        NOT_EXIST,
        INIT,
        DOWNLOADING,
        DOWNLOADED,
        CHECKING,
        RESYNCING,
        GOT_RESYNCED_STATE,
        POLLING,
        GOT_CONSENSUS,
        DONE,
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
                case DONE:
                case FINISHED:
                    return true;
            }
            return false;
        }

        public boolean isDone() {
            switch (this) {
                case SENDING_CONSENSUS:
                case DONE:
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

        public boolean notCheckedYet() {
            switch (this) {
                case NOT_EXIST:
                case INIT:
                case DOWNLOADING:
                case DOWNLOADED:
                case CHECKING:
                case RESYNCING:
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
                debug("resync vote from " + node + ": " + state + " (APPROVED/REVOKED/DECLINED/UNDEFINED)> " +
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
                debug("After resync try commit state: " + committingState);
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
                                    debug("resyncing calculations finished");
                                    ZonedDateTime createdAt = ZonedDateTime.ofInstant(
                                            Instant.ofEpochSecond((long) startDateAvg.average()), ZoneId.systemDefault());
                                    ZonedDateTime expiresAt = ZonedDateTime.ofInstant(
                                            Instant.ofEpochSecond((long) expiresAtAvg.average()), ZoneId.systemDefault());

                                    ledger.findOrCreate(hashId).setState(committingState)
                                            .setCreatedAt(createdAt)
                                            .setExpiresAt(expiresAt)
                                            .save();

                                    debug("resyncing commit success with state " + committingState);

                                    resyncingState = ResyncingItemProcessingState.COMMIT_SUCCESSFUL;
                                    break;
                                }
                            } else {
                                debug("item has not approved from " + ni);
                                if (rest.decrementAndGet() < 1) {
                                    debug("No resync consensus, fail. State was: " + stateWas);

                                    resyncingState = ResyncingItemProcessingState.COMMIT_FAILED;
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            debug("failed to get state for from " + ni + ": " + e);
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

        protected void debug(String str) {
            nodeDebug(" -> resyncing item: " + hashId + ", resyncing state: " + resyncingState + ": " + str);
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
