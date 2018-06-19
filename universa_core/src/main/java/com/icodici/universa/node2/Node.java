/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.*;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.ListRole;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.*;
import com.icodici.universa.node2.network.DatagramAdapter;
import com.icodici.universa.node2.network.Network;
import com.icodici.universa.node2.network.NetworkV2;
import net.sergeych.biserializer.BiAdapter;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
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
import java.util.function.Supplier;

/**
 * The v2 Node.
 * <p>
 * In v2 node is always the local node. All the rest takes com.icodici.universa.node2.network.Network class.
 */
public class Node {

    private static final int MAX_SANITATING_RECORDS = 64;

    NodeStats nodeStats = new NodeStats();

    private ScheduledFuture<?> sanitator;
    private ScheduledFuture<?> statsCollector;

    public boolean isSanitating() {
        return !recordsToSanitate.isEmpty();
    }

    private Map<HashId,StateRecord> recordsToSanitate;

    public Map<HashId, StateRecord> getRecordsToSanitate() {
        return recordsToSanitate;
    }

    private static LogPrinter log = new LogPrinter("NODE");

    public Config getConfig() {
        return config;
    }
    private final Config config;
    private final NodeInfo myInfo;
    private final Ledger ledger;
    private final Object ledgerRollbackLock = new Object();
    private final Network network;
    private final ItemCache cache;
    private final ParcelCache parcelCache;
    private final EnvCache envCache;
    private final NameCache nameCache;
    private final ItemInformer informer = new ItemInformer();
    protected int verboseLevel = DatagramAdapter.VerboseLevel.NOTHING;
    protected String label = null;
    protected boolean isShuttingDown = false;
    protected AsyncEvent sanitationFinished = new AsyncEvent();

    private final ItemLock itemLock = new ItemLock();
    private final ParcelLock parcelLock = new ParcelLock();

    private ConcurrentHashMap<HashId, ItemProcessor> processors = new ConcurrentHashMap();
    private ConcurrentHashMap<HashId, ParcelProcessor> parcelProcessors = new ConcurrentHashMap();

    private ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(128, new ThreadFactory() {

        private final ThreadGroup threadGroup = new ThreadGroup("node-workers");

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(threadGroup,r);
            thread.setName("node-"+myInfo.getNumber()+"-worker");
            return thread;
        }
    });

    private NSmartContract.NodeInfoProvider nodeInfoProvider = new NSmartContract.NodeInfoProvider() {

        @Override
        public Set<KeyAddress> getTransactionUnitsIssuerKeys() {
            return config.getTransactionUnitsIssuerKeys();
        }

        @Override
        public String getTUIssuerName() {
            return config.getTUIssuerName();
        }

        @Override
        public int getMinPayment(String extendedType) {
            return config.getMinPayment(extendedType);
        }

        @Override
        public double getRate(String extendedType) {
            return config.getRate(extendedType);
        }

        @Override
        public Collection<PublicKey> getAdditionalKeysToSignWith(String extendedType) {
            Set<PublicKey> set = new HashSet<>();
            if(extendedType.equals(NSmartContract.SmartContractType.UNS1)) {
                set.add(config.getAuthorizedNameServiceCenterKey());
            }
            return set;
        }
    };

    private ScheduledExecutorService lowPrioExecutorService = new ScheduledThreadPoolExecutor(16, new ThreadFactory() {

        private final ThreadGroup threadGroup = new ThreadGroup("low-prio-node-workers");

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(threadGroup,r);
            thread.setName("low-prio-node-"+myInfo.getNumber()+"-worker");
            thread.setPriority((Thread.NORM_PRIORITY+Thread.MIN_PRIORITY)/2);
            return thread;
        }
    });

    public Node(Config config, NodeInfo myInfo, Ledger ledger, Network network) {

        this.config = config;
        this.myInfo = myInfo;
        this.ledger = ledger;
        this.network = network;
        cache = new ItemCache(config.getMaxCacheAge());
        parcelCache = new ParcelCache(config.getMaxCacheAge());
        envCache = new EnvCache(config.getMaxCacheAge());
        nameCache = new NameCache(config.getMaxNameCacheAge());
        config.updateConsensusConfig(network.getNodesCount());

        label = "Node(" + myInfo.getNumber() + ") ";

        network.subscribe(myInfo, notification -> onNotification(notification));

        recordsToSanitate = ledger.findUnfinished();

        System.out.println(label + " " + recordsToSanitate.size());

        if(!recordsToSanitate.isEmpty()) {
            pulseStartSanitation();
//            try {
//                sanitationFinished.await();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
        } else {
            dbSanitationFinished();
        }

        pulseStartCleanup();

    }

    private void pulseStartCleanup() {
        lowPrioExecutorService.scheduleAtFixedRate(() -> ledger.cleanup(),1,config.getMaxDiskCacheAge().getSeconds(),TimeUnit.SECONDS);
        lowPrioExecutorService.scheduleAtFixedRate(() -> ledger.removeExpiredStorageSubscriptionsCascade(),config.getExpriedStorageCleanupInterval().getSeconds(),config.getExpriedStorageCleanupInterval().getSeconds(),TimeUnit.SECONDS);
        lowPrioExecutorService.scheduleAtFixedRate(() -> ledger.clearExpiredNameRecords(config.getHoldDuration()),config.getExpriedNamesCleanupInterval().getSeconds(),config.getExpriedNamesCleanupInterval().getSeconds(),TimeUnit.SECONDS);
    }

    private void dbSanitationFinished() {

        sanitationFinished.fire();

        nodeStats.init(ledger,config);

        pulseCollectStats();
    }

    private void pulseCollectStats() {
        statsCollector = executorService.scheduleAtFixedRate(() -> {
            if(!nodeStats.collect(ledger,config)) {
                //config changed. stats aren't collected and being reset
                statsCollector.cancel(false);
                statsCollector = null;
                pulseCollectStats();
            }
        },config.getStatsIntervalSmall().getSeconds(),config.getStatsIntervalSmall().getSeconds(),TimeUnit.SECONDS);
    }


    private void pulseStartSanitation() {
        sanitator = lowPrioExecutorService.scheduleAtFixedRate(() -> startSanitation(),
                2000,
                500,
                TimeUnit.MILLISECONDS//,
//                                        Node.this.toString() + toString() + " :: pulseStartPolling -> sendStartPollingNotification"
        );
    }

    ArrayList<HashId> sanitatingIds = new ArrayList<>();

    private void startSanitation() {
        if(recordsToSanitate.isEmpty()) {
            sanitator.cancel(false);
            dbSanitationFinished();
            return;
        }

        for (int i = 0; i < sanitatingIds.size(); i++) {
            if (!recordsToSanitate.containsKey(sanitatingIds.get(i))) {
                sanitatingIds.remove(i);
                --i;
            }
        }

        if (sanitatingIds.size() < MAX_SANITATING_RECORDS) {
            synchronized (recordsToSanitate) {
                for (StateRecord r : recordsToSanitate.values()) {
                    if (r.getState() != ItemState.LOCKED && r.getState() != ItemState.LOCKED_FOR_CREATION && !sanitatingIds.contains(r.getId())) {
                        sanitateRecord(r);
                        sanitatingIds.add(r.getId());
                        if (sanitatingIds.size() == MAX_SANITATING_RECORDS) {
                            break;
                        }
                    }
                }
            }
            if (sanitatingIds.size() == 0 && recordsToSanitate.size() > 0) {
                //ONLY LOCKED LEFT -> RESYNC THEM
                synchronized (recordsToSanitate) {
                    for (StateRecord r : recordsToSanitate.values()) {
                        r.setState(ItemState.PENDING);
                        try {
                            itemLock.synchronize(r.getId(), lock -> {
                                r.save();
                                synchronized (cache) {
                                    cache.update(r.getId(), new ItemResult(r));
                                }
                                return null;
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }


    }

    private void sanitateRecord(StateRecord r) {
        try {
            if(isShuttingDown)
                return;
            resync(r.getId());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    /**
//     * Looking for all locked items in ledger and try to resync them.
//     */
//    public void resyncAllLocked() {
//        List<Approvable> items = new ArrayList<>();
//        ledger.getAllByState(ItemState.LOCKED);
//        ledger.getAllByState(ItemState.LOCKED_FOR_CREATION);
//    }

    /**
     * Asynchronous (non blocking) check/register for item from white list. If the item is new and eligible to process with the
     * consensus, the processing will be started immediately. If it is already processing, the current state will be
     * returned.
     *
     * If item is not signed by keys from white list will return {@link ItemResult#UNDEFINED}
     *
     * @param item to register/check state
     *
     * @return current (or last known) item state
     */
    public @NonNull ItemResult registerItem(Approvable item) {

        report(getLabel(), () -> concatReportMessage("register item: ", item.getId()),
                DatagramAdapter.VerboseLevel.BASE);
//        if (item.isInWhiteList(config.getKeysWhiteList())) {
        Object x = checkItemInternal(item.getId(), null, item, true, true);

        ItemResult ir = (x instanceof ItemResult) ? (ItemResult) x : ((ItemProcessor) x).getResult();
        report(getLabel(), () -> concatReportMessage("item processor for: ", item.getId(),
                " was created, state is ", ir.state),
                DatagramAdapter.VerboseLevel.BASE);
        return ir;
//        }
//
//        report(getLabel(), () -> concatReportMessage("item: ", item.getId(), " not belongs to whitelist"),
//                DatagramAdapter.VerboseLevel.BASE);
//
//        return ItemResult.UNDEFINED;
    }

    /**
     * Asynchronous (non blocking) parcel (contract with payment) register.
     * Use Node.waitParcel for waiting parcel being processed.
     * For checking parcel parts use Node.waitItem or Node.checkItem after Node.waitParcel
     * with parcel.getPayloadContract().getId() or parcel.getPaymentContract().getId() as params.
     *
     * @param parcel to register/check state
     *
     * @return true if Parcel launch to processing. Otherwise exception will be thrown.
     */
    public boolean registerParcel(Parcel parcel) {

        report(getLabel(), () -> concatReportMessage("register parcel: ", parcel.getId()),
                DatagramAdapter.VerboseLevel.BASE);
        try {
            Object x = checkParcelInternal(parcel.getId(), parcel, true);
            if (x instanceof ParcelProcessor) {
                report(getLabel(), () -> concatReportMessage("parcel processor created for parcel: ",
                        parcel.getId(), ", state is ", ((ParcelProcessor) x).processingState),
                        DatagramAdapter.VerboseLevel.BASE);
                return true;
            }

            report(getLabel(), () -> concatReportMessage("parcel processor hasn't created: ",
                    parcel.getId()),
                    DatagramAdapter.VerboseLevel.BASE);
            return false;

        } catch (Exception e) {
            report(getLabel(), () -> concatReportMessage("register parcel: ", parcel.getId(),
                    "failed", e.getMessage()),
                    DatagramAdapter.VerboseLevel.BASE);
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

        report(getLabel(), () -> concatReportMessage("check item processor state for item: ",
                itemId),
                DatagramAdapter.VerboseLevel.BASE);
        Object x = checkItemInternal(itemId);
        ItemResult ir = (x instanceof ItemResult) ? (ItemResult) x : ((ItemProcessor) x).getResult();

        report(getLabel(), () -> concatReportMessage("item state for: ",
                itemId, "is ", ir.state),
                DatagramAdapter.VerboseLevel.BASE);

        ItemInformer.Record record = informer.takeFor(itemId);
        if (record != null)
            ir.errors = record.errorRecords;

        ir.isTestnet = ledger.isTestnet(itemId);

        return ir;
    }

    /**
     * Check the parcel's processing state. If parcel is not under processing (not start or already finished)
     * return ParcelProcessingState.NOT_EXIST
     *
     * @param parcelId parcel to check
     *
     * @return processing state
     */
    public @NonNull ParcelProcessingState checkParcelProcessingState(HashId parcelId) {

        report(getLabel(), () -> concatReportMessage("check parcel processor state for parcel: ",
                parcelId),
                DatagramAdapter.VerboseLevel.BASE);
        Object x = checkParcelInternal(parcelId);
        if (x instanceof ParcelProcessor) {
            report(getLabel(), () -> concatReportMessage("parcel processor for parcel: ",
                    parcelId, " state is ", ((ParcelProcessor) x).processingState),
                    DatagramAdapter.VerboseLevel.BASE);
            return ((ParcelProcessor) x).processingState;
        }
        report(getLabel(), () -> concatReportMessage("parcel processor for parcel: ",
                parcelId, " was not found"),
                DatagramAdapter.VerboseLevel.BASE);

        return ParcelProcessingState.NOT_EXIST;
    }


    @Deprecated
    public @NonNull Binder extendedCheckItem(HashId itemId) {

        ItemResult ir = checkItem(itemId);
        Binder result = Binder.of("itemResult", ir);
        if (ir != null && ir.state == ItemState.LOCKED) {
            ir.lockedById = ledger.getLockOwnerOf(itemId).getId();
        }
        return result;
    }


    /**
     * Resync the item.
     * This method launch resync process, call to network to know what consensus is or hasn't consensus for the item.
     *
     * @param id item to resync
     * @throws Exception with various types
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
     * If the item is being electing, block until the item been processed with the consensus. Otherwise
     * returns state immediately.
     *
     * @param itemId item to check or wait for
     * @param millisToWait is time to wait in milliseconds
     * @return item state
     * @throws TimeoutException for timeout
     * @throws InterruptedException for unexpected interrupt
     */
    public ItemResult waitItem(HashId itemId, long millisToWait) throws TimeoutException, InterruptedException {

        Object x = null;

        x = checkItemInternal(itemId);
        if (x instanceof ItemProcessor) {
            if(!((ItemProcessor) x).isDone()) {
                ((ItemProcessor) x).doneEvent.await(millisToWait);
            }

            return ((ItemProcessor) x).getResult();
        }
        return (ItemResult) x;
    }

    /**
     * If the parcel is being processing, block until the parcel been processed (been processed payment and payload contracts).
     *
     * @param parcelId parcel to wait for
     * @param millisToWait is time to wait in milliseconds
     * @throws TimeoutException for timeout
     * @throws InterruptedException for unexpected interrupt
     */
    public void waitParcel(HashId parcelId, long millisToWait) throws TimeoutException, InterruptedException {

        Object x = null;

        // first check if item is processing as part of parcel
        x = checkParcelInternal(parcelId);
        if (x instanceof ParcelProcessor) {
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

        HashMap<HashId, ItemState> itemsToResync = notification.getItemsToResync();
        Set<HashId> itemsWithEnvironments = notification.getItemsWithEnvironment();

        HashMap<HashId, ItemState> answersForItems = new HashMap<>();
        Set<HashId> answersForEnvironments = new HashSet<>();

        NodeInfo from = notification.getFrom();

        // get processor, do not create new if not exist
        // register resync vote for waiting processor or deliver resync vote
        Object itemObject = checkItemInternal(notification.getItemId());



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

                // we answer only states with consensus, in other cases we answer ItemState.UNDEFINED
                if (subItemResult != null) {
                    subItemState = subItemResult.state.isConsensusFound() ? subItemResult.state : ItemState.UNDEFINED;
                    if(subItemResult.state == ItemState.APPROVED) {
                        NImmutableEnvironment ime =  getEnvironment(hid);
                        if(ime != null) {
                            answersForEnvironments.add(hid);
                        }
                    }
                } else {
                    subItemState = ItemState.UNDEFINED;
                }

                answersForItems.put(hid, subItemState);
            }

            network.deliver(
                    from,
                    new ItemResyncNotification(myInfo, notification.getItemId(), answersForItems,answersForEnvironments, false)
            );
        }

        if (itemObject instanceof ItemProcessor) {
            ItemProcessor ip = (ItemProcessor) itemObject;
            if(ip.processingState.isResyncing()) {
                ip.lock(() -> {
                    for (HashId hid : itemsToResync.keySet()) {
                        ip.resyncVote(hid, from, itemsToResync.get(hid));
                        if(itemsWithEnvironments.contains(hid)) {
                            ip.addEnvToSources(hid,from);
                        }
                    }


                    return null;
                });
            }
        }
    }

    private NImmutableEnvironment getEnvironment(HashId hid) {
        NImmutableEnvironment result = envCache.get(hid);
        if(result == null) {
            result = ledger.getEnvironment(hid);
            if(result != null) {
                envCache.put(result);
            }
        }
        return result;
    }

    private NImmutableEnvironment getEnvironment(NSmartContract item) {
        NImmutableEnvironment result = envCache.get(item.getId());

        if(result == null && item.getParent() != null) {
            result = envCache.get(item.getParent());
        }

        if(result == null) {
            result = ledger.getEnvironment(item);
            envCache.put(result);
        }
        return result;
    }

    private NImmutableEnvironment getEnvironment(Long environmentId) {
        NImmutableEnvironment result = envCache.get(environmentId);
        if(result == null) {
            result = ledger.getEnvironment(environmentId);
            if(result != null) {
                envCache.put(result);
            }
        }

        return result;
    }

    private void removeEnvironment(HashId id) {
        envCache.remove(id);
        ledger.removeEnvironment(id);
    }




    /**
     * Obtain got common item notification: looking for result or item processor and register vote
     *
     * @param notification common item notification
     *
     */
    private final void obtainCommonNotification(ItemNotification notification) {

        // get processor, create if need
        // register my vote
        Object x = checkItemInternal(notification.getItemId(), null, null, true, true);
        NodeInfo from = notification.getFrom();

        // If it is not ParcelNotification we think t is payment type of notification
        ParcelNotification.ParcelNotificationType notType;
        if(notification instanceof ParcelNotification) {
            notType = ((ParcelNotification)notification).getType();
        } else {
            notType = ParcelNotification.ParcelNotificationType.PAYMENT;
        }

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
                                notType)
                );
            }
        } else if (x instanceof ItemProcessor) {
            ItemProcessor ip = (ItemProcessor) x;
            ItemResult result = notification.getItemResult();
            ip.lock(() -> {

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
                                    notType)
                    );
                }
                return null;
            });
        }
    }


    /**
     * Obtain got common parcel notification: looking for result or parcel processor and register vote
     *
     * @param notification common item notification
     *
     */
    private final void obtainParcelCommonNotification(ParcelNotification notification) {

        // if notification hasn't parcelId we think this is simple item notification and obtain it as it
        if(notification.getParcelId() == null) {
            obtainCommonNotification(notification);
        } else {

            // check if item for notification is already processed
            Object item_x = checkItemInternal(notification.getItemId());
            // if already processed and result has consensus - answer immediately
            if (item_x instanceof ItemResult && ((ItemResult) item_x).state.isConsensusFound()) {
                NodeInfo from = notification.getFrom();
                ItemResult r = (ItemResult) item_x;
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
            } else {
                // if we haven't results for item, we looking for or create parcel processor
                Object x = checkParcelInternal(notification.getParcelId(), null, true);
                NodeInfo from = notification.getFrom();

                if (x instanceof ParcelProcessor) {
                    ParcelProcessor pp = (ParcelProcessor) x;
                    ItemResult resultVote = notification.getItemResult();
                    pp.lock(() -> {

                        // we might still need to download and process it
                        if (resultVote.haveCopy) {
                            pp.addToSources(from);
                        }
                        if (resultVote.state != ItemState.PENDING)
                            pp.vote(from, resultVote.state, notification.getType().isTU());
                        else
                            log.e("pending vote on parcel " + notification.getParcelId()
                                    + " and item " + notification.getItemId() + " from " + from);

                        // We answer only if (1) answer is requested and (2) we have position on the subject:
                        if (notification.answerIsRequested()) {
                            // if notification type is payment, we use payment data from parcel, otherwise we use payload data
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
                }
            }
        }
    }

    private Object checkItemInternal(@NonNull HashId itemId) {
        return checkItemInternal(itemId, null, null, false, false);
    }

    private Object checkItemInternal(@NonNull HashId itemId, HashId parcelId, Approvable item, boolean autoStart, boolean forceChecking) {

        return checkItemInternal(itemId, parcelId, item, autoStart, forceChecking, false);
    }

    /**
     * Optimized for various usages, check the item, start processing as need, return object depending on the current
     * state. Note that actual error codes are set to the item itself.
     *
     * @param itemId    item to check the state.
     * @param item      provide item if any, can be null. Default is null.
     * @param autoStart - create new ItemProcessor if not exist. Default is false.
     * @param forceChecking - point item processor to wait (if false) with item checking or start without waiting (if true).
     *                      Default is false. Use ItemProcessor.forceChecking() to start waiting item checking.
     * @param ommitItemResult - do not return ItemResult for processed item,
     *                        create new ItemProcessor instead (if autoStart is true). Default is false.
     *
     * @return instance of ItemProcessor if the item is being processed (also if it was started by the call),
     *         ItemResult if it is already processed or can't be processed, say, created_at field is too far in
     *         the past, in which case result state will be ItemState#DISCARDED.
     */
    private Object checkItemInternal(@NonNull HashId itemId, HashId parcelId, Approvable item,
                                       boolean autoStart, boolean forceChecking, boolean ommitItemResult) {
        try {
            // first, let's lock to the item id:
            report(getLabel(), () -> concatReportMessage("checkItemInternal: ", itemId),
                    DatagramAdapter.VerboseLevel.BASE);
            return itemLock.synchronize(itemId, (lock) -> {
                ItemProcessor ip = processors.get(itemId);
                if (ip != null) {
                    report(getLabel(), () -> concatReportMessage("checkItemInternal: ", itemId,
                            "found item processor in state: ", ip.processingState),
                            DatagramAdapter.VerboseLevel.BASE);
                    return ip;
                }

                // if we want to get already processed result for item
                if(!ommitItemResult) {
                    StateRecord r = ledger.getRecord(itemId);
                    // if it is not pending, it means it is already processed:
                    if (r != null && !r.isPending()) {
                        // it is, and we may still have it cached - we do not put it again:
                        report(getLabel(), () -> concatReportMessage("checkItemInternal: ", itemId,
                                "found item result, and state is: ", r.getState()),
                                DatagramAdapter.VerboseLevel.BASE);

                        Approvable cachedItem = cache.get(itemId);
                        ItemResult result = cache.getResult(itemId);
                        if(result == null) {
                            result = new ItemResult(r, cachedItem != null);
                        }

                        return result;
                    }

                    // we have no consensus on it. We might need to find one, after some precheck.
                    // The contract should not be too old to process:
                    if (item != null &&
                            item.getCreatedAt().isBefore(ZonedDateTime.now().minus(config.getMaxItemCreationAge()))) {
                        // it is too old - client must manually check other nodes. For us it's unknown
                        item.addError(Errors.EXPIRED, "created_at", "too old");
                        report(getLabel(), () -> concatReportMessage("checkItemInternal: ", itemId,
                                "too old: "),
                                DatagramAdapter.VerboseLevel.BASE);
                        return ItemResult.DISCARDED;
                    }
                }

                // if we want to create new ItemProcessor
                if (autoStart) {
                    if (item != null) {
                        synchronized (cache) {
                            cache.put(item, ItemResult.UNDEFINED);
                        }
                    }
                    report(getLabel(), () -> concatReportMessage("checkItemInternal: ", itemId,
                            "nothing found, will create item processor"),
                            DatagramAdapter.VerboseLevel.BASE);
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
     * state. Note that actual error codes are set to the item itself. Use in pair with checkItemInternal() to check parts of parcel.
     *
     * @param parcelId    parcel's id.
     * @param parcel      provide parcel if need, can be null. Default is null.
     * @param autoStart - create new ParcelProcessor if not exist. Default is false.
     *
     * @return instance of ParcelProcessor if the parcel is being processed (also if it was started by the call),
     *         ItemResult if it is can't be processed.
     */
    protected Object checkParcelInternal(@NonNull HashId parcelId, Parcel parcel, boolean autoStart) {
        try {
            return parcelLock.synchronize(parcelId, (lock) -> {
                // let's look existing parcel processor
                ParcelProcessor processor = parcelProcessors.get(parcelId);
                if (processor != null) {
                    return processor;
                }

                // if nothing found and need to create new - create it
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


    private SimpleDateFormat dataFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");

    @Override
    public String toString() {
        return "[" + dataFormat.format(new Date()) + "] Node(" + myInfo.getNumber() + ") ";
    }


    public String getLabel()
    {
        return label;
    }


    /**
     * Get the cached item.
     *
     * @param itemId is {@link HashId} of the looking item
     *
     * @return cached item or null if it is missing
     */
    public Approvable getItem(HashId itemId) {
        synchronized (cache) {
            @Nullable Approvable i = cache.get(itemId);
            return i;
        }
    }

    /**
     * Get the cached parcel.
     *
     * @param parcelId is {@link HashId} of {@link Parcel}
     *
     * @return cached {@link Parcel} or null if it is missing
     */
    public Parcel getParcel(HashId parcelId) {
        synchronized (parcelCache) {
            @Nullable Parcel i = parcelCache.get(parcelId);
            return i;
        }
    }

    public int countElections() {
        return processors.size();
    }

    public ItemCache getCache() {
        return cache;
    }

    public ParcelCache getParcelCache() {
        return parcelCache;
    }

    public Ledger getLedger() {
        return ledger;
    }

    public void shutdown() {
        isShuttingDown = true;
        System.out.println(toString() + "please wait, shutting down has started, num alive item processors: " + processors.size());
        for (ItemProcessor ip : processors.values()) {
            ip.emergencyBreak();
        }

        while(processors.size() > 0) {
            System.out.println("---------------------------------------------");
            System.out.println(toString() + "please wait, shutting down is still continue, num alive item processors: " + processors.size());
            for (HashId hid : processors.keySet()) {
                ItemProcessor ipr = processors.get(hid);
                System.out.println(toString() + "processor " + hid + " is " + ipr);
                ipr.emergencyBreak();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        System.out.println(toString() + "please wait, executorService is shutting down");
        executorService.shutdown();
        lowPrioExecutorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("executorService.awaitTermination... timeout");
        }
        try {
            lowPrioExecutorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            System.out.println("lowPrioExecutorService.awaitTermination... timeout");
        }
        cache.shutdown();
        parcelCache.shutdown();
        nameCache.shutdown();
        System.out.println(toString() + "shutdown finished");
    }





    public int getVerboseLevel() {
        return verboseLevel;
    }

    public void setVerboseLevel(int level) {
        this.verboseLevel = level;
    }


    public void report(String label, String message, int level)
    {
        if(level <= verboseLevel)
            System.out.println(label + message);
    }


    public void report(String label, Callable<String> message, int level)
    {
        if(level <= verboseLevel)
            try {
                System.out.println(label + message.call());
            } catch (Exception e) {
                e.printStackTrace();
            }
    }


    public void report(String label, String message)
    {
        report(label, message, DatagramAdapter.VerboseLevel.DETAILED);
    }


    public void report(String label, Callable<String> message)
    {
        report(label, message, DatagramAdapter.VerboseLevel.DETAILED);
    }

    protected String concatReportMessage(Object... messages) {
        String returnMessage = "";
        for (Object m : messages) {
            returnMessage += m != null ? m.toString() : "null";
        }
        return returnMessage;
    }


    public void addNode(NodeInfo nodeToAdd) {
        ledger.addNode(nodeToAdd);
        network.addNode(nodeToAdd);
        if(!config.updateConsensusConfig(network.getNodesCount())) {
            throw new RuntimeException("Dynamic consensus reconfigurator isn't set");
        }

    }

    public void removeNode(NodeInfo nodeToRemove) {
        ledger.removeNode(nodeToRemove);
        network.removeNode(nodeToRemove);
        if(!config.updateConsensusConfig(network.getNodesCount())) {
            throw new RuntimeException("Dynamic consensus reconfigurator isn't set");
        }
    }

    public Binder provideStats(Integer showDays) {
        if(nodeStats.nodeStartTime == null)
            throw new IllegalStateException("node state are not initialized. wait for node initialization to finish.");

        Binder result = Binder.of(
                "uptime", Instant.now().getEpochSecond() - nodeStats.nodeStartTime.toEpochSecond(),
                "ledgerSize", nodeStats.ledgerSize.isEmpty() ? 0 : nodeStats.ledgerSize.values().stream().reduce((i1, i2) -> i1 + i2).get(),
                "smallIntervalApproved", nodeStats.smallIntervalApproved,
                "bigIntervalApproved", nodeStats.bigIntervalApproved,
                "uptimeApproved", nodeStats.uptimeApproved,
                "coreVersion", Core.VERSION,
                "nodeNumber", myInfo.getNumber()
                );
        if(showDays != null) {
            result.put("payments",nodeStats.getPaymentStats(ledger,showDays));
        }

        return result;
    }

    public void setNeworkVerboseLevel(int level) {
        if(network instanceof NetworkV2) {
            ((NetworkV2)network).setVerboseLevel(level);
        }
    }

    public void setUDPVerboseLevel(int level) {
        if(network instanceof NetworkV2) {
            ((NetworkV2)network).setUDPVerboseLevel(level);
        }
    }

    public EnvCache getEnvCache() {
        return envCache;
    }

    public PublicKey getNodeKey() {
        return myInfo.getPublicKey();
    }

    public int getNumber() {
        return myInfo.getNumber();
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


        /**
         * Processor for parcel that should be processed.
         *
         * Parcel's processor download parcel or get it from constructor params;
         * then run {@link Node#checkItemInternal(HashId, HashId, Approvable, boolean, boolean, boolean)} for both
         * payment and payload items, but with isCheckingForce param set to false for payload: payload checking wait
         * for payment item will be processed (goes through {@link ParcelProcessingState#PREPARING},
         * {@link ParcelProcessingState#PAYMENT_CHECKING}, {@link ParcelProcessingState#PAYMENT_POLLING} processing states);
         * after payment have been processed payload is start checking (goes through
         * {@link ParcelProcessingState#PAYLOAD_CHECKING}, {@link ParcelProcessingState#PAYLOAD_POLLING}); finally
         * parcel's processor removing (goes through  {@link ParcelProcessingState#FINISHED},
         * {@link ParcelProcessingState#NOT_EXIST} processing states).
         *
         * @param parcelId is parcel's id to processing
         * @param parcel is {@link Parcel} if exists. Will download if not exists.
         * @param lock is lock object for parcel.
         */
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

            report(getLabel(), () -> concatReportMessage("parcel processor for: ", parcelId, " created"),
                    DatagramAdapter.VerboseLevel.BASE);

            if (this.parcel != null)
                 executorService.submit(() -> parcelDownloaded(),
                         Node.this.toString() + " pp > parcel " + parcelId + " :: ParcelProcessor -> parcelDownloaded");
        }

        //////////// processing section /////////////

        private void pulseProcessing() {
            report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                    parcelId, " :: pulseProcessing, state ", processingState),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {
                synchronized (mutex) {
                    if (processSchedule == null || processSchedule.isDone()) {
                        processSchedule = (ScheduledFuture<?>) executorService.submit(() -> process(),
                                Node.this.toString() + " pp > parcel " + parcelId + " :: pulseProcessing -> process");
                    }
                }
            }
        }

        /**
         * Main process of processor. Here processor wait until payment will checked and approved.
         * Then wait decision about payload contract.
         */
        private void process() {
            report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                    parcelId, " :: process, payment ",
                    payment.getId(), ", payload ",
                    payload.getId(), ", state ", processingState),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {

                processingState = ParcelProcessingState.PREPARING;
                try {
                    report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                            parcelId, " :: check payment, state ", processingState),
                            DatagramAdapter.VerboseLevel.BASE);
                    // wait payment
                    if (paymentResult == null) {
                        processingState = ParcelProcessingState.PAYMENT_CHECKING;

                        for (NodeInfo ni : paymentDelayedVotes.keySet())
                            paymentProcessor.vote(ni, paymentDelayedVotes.get(ni));
                        paymentDelayedVotes.clear();

                        processingState = ParcelProcessingState.PAYMENT_POLLING;
                        if(!paymentProcessor.isDone()) {
                            paymentProcessor.doneEvent.await();
                        }
                        paymentResult = paymentProcessor.getResult();
                    }

                    report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                            parcelId, " :: payment checked, state ", processingState),
                            DatagramAdapter.VerboseLevel.BASE);
                    // if payment is ok, wait payload
                    if (paymentResult.state.isApproved()) {
                        if(!payment.isLimitedForTestnet())
                            ledger.savePayment(parcel.getQuantasLimit()/Quantiser.quantaPerU, paymentProcessor != null ? paymentProcessor.record.getCreatedAt() : ledger.getRecord(payment.getId()).getCreatedAt());

                        report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                                parcelId, " :: check payload, state ", processingState),
                                DatagramAdapter.VerboseLevel.BASE);


                        if (payment.getOrigin().equals(payload.getOrigin())) {
                            payload.addError(Errors.BADSTATE, payload.getId().toString(), "can't register contract with same origin as payment contract ");

                            payloadProcessor.emergencyBreak();
                            payloadProcessor.doneEvent.await();
                        } else {

                            if (payloadResult == null) {

                                processingState = ParcelProcessingState.PAYLOAD_CHECKING;

                                payload.getQuantiser().reset(parcel.getQuantasLimit());

                                // force payload checking (we've freeze it at processor start)
                                payloadProcessor.forceChecking(true);

                                for (NodeInfo ni : payloadDelayedVotes.keySet())
                                    payloadProcessor.vote(ni, payloadDelayedVotes.get(ni));
                                payloadDelayedVotes.clear();

                                processingState = ParcelProcessingState.PAYLOAD_POLLING;
                                if (!payloadProcessor.isDone()) {
                                    payloadProcessor.doneEvent.await();
                                }
                                payloadResult = payloadProcessor.getResult();
                            }

                            if ((payloadResult != null) && payloadResult.state.isApproved())
                                if(!payload.isLimitedForTestnet()) {
                                    int paidU = payload.getStateData().getInt(NSmartContract.PAID_U_FIELD_NAME, 0);
                                    if (paidU > 0)
                                        ledger.savePayment(paidU, payloadProcessor != null ? payloadProcessor.record.getCreatedAt() : ledger.getRecord(payload.getId()).getCreatedAt());
                                }
                        }
                        report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                                parcelId, " :: payload checked, state ", processingState),
                                DatagramAdapter.VerboseLevel.BASE);
                    } else {
                        report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                                parcelId, " :: payment was not approved: ", paymentResult.state,
                                ", state ", processingState),
                                DatagramAdapter.VerboseLevel.BASE);
                        if(payloadProcessor != null) {
                            payloadProcessor.emergencyBreak();
                            payloadProcessor.doneEvent.await();
                        }
                    }

                    // we got payment and payload result, can fire done event for waiters
                    processingState = ParcelProcessingState.FINISHED;

                    report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                            parcelId, " :: processing finished, state ", processingState),
                            DatagramAdapter.VerboseLevel.BASE);

                    doneEvent.fire();

                    // but we want to wait until paymentProcessor and payloadProcessor will be removed
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
                } catch (Exception e) {
                    e.printStackTrace();
                    processingState = ParcelProcessingState.FINISHED;
                    doneEvent.fire();
                }

                removeSelf();
            }
        }

        private void stopProcesser() {
            if (processSchedule != null)
                processSchedule.cancel(true);
        }

        //////////// download section /////////////

        private void pulseDownload() {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    processingState = ParcelProcessingState.DOWNLOADING;

                    synchronized (mutex) {
                        if (parcel == null && (downloader == null || downloader.isDone())) {
                            downloader = (ScheduledFuture<?>) executorService.submit(() -> download(),
                                    Node.this.toString() + " > parcel " + parcelId + " :: parcel pulseDownload -> download");
                        }
                    }
                }
            }
        }

        private void download() {
            if(processingState.canContinue()) {

                int retryCounter = config.getGetItemRetryCount();
                while (!isPayloadPollingExpired() && parcel == null) {
                    if (sources.isEmpty()) {
//                        log.e("empty sources for download tasks, stopping");
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
                                parcelDownloaded();
                                return;
                            } else {
                                Thread.sleep(1000);
                                retryCounter -= 1;
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (retryCounter <= 0)
                        return;
                }
            }
        }

        private final void parcelDownloaded() {

            report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                    parcelId, " :: parcelDownloaded, state ", processingState),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {
                synchronized (parcelCache) {
                    parcelCache.put(parcel);
                }

                payment = parcel.getPaymentContract();
                payload = parcel.getPayloadContract();

                // create item processors or get results for payment and payload
                synchronized (mutex) {

                    Object x = checkItemInternal(payment.getId(), parcelId, payment, true, true);
                    if (x instanceof ItemProcessor) {
                        paymentProcessor = ((ItemProcessor) x);
                        report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                                parcelId, " :: payment is processing, item processing state: ",
                                paymentProcessor.processingState, ", parcel processing state ", processingState,
                                ", item state ", paymentProcessor.getState()),
                                DatagramAdapter.VerboseLevel.BASE);

                        // if current item processor for payment was inited by another parcel we should decline this payment
                        if(!parcelId.equals(paymentProcessor.parcelId)) {
                            paymentResult = ItemResult.UNDEFINED;
                        }
                    } else {
                        paymentResult = (ItemResult) x;
                        report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                                parcelId, " :: payment already processed, parcel processing state ",
                                processingState,
                                ", item state ", paymentResult.state),
                                DatagramAdapter.VerboseLevel.BASE);

                        // if ledger already have approved state for payment it means onw of two:
                        // 1. payment was already processed and cannot be used as payment for current parcel
                        // 2. payment having been processing but this node starts too old and consensus already got.
                        // So, in both ways we can answer undefined
                        if (paymentResult.state == ItemState.APPROVED) {
                            paymentResult = ItemResult.UNDEFINED;
                        }
                    }
                    // we freeze payload checking until payment will be approved
                    x = checkItemInternal(payload.getId(), parcelId, payload, true, false);
                    if (x instanceof ItemProcessor) {
                        payloadProcessor = ((ItemProcessor) x);
                        report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                                parcelId, " :: payload is processing, item processing state: ",
                                payloadProcessor.processingState, ", parcel processing state ", processingState,
                                ", item state ", payloadProcessor.getState()),
                                DatagramAdapter.VerboseLevel.BASE);
                    } else {
                        payloadResult = (ItemResult) x;
                        report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                                parcelId, " :: payload already processed, parcel processing state ",
                                processingState,
                                ", item state ", payloadResult.state),
                                DatagramAdapter.VerboseLevel.BASE);
                    }
                }

                pulseProcessing();
                downloadedEvent.fire();
            }
        }

        private void stopDownloader() {
            if (downloader != null)
                downloader.cancel(true);
        }


        //////////// polling section /////////////

        private final void vote(NodeInfo node, ItemState state, boolean isTU) {
            if(processingState.canContinue()) {

                // if we got vote but item processor not exist yet - we store that vote.
                // Otherwise we give vote to item processor
                if(isTU){
                    if (paymentProcessor != null) {
                        paymentProcessor.vote(node, state);
                    } else {
                        paymentDelayedVotes.put(node, state);
                    }
                } else {
                    if (payloadProcessor != null) {
                        payloadProcessor.vote(node, state);
                    } else {
                        payloadDelayedVotes.put(node, state);
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
         * true if we need to get payload vote from a node
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
         * true if we need to get payment vote from a node
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
                    pulseDownload();
                }
            }
        }

        /**
         * Remove parcel processor from the Node and stop all processes.
         */
        private final void removeSelf() {
            report(getLabel(), () -> concatReportMessage("parcel processor for: ",
                    parcelId, " :: removeSelf, state ", processingState),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canRemoveSelf()) {
                parcelProcessors.remove(parcelId);

                stopDownloader();
                stopProcesser();

                doneEvent.fire();
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
    }

    /// ItemProcessor ///

    private class ItemProcessor {

        private final HashId itemId;
        private final HashId parcelId;
        //private final Config config;
        //private final List<NodeInfo> nodes;
        private Approvable item;
        private final StateRecord record;
        private final ItemState stateWas;
        private ItemProcessingState processingState;
        private Set<NodeInfo> sources = new HashSet<>();
        private Map<HashId,Set<NodeInfo>> envSources = new HashMap<>();

        /**
         * Set true if you resyncing item itself (item will be rollbacked with ItemProcessor if resync will failed).
         */
        private boolean justResycnNoFurtherVoting;

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

        private Binder extraResult = new Binder();

        private final AsyncEvent<Void> downloadedEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> doneEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> pollingReadyEvent = new AsyncEvent<>();
        private final AsyncEvent<Void> removedEvent = new AsyncEvent<>();

        private final Object mutex;
        private final Object resyncMutex;

        private ScheduledFuture<?> downloader;
        private RunnableWithDynamicPeriod poller;
        private RunnableWithDynamicPeriod consensusReceivedChecker;
        private RunnableWithDynamicPeriod resyncer;
        private ScheduledFuture<?> envSaver;

        /**
         * Processor for item that will be processed from check to poll and other processes.
         *
         * Lifecycle of the item processor is:
         * - download
         * - check
         * - resync subitems (optinal)
         * - polling
         * - send consensus
         * - remove
         *
         * First of all item should be downloaded from other node or get from param of a constructor.
         *
         * Then item will be checked. Immediately after download if {@link ItemProcessor#isCheckingForce} is true
         * or after {@link ItemProcessor#forceChecking(boolean)} call. Will call {@link Approvable#check()}
         * or {@link Approvable#paymentCheck(Set)} if item is payment ({@link Approvable#shouldBeU()}).
         * Then subitems will be checked: {@link Approvable#getReferencedItems()} will checked if exists in the ledger;
         * {@link Approvable#getRevokingItems()} will checked if exists in the ledger and its
         * own {@link Approvable#getReferencedItems()} will recursively checked and will get {@link ItemState#LOCKED};
         * {@link Approvable#getNewItems()} will checked if errors exists (after {@link Approvable#check()} -
         * it recursively call check() for new items) and recursively checked for own references, revokes and new items,
         * if all is ok - item will get {@link ItemState#LOCKED_FOR_CREATION} state.
         *
         * While checking, after item itself checking but before subitems checking {@link ItemProcessor#isNeedToResync(boolean)}
         * calling. If return value is true item processor will go to resync subitems. Resync calls to nodes
         * about states of subitems and update consensus states. After resync back to check subitems.
         *
         * After checking item processor run polling. It set {@link ItemState#PENDING_POSITIVE} or {@link ItemState#PENDING_NEGATIVE}
         * state for processing item, send state to the network via {@link ItemProcessor#broadcastMyState()} and run polling.
         * While polling item processing wait for votes from other nodes and collect it
         * using {@link ItemProcessor#vote(NodeInfo, ItemState)}. When consensus is got item processor save item
         * to the ledger with consensus state via {@link ItemProcessor#approveAndCommit()} if consensus is positive or
         * via {@link ItemProcessor#rollbackChanges(ItemState)} if consensus is negative.
         *
         * Then item processor looking for nodes that not answered with for polling and send them new consensus until
         * they will have answered.
         *
         * And finally, if node got answers from all  other nodes - item processor removing via {@link ItemProcessor#removeSelf()}
         *
         * Look at {@link ItemProcessor#processingState} to know what happend with processing at calling time.
         *
         *
         * @param itemId is item's id to be process.
         * @param parcelId is parcel's id that item belongs to.
         * @param item is item object if exist.
         * @param lock is object for synchronization (it is object from {@link ItemLock} that points to item's hashId)
         * @param isCheckingForce if true checking item processing without delays.
         *                        If false checking item wait until forceChecking() will be called.
         */
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

            StateRecord recordWas = null;
            try {
                recordWas = ledger.getRecord(itemId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (recordWas != null) {
                stateWas = recordWas.getState();
            } else {
                stateWas = ItemState.UNDEFINED;
            }

            record = ledger.findOrCreate(itemId);

            pollingExpiresAt = Instant.now().plus(config.getMaxElectionsTime());
            consensusReceivedExpiresAt = Instant.now().plus(config.getMaxConsensusReceivedCheckTime());
            resyncExpiresAt = Instant.now().plus(config.getMaxResyncTime());

            alreadyChecked = false;

            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: created, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);

            if (this.item != null) {
                executorService.submit(() -> itemDownloaded(),
                        Node.this.toString() + toString() + " :: ItemProcessor -> itemDownloaded");
            }
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
                            downloader = (ScheduledFuture<?>) executorService.submit(() -> download(),
                                    Node.this.toString() + toString() + " :: item pulseDownload -> download");
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
                                itemDownloaded();
                                return;
                            } else {
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
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: itemDownloaded, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {
                synchronized (cache) {
                    cache.put(item, getResult());
                }


                synchronized (mutex) {
                    //save item in disk cache
                    ledger.putItem(record, item, Instant.now().plus(config.getMaxDiskCacheAge()));
                }

                if(item instanceof Contract) {
                    if(((Contract)item).isLimitedForTestnet()) {
                        markContractTest((Contract) item);
                    }
                }

                if(!processingState.isProcessedToConsensus()) {
                    processingState = ItemProcessingState.DOWNLOADED;
                }
                if(isCheckingForce) {
                    checkItem();
                }
                downloadedEvent.fire();
            }
        }

        private void markContractTest(Contract contract) {
            ledger.markTestRecord(contract.getId());
            contract.getNew().forEach(c -> markContractTest(c));
        }

        private void stopDownloader() {
            if (downloader != null)
                downloader.cancel(true);
        }

        private void stopEnvSaver() {
            if(envSaver != null) {
                envSaver.cancel(true);
            }
        }

        //////////// check item section /////////////

        private final synchronized void checkItem() {
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: checkItem, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()
                        && processingState != ItemProcessingState.POLLING
                        && processingState != ItemProcessingState.CHECKING
                        && processingState != ItemProcessingState.RESYNCING) {
                    if (alreadyChecked) {
                        throw new RuntimeException("Check already processed");
                    }

                    if(!processingState.isProcessedToConsensus()) {
                        processingState = ItemProcessingState.CHECKING;
                    }

                    // Check the internal state
                    // Too bad if basic check isn't passed, we will not process it further
                    HashMap<HashId, StateRecord> itemsToResync = new HashMap<>();
                    boolean needToResync = false;

                    try {
                        boolean checkPassed = false;

                        if(item.shouldBeU()) {
                            if(item.isU(config.getTransactionUnitsIssuerKeys(), config.getTUIssuerName())) {
                                checkPassed = item.paymentCheck(config.getTransactionUnitsIssuerKeys());
                            } else {
                                checkPassed = false;
                                item.addError(Errors.BADSTATE, item.getId().toString(),
                                        "Item that should be TU contract is not TU contract");
                            }
                        } else {
                            checkPassed = item.check();

                            // if item is smart contract we check it additionally
                            if(item instanceof NSmartContract) {
                                // slot contract need ledger, node's config and nodeInfo to work
                                ((NSmartContract) item).setNodeInfoProvider(nodeInfoProvider);

                                // restore environment if exist, otherwise create new.
                                NImmutableEnvironment ime = getEnvironment((NSmartContract)item);
                                ime.setNameCache(nameCache);
                                // Here can be only APPROVED state, so we call only beforeCreate or beforeUpdate
                                if (((NSmartContract) item).getRevision() == 1) {
                                    if (!((NSmartContract) item).beforeCreate(ime))
                                        item.addError(Errors.FAILED_CHECK, item.getId().toString(), "beforeCreate fails");
                                } else {
                                    if (!((NSmartContract) item).beforeUpdate(ime))
                                        item.addError(Errors.FAILED_CHECK, item.getId().toString(), "beforeUpdate fails");
                                }
                            }
                        }

                        if (checkPassed) {

                            itemsToResync = isNeedToResync(true);
                            needToResync = !itemsToResync.isEmpty();

                            // If no need to resync subItems, check them
                            if (!needToResync) {
                                checkSubItems();
                            }
                        }
                    } catch (Quantiser.QuantiserException e) {
                        item.addError(Errors.FAILURE, item.getId().toString(),
                                "Not enough payment for process item (quantas limit)");
                        emergencyBreak();
                        return;
                    } catch (Exception e) {
                        item.addError(Errors.FAILED_CHECK,item.getId().toString(), "Exception during check: " + e.getMessage());
                        emergencyBreak();
                        return;
                    }
                    alreadyChecked = true;

                    if (!needToResync) {
                        commitCheckedAndStartPolling();
                    } else {
                        for (HashId hid : itemsToResync.keySet()) {
                            addItemToResync(hid, itemsToResync.get(hid));
                        }

                        pulseResync();
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
        private final synchronized void checkSubItemsOf(Approvable checkingItem) {
            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {

                    // check referenced items
                    checkReferencesOf(checkingItem);

                    // check revoking items
                    checkRevokesOf(checkingItem);

                    // check new items
                    checkNewsOf(checkingItem);
                }
            }
        }

        private final synchronized void checkReferencesOf(Approvable checkingItem) {

            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {
                    for (Approvable ref : checkingItem.getReferencedItems()) {
                        HashId id = ref.getId();
                        if (!ledger.isApproved(id)) {
                            checkingItem.addError(Errors.BAD_REF, id.toString(), "reference not approved");
                        }
                    }
                }
            }
        }

        private final synchronized void checkRevokesOf(Approvable checkingItem) {

            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {
                    // check revoking items
                    for (Approvable revokingItem : checkingItem.getRevokingItems()) {

                        if (revokingItem instanceof Contract)
                            ((Contract)revokingItem).getErrors().clear();

                        checkReferencesOf(revokingItem);

                        // if revoking item is smart contract node additionally check it
                        if(revokingItem instanceof NSmartContract) {
                            // slot contract need ledger, node's config and nodeInfo to work
                            ((NSmartContract) revokingItem).setNodeInfoProvider(nodeInfoProvider);

                            // restore environment if exist
                            NImmutableEnvironment ime = getEnvironment((NSmartContract)revokingItem);

                            if(ime != null) {
                                ime.setNameCache(nameCache);
                                // Here only REVOKED states, so we call only beforeRevoke
                                ((NSmartContract) revokingItem).beforeRevoke(ime);
                            } else {
                                revokingItem.addError(Errors.FAILED_CHECK, revokingItem.getId().toString(), "can't load environment to revoke");
                            }
                        }

                        for (ErrorRecord er : revokingItem.getErrors()) {
                            checkingItem.addError(Errors.BAD_REVOKE, revokingItem.getId().toString(), "can't revoke: " + er);
                        }

                        synchronized (mutex) {
                            try {
                                itemLock.synchronize(revokingItem.getId(), lock -> {
                                    StateRecord r = record.lockToRevoke(revokingItem.getId());
                                    if (r == null) {
                                        checkingItem.addError(Errors.BAD_REVOKE, revokingItem.getId().toString(), "can't revoke");
                                    } else {
                                        if (!lockedToRevoke.contains(r))
                                            lockedToRevoke.add(r);
                                    }
                                    return null;
                                });
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }

        private final synchronized void checkNewsOf(Approvable checkingItem) {

            if(processingState.canContinue()) {
                if (!processingState.isProcessedToConsensus()) {
                    // check new items
                    for (Approvable newItem : checkingItem.getNewItems()) {

                        checkSubItemsOf(newItem);

                        // if new item is smart contract we check it additionally
                        if(newItem instanceof NSmartContract) {
                            // slot contract need ledger, node's config and nodeInfo to work
                            ((NSmartContract) newItem).setNodeInfoProvider(nodeInfoProvider);

                            // restore environment if exist, otherwise create new.
                            NImmutableEnvironment ime = getEnvironment((NSmartContract)newItem);
                            ime.setNameCache(nameCache);
                            // Here only APPROVED states, so we call only beforeCreate or beforeUpdate
                            if (((Contract) newItem).getRevision() == 1) {
                                if (!((NSmartContract) newItem).beforeCreate(ime))
                                    newItem.addError(Errors.BAD_NEW_ITEM, item.getId().toString(), "newItem.beforeCreate fails");
                            } else {
                                if (!((NSmartContract) newItem).beforeUpdate(ime))
                                    newItem.addError(Errors.BAD_NEW_ITEM, item.getId().toString(), "newItem.beforeUpdate fails");
                            }
                        }

                        if (!newItem.getErrors().isEmpty()) {
                            checkingItem.addError(Errors.BAD_NEW_ITEM, newItem.getId().toString(), "bad new item: not passed check");
                        } else {
                            synchronized (mutex) {
                                try {
                                    itemLock.synchronize(newItem.getId(), lock -> {
                                        StateRecord r = record.createOutputLockRecord(newItem.getId());
                                        if (r == null) {
                                            checkingItem.addError(Errors.NEW_ITEM_EXISTS, newItem.getId().toString(), "new item exists in ledger");
                                        } else {
                                            if (!lockedToCreate.contains(r))
                                                lockedToCreate.add(r);
                                        }
                                        return null;
                                    });
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
            }
        }

        private final void commitCheckedAndStartPolling() {
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: commitCheckedAndStartPolling, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    boolean checkPassed = item.getErrors().isEmpty();

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

                        record.setExpiresAt(item.getExpiresAt());
                        try {
                            if (record.getState() != ItemState.UNDEFINED) {
                                record.save();

                                if (item != null) {
                                    synchronized (cache) {
                                        cache.update(itemId, getResult());
                                    }
                                }
                            } else {
                                log.e("Checked item with state ItemState.UNDEFINED (should be ItemState.PENDING)");
                                emergencyBreak();
                            }
                        } catch (Ledger.Failure failure) {
                            emergencyBreak();
                            return;
                        }
                    }

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
                    for (Approvable ref : item.getReferencedItems()) {
                        HashId id = ref.getId();
//                        if(refModel.type == Reference.TYPE_EXISTING && id != null) {
                        StateRecord r = ledger.getRecord(id);

                        if (r == null || !r.getState().isConsensusFound()) {
                            unknownParts.put(id, r);
                        } else {
                            knownParts.put(id, r);
                        }
//                        }
                    }

                    // check revoking items
                    for (Approvable a : item.getRevokingItems()) {
                        StateRecord r = ledger.getRecord(a.getId());

                        if (r == null || !r.getState().isConsensusFound()) {
                            unknownParts.put(a.getId(), r);
                        } else {
                            knownParts.put(a.getId(), r);
                        }
                    }
                }

                boolean needToResync = false;
                // contract is complex and consist from parts
                if (unknownParts.size() + knownParts.size() > 0) {
                    needToResync = baseCheckPassed &&
                            unknownParts.size() > 0 &&
                            knownParts.size() >= config.getKnownSubContractsToResync();
                }

                if (needToResync)
                    return unknownParts;
            }
            return new HashMap<>();
        }

        //////////// polling section /////////////

        private final void broadcastMyState() {
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: broadcastMyState, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {
                Notification notification;

                ParcelNotification.ParcelNotificationType notificationType;
                if(item.shouldBeU()) {
                    notificationType = ParcelNotification.ParcelNotificationType.PAYMENT;
                } else {
                    notificationType = ParcelNotification.ParcelNotificationType.PAYLOAD;
                }
                notification = new ParcelNotification(myInfo, itemId, parcelId, getResult(), true, notificationType);
                network.broadcast(myInfo, notification);
            }
        }

        private final void pulseStartPolling() {
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: pulseStartPolling, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {

                    // at this point the item is with us, so we can start
                    synchronized (mutex) {
                        if (!processingState.isProcessedToConsensus()) {
                            if (poller == null) {
                                List<Integer> pollTimes = config.getPollTime();
                                poller = new RunnableWithDynamicPeriod(() -> sendStartPollingNotification(),
                                        pollTimes,
                                        executorService
                                );
                                poller.run();
                            }
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

                            processingState = ItemProcessingState.GOT_CONSENSUS;

                            stopPoller();
                            stopDownloader();
                            rollbackChanges(ItemState.UNDEFINED);
                            return;
                        }
                    }
                    // at this point we should requery the nodes that did not yet answered us
                    Notification notification;
                    ParcelNotification.ParcelNotificationType notificationType;
                    if(item.shouldBeU()) {
                        notificationType = ParcelNotification.ParcelNotificationType.PAYMENT;
                    } else {
                        notificationType = ParcelNotification.ParcelNotificationType.PAYLOAD;
                    }
                    notification = new ParcelNotification(myInfo, itemId, parcelId, getResult(), true, notificationType);
                    List<NodeInfo> nodes = network.allNodes();
                    for(NodeInfo node : nodes) {
                        if (!positiveNodes.contains(node) && !negativeNodes.contains(node))
                            network.deliver(node, notification);
                    }
                }
            }
        }

        private final void vote(NodeInfo node, ItemState state) {
            if(processingState.canContinue()) {
                report(getLabel(), () -> concatReportMessage("item processor for item: ",
                        itemId, " from parcel: ", parcelId,
                        " :: vote " + state + " from " + node + ", state ", processingState,
                        " :: itemState ", getState()),
                        DatagramAdapter.VerboseLevel.BASE);
                boolean positiveConsensus = false;
                boolean negativeConsensus = false;

                // check if vote already count
                if((state.isPositive() && positiveNodes.contains(node)) ||
                        (!state.isPositive() && negativeNodes.contains(node))) {
                    return;
                }
                synchronized (mutex) {

                    if(processingState.canRemoveSelf()) {
                        return;
                    }

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
                        if(processingState.isDone()) {
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
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: approveAndCommit, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {
                // todo: fix logic to surely copy approving item dependency. e.g. download original or at least dependencies
                // first we need to flag our state as approved
                setState(ItemState.APPROVED);
                executorService.submit(() -> downloadAndCommit(),
                        Node.this.toString() + toString() + " :: approveAndCommit -> downloadAndCommit");
            }
        }

        // commit subitems of given item to the ledger (recursively)
        private void downloadAndCommitSubItemsOf(Approvable commitingItem) {
            if(processingState.canContinue()) {
                for (Approvable revokingItem : commitingItem.getRevokingItems()) {
                    // The record may not exist due to ledger desync, so we create it if need
                    try {
                        itemLock.synchronize(revokingItem.getId(), lock -> {
                            StateRecord r = ledger.findOrCreate(revokingItem.getId());
                            r.setState(ItemState.REVOKED);
                            r.setExpiresAt(ZonedDateTime.now().plus(config.getRevokedItemExpiration()));
                            try {
                                r.save();
                                // if revoking item is smart contract node calls method onRevoked
                                if(revokingItem instanceof NSmartContract) {

                                    if(!searchNewItemWithParent(item,revokingItem.getId())) {
                                        ((NSmartContract) revokingItem).setNodeInfoProvider(nodeInfoProvider);
                                        NImmutableEnvironment ime = getEnvironment((NSmartContract)revokingItem);
                                        if (ime != null) {
                                            // and run onRevoked
                                            ((NSmartContract) revokingItem).onRevoked(ime);
                                            removeEnvironment(revokingItem.getId());
                                        }
                                    }
                                }

                                notifyStorageSubscribers(revokingItem, r.getState());

                                synchronized (cache) {
                                    ItemResult rr = new ItemResult(r);
                                    rr.extraDataBinder = null;
                                    if(cache.get(r.getId()) == null) {
                                        cache.put(revokingItem, rr);
                                    } else {
                                        cache.update(r.getId(), rr);
                                    }
                                }
                            } catch (Ledger.Failure failure) {
                                emergencyBreak();
                                return null;
                            }
                            return null;
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                for (Approvable newItem : commitingItem.getNewItems()) {
                    // The record may not exist due to ledger desync too, so we create it if need
                    try {
                        itemLock.synchronize(newItem.getId(), lock -> {
                            StateRecord r = ledger.findOrCreate(newItem.getId());
                            r.setState(ItemState.APPROVED);
                            r.setExpiresAt(newItem.getExpiresAt());
                            try {
                                r.save();
                                Binder newExtraResult = new Binder();
                                // if new item is smart contract node calls method onCreated or onUpdated
                                if(newItem instanceof NSmartContract) {

                                    if(negativeNodes.contains(myInfo)) {
                                        addItemToResync(itemId,record);
                                    } else {

                                        ((NSmartContract) newItem).setNodeInfoProvider(nodeInfoProvider);

                                        NImmutableEnvironment ime = getEnvironment((NSmartContract) newItem);
                                        ime.setNameCache(nameCache);
                                        NMutableEnvironment me = ime.getMutable();


                                        if (((NSmartContract) newItem).getRevision() == 1) {
                                            // and call onCreated
                                            newExtraResult.set("onCreatedResult", ((NSmartContract) newItem).onCreated(me));
                                        } else {
                                            newExtraResult.set("onUpdateResult", ((NSmartContract) newItem).onUpdated(me));
                                        }

                                        me.save();
                                    }
                                }

                                // update new item's smart contracts link to
                                notifyStorageSubscribers(newItem, r.getState());

                                synchronized (cache) {
                                    ItemResult rr = new ItemResult(r);
                                    rr.extraDataBinder = newExtraResult;
                                    if(cache.get(r.getId()) == null) {
                                        cache.put(newItem, rr);
                                    } else {
                                        cache.update(r.getId(), rr);
                                    }
                                }
                            } catch (Ledger.Failure failure) {
                                emergencyBreak();
                                return null;
                            }
                            return null;
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    lowPrioExecutorService.schedule(() -> checkSpecialItem(newItem),100,TimeUnit.MILLISECONDS);


                    downloadAndCommitSubItemsOf(newItem);
                }
            }
        }

        private boolean searchNewItemWithParent(Approvable item, HashId id) {
            if(item instanceof Contract && ((Contract) item).getParent() != null && ((Contract) item).getParent().equals(id)) {
                return true;
            }

            for(Approvable newItem : item.getNewItems()) {
                if(searchNewItemWithParent(newItem,id)) {
                    return true;
                }
            }
            return false;
        }

        private void downloadAndCommit() {
            if(processingState.canContinue()) {
                // it may happen that consensus is found earlier than item is download
                // we still need item to fix all its relations:
                try {

                    resyncingItems.clear();

                    if (item == null) {
                        // If positive consensus os found, we can spend more time for final download, and can try
                        // all the network as the source:
                        pollingExpiresAt = Instant.now().plus(config.getMaxDownloadOnApproveTime());
                        downloadedEvent.await(getMillisLeft());
                    }
                    // We use the caching capability of ledger so we do not get records from
                    // lockedToRevoke/lockedToCreate, as, due to conflicts, these could differ from what the item
                    // yields. We just clean them up afterwards:

                    // first, commit all subitems of our item
                    downloadAndCommitSubItemsOf(item);

                    synchronized (mutex) {
                        lockedToCreate.clear();
                        lockedToRevoke.clear();

                        try {
                            record.save();

                            if (item != null) {
                                synchronized (cache) {
                                    cache.update(itemId, getResult());
                                }
                            }
                        } catch (Ledger.Failure failure) {
                            emergencyBreak();
                            return;
                        }

                        if (record.getState() != ItemState.APPROVED) {
                            log.e("record is not approved " + record.getState());
                        }
                    }

                    try {
                        // if item is smart contract node calls onCreated or onUpdated
                        if(item instanceof NSmartContract) {
                            // slot need ledger, config and nodeInfo for processing
                            ((NSmartContract) item).setNodeInfoProvider(nodeInfoProvider);


                            if(negativeNodes.contains(myInfo)) {
                                addItemToResync(item.getId(),record);
                            } else {

                                NImmutableEnvironment ime = getEnvironment((NSmartContract) item);
                                ime.setNameCache(nameCache);
                                NMutableEnvironment me = ime.getMutable();

                                if (((NSmartContract) item).getRevision() == 1) {
                                    // and call onCreated
                                    extraResult.set("onCreatedResult", ((NSmartContract) item).onCreated(me));
                                } else {
                                    extraResult.set("onUpdateResult", ((NSmartContract) item).onUpdated(me));
                                }

                                me.save();

                                if (item != null) {
                                    synchronized (cache) {
                                        cache.update(itemId, getResult());
                                    }
                                }
                            }

                            ((NSmartContract)item).getExtraResultForApprove().forEach((k, v) -> extraResult.putAll(k, v));
                        }

                        // update item's smart contracts link to
                        notifyStorageSubscribers(item, getState());

                    } catch (Exception ex) {
                        System.err.println(myInfo);
                        ex.printStackTrace();
                    }

                    lowPrioExecutorService.schedule(() -> checkSpecialItem(item),100,TimeUnit.MILLISECONDS);

                    if(!resyncingItems.isEmpty()) {
                        processingState = ItemProcessingState.RESYNCING;
                        pulseResync(true);
                        return;
                    }

                } catch (TimeoutException | InterruptedException e) {
                    setState(ItemState.UNDEFINED);
                    try {
                        itemLock.synchronize(record.getId(), lock -> {
                            record.destroy();

                            if (item != null) {
                                synchronized (cache) {
                                    cache.update(itemId, null);
                                }
                            }
                            return null;
                        });
                    } catch (Exception ee) {
                        ee.printStackTrace();
                    }
                }
                close();
            }
        }

        /**
         * Method looking for item's subscriptions and if it exist fire events
         * @param updatingItem is item that processing
         * @param updatingState state that is consensus for processing item
         */
        private void notifyStorageSubscribers(Approvable updatingItem, ItemState updatingState) {
            try {
                HashId lookingId = null;

                // we are looking for updatingItem's parent subscriptions and want to update it
                if (updatingState == ItemState.APPROVED) {
                    if(updatingItem instanceof Contract && ((Contract) updatingItem).getParent() != null) {
                        lookingId = ((Contract) updatingItem).getParent();
                    }
                }

                // we are looking for own id and will update own subscriptions
                if (updatingState == ItemState.REVOKED) {
                    lookingId = updatingItem.getId();
                }

                if(lookingId != null) {
                    // find all enviroments that have subscription for item
                    Set<Long> enviromentIdsForContractId = ledger.getSubscriptionEnviromentIdsForContractId(lookingId);
                    for (Long environmentId : enviromentIdsForContractId) {
                        NImmutableEnvironment ime = getEnvironment(environmentId);
                        ime.setNameCache(nameCache);
                        NSmartContract contract = ime.getContract();
                        contract.setNodeInfoProvider(nodeInfoProvider);
                        NMutableEnvironment me = ime.getMutable();
                        NContractStorageSubscription subscription = null;
                        for(ContractStorageSubscription sub : ime.storageSubscriptions() ) {
                            if(sub.getContract().getId().equals(lookingId)) {
                                subscription = (NContractStorageSubscription) sub;
                                break;
                            }
                        }

                        NContractStorageSubscription finalSubscription = subscription;
                        if (subscription != null) {
                            if (updatingState == ItemState.APPROVED) {
                                contract.onContractStorageSubscriptionEvent(new ContractStorageSubscription.ApprovedEvent() {
                                    @Override
                                    public Contract getNewRevision() {
                                        return (Contract) updatingItem;
                                    }

                                    @Override
                                    public byte[] getPackedTransaction() {
                                        return ((Contract) updatingItem).getPackedTransaction();
                                    }

                                    @Override
                                    public MutableEnvironment getEnvironment() {
                                        return me;
                                    }

                                    @Override
                                    public ContractStorageSubscription getSubscription() {
                                        return finalSubscription;
                                    }
                                });
                                me.save();
                            }
                            if (updatingState == ItemState.REVOKED) {
                                contract.onContractStorageSubscriptionEvent(new ContractStorageSubscription.RevokedEvent() {
                                    @Override
                                    public ImmutableEnvironment getEnvironment() {
                                        return ime;
                                    }

                                    @Override
                                    public ContractStorageSubscription getSubscription() {
                                        return finalSubscription;
                                    }
                                });
                                //me.destroySubscription(subscription);
                                me.save();
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println(myInfo);
                ex.printStackTrace();
            }
        }

        private void rollbackChanges(ItemState newState) {
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: rollbackChanges, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);

            synchronized (ledgerRollbackLock) {
                ledger.transaction(() -> {
                    for (StateRecord r : lockedToRevoke) {
                        try {
                            itemLock.synchronize(r.getId(), lock -> {
                                r.unlock().save();
                                synchronized (cache) {
                                    ItemResult cr = cache.getResult(r.getId());
                                    ItemResult rr = new ItemResult(r);
                                    if(cr != null) {
                                        rr.extraDataBinder = cr.extraDataBinder;
                                    }
                                    cache.update(r.getId(), rr);
                                }
                                return null;
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    lockedToRevoke.clear();

                    // form created records, we touch only these that we have actually created
                    for (StateRecord r : lockedToCreate) {
                        try {
                            itemLock.synchronize(r.getId(), lock -> {
                                r.unlock().save();
                                synchronized (cache) {
                                    ItemResult cr = cache.getResult(r.getId());
                                    ItemResult rr = new ItemResult(r);
                                    if(cr != null) {
                                        rr.extraDataBinder = cr.extraDataBinder;
                                    }
                                    cache.update(r.getId(), rr);
                                }
                                return null;
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    // todo: concurrent modification can happen here!
                    lockedToCreate.clear();

                    setState(newState);
                    ZonedDateTime expiration = ZonedDateTime.now()
                            .plus(newState == ItemState.REVOKED ?
                                    config.getRevokedItemExpiration() : config.getDeclinedItemExpiration());
                    record.setExpiresAt(expiration);
                    try {
                        synchronized (mutex) {
                            if (newState != ItemState.UNDEFINED) {
                                record.save(); // TODO: current implementation will cause an inner dbPool.db() invocation

                                if (item != null) {
                                    synchronized (cache) {
                                        cache.update(itemId, getResult());
                                    }
                                }
                            } else {
//                                log.e("Can not rollback to ItemState.UNDEFINED, will destroy item");
                                record.destroy();
                            }
                        }
                    } catch (Ledger.Failure failure) {
                        failure.printStackTrace();
                        log.e(failure.getMessage());
                    }
                    return null;
                });
                close();
            }
        }

        private void stopPoller() {
            if (poller != null)
                poller.cancel(true);
        }

        private boolean isPollingExpired() {
            return pollingExpiresAt.isBefore(Instant.now());
        }

        //////////// sending new state section /////////////

        private final void pulseSendNewConsensus() {
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: pulseSendNewConsensus, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canContinue()) {

                processingState = ItemProcessingState.SENDING_CONSENSUS;

                synchronized (mutex) {
                    if(consensusReceivedChecker == null) {
                        List<Integer> periodsMillis = config.getConsensusReceivedCheckTime();
                        consensusReceivedChecker = new RunnableWithDynamicPeriod(() -> sendNewConsensusNotification(),
                                periodsMillis,
                                executorService
                        );
                        consensusReceivedChecker.run();
                    }
                }
            }
        }

        private final void sendNewConsensusNotification() {
            if(processingState.canContinue()) {

                if (processingState.isConsensusSentAndReceived())
                    return;

                synchronized (mutex) {
                    if (isConsensusReceivedExpired()) {
                        // cancel by timeout expired
                        processingState = ItemProcessingState.FINISHED;
                        stopConsensusReceivedChecker();
                        removeSelf();
                        return;
                    }
                }
                // at this point we should requery the nodes that did not yet answered us
                Notification notification;
                ParcelNotification.ParcelNotificationType notificationType;
                if(item.shouldBeU()) {
                    notificationType = ParcelNotification.ParcelNotificationType.PAYMENT;
                } else {
                    notificationType = ParcelNotification.ParcelNotificationType.PAYLOAD;
                }
                notification = new ParcelNotification(myInfo, itemId, parcelId, getResult(), true, notificationType);
                List<NodeInfo> nodes = network.allNodes();
                for(NodeInfo node : nodes) {
                    if (!positiveNodes.contains(node) && !negativeNodes.contains(node)) {
                        // if node do not know own vote we do not send notification, just looking for own state
                        if(!myInfo.equals(node)) {
                            network.deliver(node, notification);
                        } else {
                            if(processingState.isProcessedToConsensus()) {
                                vote(myInfo, record.getState());
                            }
                        }
                    }
                }
            }
        }

        private final Boolean checkIfAllReceivedConsensus() {
            if(processingState.canContinue()) {
                List<NodeInfo> nodes = network.allNodes();
                Boolean allReceived = nodes.size() <= positiveNodes.size() + negativeNodes.size();

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
                consensusReceivedChecker.cancel(true);
        }

        //////////// resync section /////////////

        public final void pulseResync() {
            pulseResync(false);
        }

        /**
         * Start resyncing.
         *
         * @param justResyncNoFurtherVoting - set true if you want to resync item itself.
         */
        public final void pulseResync(boolean justResyncNoFurtherVoting) {
            if(processingState.canContinue()) {

                if (!processingState.isProcessedToConsensus()) {
                    this.justResycnNoFurtherVoting = justResyncNoFurtherVoting;
                    if (justResyncNoFurtherVoting) {
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
                        List<Integer> periodsMillis = config.getResyncTime();
                        if(resyncer == null) {
                            resyncer = new RunnableWithDynamicPeriod(() -> sendResyncNotification(),
                                    periodsMillis,
                                    executorService
                            );
                            resyncer.run();
                        }
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
                                //Resync should only send "final" states no PENDING* is possible here
                                itemsToResync.put(hid, resyncingItems.get(hid).getItemState().isConsensusFound() ? resyncingItems.get(hid).getItemState() : ItemState.UNDEFINED);
                            }
                        }
                        if (itemsToResync.size() > 0) {
                            ItemResyncNotification notification = new ItemResyncNotification(myInfo, itemId, itemsToResync, new HashSet<>(),  true);

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
                    int numFinished = 0;
                    synchronized (resyncMutex) {
                        for (ResyncingItem rit : resyncingItems.values()) {
                            if (rit.isCommitFinished())
                                numFinished++;
                        }
                    }
                    if (resyncingItems.size() == numFinished && processingState.isGotResyncedState()) {
                        stopResync();

                        if(!processingState.isProcessedToConsensus() && !justResycnNoFurtherVoting) {
                            processingState = ItemProcessingState.CHECKING;
                        }

                        //DELETE ENVIRONMENTS FOR REVOKED ITEMS
                        resyncingItems.keySet().forEach(id -> {
                            if(resyncingItems.get(id).getResyncingState() == ResyncingItemProcessingState.COMMIT_SUCCESSFUL) {
                                if(resyncingItems.get(id).getItemState() == ItemState.REVOKED) {
                                    removeEnvironment(id);
                                }
                            }
                        });
                        //SAVE ENVIRONMENTS FOR APPROVED ITEMS
                        pulseSaveResyncedEnvironments();



                        // if we was resyncing itself (not own subitems) and state from network was undefined - rollback state
                        if (justResycnNoFurtherVoting) {
                            if (itemId.equals(ri.hashId)) {
                                if (ri.getResyncingState() == ResyncingItemProcessingState.COMMIT_FAILED) {
                                    rollbackChanges(stateWas);

                                    //resync failed we need to restart sanitated item
                                    executorService.schedule(() -> itemSanitationFailed(ri.record),0,TimeUnit.SECONDS);
                                    return;
                                }
                            }
                            if(!hasEnvironmentsToSave()) {
                                processingState = ItemProcessingState.FINISHED;
                                close();
                            }

                            //item successfully sanitated
                            executorService.schedule(() -> itemSanitationDone(ri.record),0,TimeUnit.SECONDS);
                        } else {
                            try {
                                checkSubItems();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            commitCheckedAndStartPolling();
                        }
                    }
                }
            }
        }

        private void pulseSaveResyncedEnvironments() {
            envSaver = executorService.schedule(() -> saveResyncedEnvironents(),0,TimeUnit.SECONDS);
        }

        private boolean hasEnvironmentsToSave() {
            return !envSources.isEmpty();
        }
        private void saveResyncedEnvironents() {
            if(!envSources.isEmpty()) {
                HashSet<HashId> itemsToReResync = new HashSet<>();
                envSources.keySet().forEach(id -> {
                    Random random = new Random(Instant.now().toEpochMilli() * myInfo.getNumber());
                    Object[] array = envSources.get(id).toArray();
                    NodeInfo from = (NodeInfo) array[(int) (array.length * random.nextFloat())];
                    try {
                        NImmutableEnvironment environment = network.getEnvironment(id, from, config.getMaxGetItemTime());
                        if (environment != null) {
                            Set<HashId> conflicts = ledger.saveEnvironment(environment);
                            if (conflicts.size() > 0) {
                                //TODO: remove in release
                                boolean resyncConflicts = true;
                                if (resyncConflicts) {
                                    itemsToReResync.addAll(conflicts);
                                } else {
                                    conflicts.forEach(conflict -> removeEnvironment(conflict));
                                    assert ledger.saveEnvironment(environment).isEmpty();
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        return;
                    }
                });

                if (itemsToReResync.size() > 0) {
                    itemsToReResync.addAll(resyncingItems.keySet());
                    resyncingItems.clear();
                    itemsToReResync.forEach(id -> {
                        //TODO: OPTIMIZE GETTING STATE RECORD
                        addItemToResync(id, ledger.getRecord(id));
                    });
                    pulseResync(true);
                } else {
                    if(justResycnNoFurtherVoting) {
                        processingState = ItemProcessingState.FINISHED;
                        close();
                    }
                }
            }
        }


        private boolean isResyncExpired() {
            return resyncExpiresAt.isBefore(Instant.now());
        }

        private void stopResync() {
            if (resyncer != null) {
                resyncer.cancel(true);
                resyncer = null;
            }
        }

        public void addItemToResync(HashId hid, StateRecord record) {
            if(processingState.canContinue()) {
                try {
                    synchronized (resyncMutex) {
                        if (!resyncingItems.containsKey(hid)) {
                            resyncingItems.put(hid, new ResyncingItem(hid, record));
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

        /**
         * Start checking if item was downloaded and wait for isCheckingForce flag.
         * If item hasn't downloaded just set isCheckingForce for true.
         * @param isCheckingForce
         */
        private void forceChecking(boolean isCheckingForce) {
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: forceChecking, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            this.isCheckingForce = isCheckingForce;
            if(processingState.canContinue()) {
                if (processingState == ItemProcessingState.DOWNLOADED) {
                    executorService.submit(() -> {
                        checkItem();
                    }, Node.this.toString() + toString() + " :: forceChecking -> checkItem");
                }
            }
        }

        private void close() {
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: close, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);

            if(processingState.canContinue())
                processingState = ItemProcessingState.DONE;

            stopPoller();

            // fire all event to release possible listeners
            downloadedEvent.fire();
            pollingReadyEvent.fire();
            doneEvent.fire();

            if(processingState.canContinue()) {
                // If we not just resynced itslef
                if (!justResycnNoFurtherVoting) {
                    checkIfAllReceivedConsensus();
                    if (processingState == ItemProcessingState.DONE) {
                        pulseSendNewConsensus();
                    } else {
                        removeSelf();
                    }
                } else {
                    //TODO: some weird tweaking of processing state. It needs to be simplified
                    processingState = ItemProcessingState.FINISHED;
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
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: emergencyBreak, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);

            boolean doRollback = !processingState.isDone();

            processingState = ItemProcessingState.EMERGENCY_BREAK;

            stopDownloader();
            stopEnvSaver();
            stopPoller();
            stopConsensusReceivedChecker();
            stopResync();

            for(ResyncingItem ri : resyncingItems.values()) {
                if(!ri.isCommitFinished()) {
                    ri.closeByTimeout();
                }
            }

            if(doRollback)
                rollbackChanges(stateWas);
            else
                close();

            processingState = ItemProcessingState.FINISHED;
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
            report(getLabel(), () -> concatReportMessage("item processor for item: ",
                    itemId, " from parcel: ", parcelId,
                    " :: removeSelf, state ", processingState, " itemState: ", getState()),
                    DatagramAdapter.VerboseLevel.BASE);
            if(processingState.canRemoveSelf()) {
                forceRemoveSelf();
            }
        }

        //used in test purposes
        private void forceRemoveSelf() {
            processors.remove(itemId);

            stopDownloader();
            stopPoller();
            stopConsensusReceivedChecker();
            stopResync();

            // fire all event to release possible listeners
            downloadedEvent.fire();
            pollingReadyEvent.fire();
            doneEvent.fire();
            removedEvent.fire();
        }


        public @NonNull ItemResult getResult() {
            ItemResult result = new ItemResult(record, item != null);
            result.extraDataBinder = extraResult;
            if (item != null)
                result.errors = new ArrayList<>(item.getErrors());
            return result;
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
                    pulseDownload();
                }
            }
        }

        public void addEnvToSources(HashId hid, NodeInfo from) {
            synchronized (envSources) {
                if(!envSources.containsKey(hid)) {
                    envSources.put(hid,new HashSet<>());
                }
                envSources.get(hid).add(from);
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

        public String toString() {
            return "ip -> parcel: " + parcelId + ", item: " + itemId + ", processing state: " + processingState;
        }

    }



    private void itemSanitationDone(StateRecord record) {

        synchronized (recordsToSanitate) {
            if(recordsToSanitate.containsKey(record.getId())) {


                recordsToSanitate.remove(record.getId());
                Set<HashId> idsToRemove = new HashSet<>();
                for (StateRecord r : recordsToSanitate.values()) {
                    if (r.getLockedByRecordId() == record.getRecordId()) {
                        try {
                            itemLock.synchronize(r.getId(), lock -> {
                                if (record.getState() == ItemState.APPROVED) {
                                    //ITEM ACCEPTED. LOCKED -> REVOKED, LOCKED_FOR_CREATION -> ACCEPTED
                                    if (r.getState() == ItemState.LOCKED) {
                                        r.setState(ItemState.REVOKED);
                                        r.save();
                                        synchronized (cache) {
                                            cache.update(r.getId(), new ItemResult(r));
                                        }
                                        idsToRemove.add(r.getId());
                                    } else if (r.getState() == ItemState.LOCKED_FOR_CREATION) {
                                        r.setState(ItemState.APPROVED);
                                        r.save();
                                        synchronized (cache) {
                                            cache.update(r.getId(), new ItemResult(r));
                                        }
                                        idsToRemove.add(r.getId());
                                    }
                                } else if (record.getState() == ItemState.DECLINED) {
                                    //ITEM REJECTED. LOCKED -> ACCEPTED, LOCKED_FOR_CREATION -> REMOVE
                                    if (r.getState() == ItemState.LOCKED) {
                                        r.setState(ItemState.APPROVED);
                                        r.save();
                                        synchronized (cache) {
                                            cache.update(r.getId(), new ItemResult(r));
                                        }
                                        idsToRemove.add(r.getId());
                                    } else if (r.getState() == ItemState.LOCKED_FOR_CREATION) {
                                        r.destroy();
                                        synchronized (cache) {
                                            cache.update(r.getId(), null);
                                        }
                                        idsToRemove.add(r.getId());
                                    }
                                } else if (record.getState() == ItemState.REVOKED) {
                                    //ITEM ACCEPTED AND THEN REVOKED. LOCKED -> REVOKED, LOCKED_FOR_CREATION -> ACCEPTED
                                    if (r.getState() == ItemState.LOCKED) {
                                        r.setState(ItemState.REVOKED);
                                        r.save();
                                        synchronized (cache) {
                                            cache.update(r.getId(), new ItemResult(r));
                                        }
                                        idsToRemove.add(r.getId());
                                    } else if (r.getState() == ItemState.LOCKED_FOR_CREATION) {
                                        r.setState(ItemState.APPROVED);
                                        r.save();
                                        synchronized (cache) {
                                            cache.update(r.getId(), new ItemResult(r));
                                        }
                                        idsToRemove.add(r.getId());
                                    }
                                } else if (record.getState() == ItemState.UNDEFINED) {
                                    //ITEM UNDEFINED. LOCKED -> ACCEPTED, LOCKED_FOR_CREATION -> REMOVE
                                    if (r.getState() == ItemState.LOCKED) {
                                        r.setState(ItemState.APPROVED);
                                        r.save();
                                        synchronized (cache) {
                                            cache.update(r.getId(), new ItemResult(r));
                                        }
                                        idsToRemove.add(r.getId());
                                    } else if (r.getState() == ItemState.LOCKED_FOR_CREATION) {
                                        r.destroy();
                                        synchronized (cache) {
                                            cache.update(r.getId(), null);
                                        }
                                        idsToRemove.add(r.getId());
                                    }
                                }
                                return null;
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                idsToRemove.stream().forEach(id -> recordsToSanitate.remove(id));

            }

        }
    }

    private void itemSanitationFailed(StateRecord record) {
        if(recordsToSanitate.containsKey(record.getId())) {

            //System.out.println("itemSanitationFailed at " + myInfo.getNumber() + " item " + record.getId());

            //item unknown to network we must restart voting
            Contract contract = (Contract) ledger.getItem(record);
            if (contract != null) {
                // todo: looks like we need re-check and re-register items after sanitation will be finished
                //Item found in disk cache. Restart voting.
                record.setState(ItemState.PENDING);
                try {
                    itemLock.synchronize(record.getId(), lock -> {
                        record.save();
                        synchronized (cache) {
                            cache.update(record.getId(), new ItemResult(record));
                        }
                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Object x = checkItemInternal(contract.getId(),null,contract,true,true,true);
                if (x instanceof ItemProcessor) {
                    ((ItemProcessor)x).doneEvent.addConsumer(i -> executorService.schedule( () -> itemSanitationDone(record),0,TimeUnit.SECONDS));
                } else {
                    throw new IllegalStateException("should never happen because ommitItemResult is true");
                }
//                record.setState(ItemState.UNDEFINED);
//                record.destroy();
//                itemSanitationDone(record);
            } else {
                //Item not found in cache so we can't restart voting
                //Nothing we can do here. Just throw item away and remove all locks
                record.setState(ItemState.UNDEFINED);
                try {
                    itemLock.synchronize(record.getId(), lock -> {
                        record.destroy();
                        return null;
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
                itemSanitationDone(record);
            }
        }
    }


    private void checkSpecialItem(Approvable item) {
        if(item instanceof Contract) {
            Contract contract = (Contract) item;
            if (contract.getIssuer().getKeys().stream().anyMatch(key -> config.getNetworkReconfigKeyAddress().isMatchingKey(key))) {
                if(contract.getParent() == null)
                    return;

                if(contract.getRevoking().size() == 0 || !contract.getRevoking().get(0).getId().equals(contract.getParent()))
                    return;

                Contract parent = contract.getRevoking().get(0);


                if(!checkContractCorrespondsToConfig(parent,network.allNodes())) {
                    return;
                }

                if(!checkIfContractContainsNetConfig(contract)) {
                    return;
                }

                List<NodeInfo> networkNodes = network.allNodes();

                List contractNodes = (List)DefaultBiMapper.getInstance().deserializeObject(contract.getStateData().get("net_config"));
                contractNodes.stream().forEach(nodeInfo -> {
                    if(!networkNodes.contains(nodeInfo)) {
                        addNode((NodeInfo) nodeInfo);
                    }

                    networkNodes.remove(nodeInfo);
                });

                networkNodes.stream().forEach( nodeInfo -> removeNode(nodeInfo));
            }
        }
    }

    private boolean checkIfContractContainsNetConfig(Contract contract) {
        if(!contract.getStateData().containsKey("net_config")) {
            return false;
        }

        //check if owner is list role
        if(!(contract.getOwner() instanceof ListRole)) {
            return false;
        }

        //TODO: network council criteria here
        // check if quorum matches network concil criteria
        ListRole owner = (ListRole) contract.getOwner();
        if(owner.getQuorum() == 0 || owner.getQuorum() < owner.getRoles().size()-1) {
            return false;
        }

        //check if owner keys set equals to nodes key set
        Object obj = DefaultBiMapper.getInstance().deserializeObject(contract.getStateData().get("net_config"));
        if(!(obj instanceof List)) {
            return false;
        }

        List contractNodes = (List) obj;
        Set<PublicKey> ownerKeys = contract.getOwner().getKeys();
        if(contractNodes.size() != ownerKeys.size() || !contractNodes.stream().allMatch(nodeInfo -> nodeInfo instanceof NodeInfo && ownerKeys.contains(((NodeInfo)nodeInfo).getPublicKey()))) {
            return false;
        }

        for(Permission permission : contract.getPermissions().values()) {
            if(permission instanceof ChangeOwnerPermission) {
                if(!(permission.getRole() instanceof RoleLink) || ((RoleLink)permission.getRole()).getRole() != contract.getOwner())
                    return false;
            }

            if(permission instanceof ModifyDataPermission) {
                if(((ModifyDataPermission)permission).getFields().containsKey("net_config")) {
                    if(!(permission.getRole() instanceof RoleLink) || ((RoleLink)permission.getRole()).getRole() != contract.getOwner())
                        return false;
                }
            }
        }

        return true;
    }

    private boolean checkContractCorrespondsToConfig(Contract contract, List<NodeInfo> netNodes) {
        //check if contract contains net config
        if(!checkIfContractContainsNetConfig(contract)) {
            return false;
        }

        //check if net config equals to current network configuration
        List<NodeInfo> contractNodes = DefaultBiMapper.getInstance().deserializeObject(contract.getStateData().get("net_config"));
        if(contractNodes.size() != netNodes.size() || !contractNodes.stream().allMatch(nodeInfo -> netNodes.contains(nodeInfo))) {
            return false;
        }

        return true;
    }


    public enum ParcelProcessingState {
        NOT_EXIST,
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
         * @return true if consensus found
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

        public boolean isProcessing() {
            return canContinue() && this != FINISHED && this != NOT_EXIST;
        }

        public Binder toBinder() {
            return Binder.fromKeysValues(
                    "state", name()
            );
        }

        static {
            DefaultBiMapper.registerAdapter(ParcelProcessingState.class, new BiAdapter() {
                @Override
                public Binder serialize(Object object, BiSerializer serializer) {
                    return ((ParcelProcessingState) object).toBinder();
                }

                @Override
                public ParcelProcessingState deserialize(Binder binder, BiDeserializer deserializer) {
                    return ParcelProcessingState.valueOf(binder.getStringOrThrow("state"));
                }

                @Override
                public String typeName() {
                    return "ParcelProcessingState";
                }
            });
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
         * @return true if consensus got and processing going father
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

            report(getLabel(), () -> concatReportMessage("resyncVote at " + myInfo.getNumber() + " from " +node.getNumber() + " item " + hashId + " state " + state),
                    DatagramAdapter.VerboseLevel.DETAILED);

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
                if (!isResyncPollingFinished())
                    return;
            }
            if (revokedConsenus) {
                executorService.submit(() -> resyncAndCommit(ItemState.REVOKED),
                        Node.this.toString() + " > item " + hashId + " :: resyncVote -> resyncAndCommit");
            } else if (declinedConsenus) {
                executorService.submit(() -> resyncAndCommit(ItemState.DECLINED),
                        Node.this.toString() + " > item " + hashId + " :: resyncVote -> resyncAndCommit");
            } else if (approvedConsenus) {
                executorService.submit(() -> resyncAndCommit(ItemState.APPROVED),
                        Node.this.toString() + " > item " + hashId + " :: resyncVote -> resyncAndCommit");
            } else if (undefinedConsenus) {
                executorService.submit(() -> resyncAndCommit(ItemState.UNDEFINED),

                        Node.this.toString() + " > item " + hashId + " :: resyncVote -> resyncAndCommit");
            } else
                throw new RuntimeException("error: resync consensus reported without consensus");
        }

        //there should be no consensus checks here as it was already done in resyncVote
        private final void resyncAndCommit(ItemState committingState) {

            resyncingState = ResyncingItemProcessingState.IS_COMMITTING;

            final Average startDateAvg = new Average();
            final Average expiresAtAvg = new Average();

            executorService.submit(()->{
                if(committingState.isConsensusFound()) {
                    Set<NodeInfo> rNodes = new HashSet<>();
                    Set<NodeInfo> nowNodes = resyncNodes.get(committingState);

                    // make local set of nodes to prevent changing set of nodes while commiting
                    synchronized (resyncNodes) {
                        for (NodeInfo ni : nowNodes) {
                            rNodes.add(ni);
                        }
                    }
                    for (NodeInfo ni : rNodes) {
                        if (ni != null) {
                            try {
                                ItemResult r = network.getItemState(ni, hashId);
                                if (r != null) {
                                    startDateAvg.update(r.createdAt.toEpochSecond());
                                    expiresAtAvg.update(r.expiresAt.toEpochSecond());

                                    ZonedDateTime createdAt = ZonedDateTime.ofInstant(
                                            Instant.ofEpochSecond((long) startDateAvg.average()), ZoneId.systemDefault());
                                    ZonedDateTime expiresAt = ZonedDateTime.ofInstant(
                                            Instant.ofEpochSecond((long) expiresAtAvg.average()), ZoneId.systemDefault());

                                    try {
                                        itemLock.synchronize(hashId, lock -> {
                                            StateRecord newRecord = ledger.findOrCreate(hashId);
                                            newRecord.setState(committingState)
                                                    .setCreatedAt(createdAt)
                                                    .setExpiresAt(expiresAt)
                                                    .save();
                                            synchronized (cache) {
                                                cache.update(newRecord.getId(), new ItemResult(newRecord));
                                            }
                                            return null;
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            } catch (IOException e) {
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    resyncingState = ResyncingItemProcessingState.COMMIT_SUCCESSFUL;
                } else {
                    resyncingState = ResyncingItemProcessingState.COMMIT_FAILED;
                }
                finishEvent.fire(this);
            }, Node.this.toString() + " > item " + hashId + " :: resyncAndCommit -> body");
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
