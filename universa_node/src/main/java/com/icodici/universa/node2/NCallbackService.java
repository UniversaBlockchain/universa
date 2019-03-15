package com.icodici.universa.node2;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.HashType;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.Ledger;
import com.icodici.universa.node2.network.DatagramAdapter;
import com.icodici.universa.node2.network.Network;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import net.sergeych.utils.Ut;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.*;

/**
 * Universa network callback service. The service creates a callback processor, sends a callback to distant callback URL,
 * notifies the follower contract of a state change, notifies the other network nodes, and synchronizes the callback states.
 *
 * Callback service runs on the Universa node. A node also calls methods for handling notifications when notifications
 * are received from the network and methods for synchronizing callbacks when a follower contract is refilled.
 */
public class NCallbackService implements CallbackService {

    private final Node node;
    private final Config config;
    private final NodeInfo myInfo;
    private final Ledger ledger;
    private final Network network;
    private final PrivateKey nodeKey;
    private final ScheduledExecutorService executorService;

    private ConcurrentHashMap<HashId, CallbackProcessor> callbackProcessors = new ConcurrentHashMap<>();
    private ConcurrentHashMap<HashId, CallbackNotification> deferredCallbackNotifications = new ConcurrentHashMap<>();
    private ConcurrentHashMap<HashId, CallbackRecord> callbacksToSynchronize = new ConcurrentHashMap<>();

    public enum FollowerCallbackState {
        UNDEFINED,
        STARTED,
        EXPIRED,    // not commited failed
        COMPLETED,
        FAILED
    }

    /**
     * Initialize callback service on node and start synchronization thread.
     *
     * @param node is Universa node
     * @param config is node configuration
     * @param myInfo is node information
     * @param ledger is DB ledger
     * @param network is Universa network
     * @param nodeKey is public key of node
     * @param executorService is executor service from node to run synchronization
     */
    public NCallbackService(Node node, Config config, NodeInfo myInfo, Ledger ledger, Network network, PrivateKey nodeKey,
                            ScheduledExecutorService executorService) {
        this.node = node;
        this.config = config;
        this.myInfo = myInfo;
        this.ledger = ledger;
        this.network = network;
        this.nodeKey = nodeKey;
        this.executorService = executorService;

        // start synchronization
        executorService.scheduleWithFixedDelay(() -> synchronizeFollowerCallbacks(), 60,
                config.getFollowerCallbackSynchronizationInterval().getSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Starts state synchronization for expired callbacks (past expiration time and state is STARTED or EXPIRED).
     * The follower contract that launched it is notified of the synchronized state of the callback.
     */
    public void synchronizeFollowerCallbacks() {
        int nodesCount = network.getNodesCount();
        if (nodesCount < 2)
            return;

        Collection<CallbackRecord> callbackRecords = ledger.getFollowerCallbacksToResync();
        if (callbackRecords.isEmpty())
            return;

        startSynchronizeFollowerCallbacks(callbackRecords, nodesCount);
    }

    /**
     * Starts state synchronization for expired callbacks (past expiration time and state is STARTED or EXPIRED) for environment.
     * The follower contract that launched it is notified of the synchronized state of the callback.
     *
     * @param environmentId is callback processor
     */
    public void synchronizeFollowerCallbacks(long environmentId) {
        int nodesCount = network.getNodesCount();
        if (nodesCount < 2)
            return;

        Collection<CallbackRecord> callbackRecords = ledger.getFollowerCallbacksToResyncByEnvId(environmentId);
        if (callbackRecords.isEmpty())
            return;

        startSynchronizeFollowerCallbacks(callbackRecords, nodesCount);
    }

    private synchronized void startSynchronizeFollowerCallbacks(Collection<CallbackRecord> callbackRecords, int nodesCount) {
        ZonedDateTime expiresAt = ZonedDateTime.now().plusSeconds(20);

        callbackRecords.forEach(r -> {
            if (!callbacksToSynchronize.containsKey(r.getId())) {
                // init record to synchronization
                r.setExpiresAt(expiresAt);
                r.setConsensusAndLimit(nodesCount);
                callbacksToSynchronize.put(r.getId(), r);

                // request callback state from all nodes
                network.broadcast(myInfo, new CallbackNotification(myInfo, r.getId(),
                        CallbackNotification.CallbackNotificationType.GET_STATE, null));
            }
        });

        executorService.schedule(() -> endSynchronizeFollowerCallbacks(), 20, TimeUnit.SECONDS);
    }

    private synchronized void endSynchronizeFollowerCallbacks() {
        for (CallbackRecord record: callbacksToSynchronize.values()) {
            if (record.endSynchronize(ledger, node))
                callbacksToSynchronize.remove(record.getId());
        }
    }

    /**
     * Runs callback processor for one callback. Adds callback record to ledger, runs callback processing thread and
     * checks and obtains deferred callback notifications.
     *
     * @param updatingItem is new revision of following contract
     * @param state is state of new revision of following contract
     * @param contract is follower contract
     * @param me is environment
     */
    public void startCallbackProcessor(Contract updatingItem, ItemState state, NSmartContract contract, MutableEnvironment me) {
        // initialize callback processor
        CallbackProcessor callback = new CallbackProcessor(updatingItem, state, contract, ((NMutableEnvironment) me).getId(), this);

        // add callback record to DB
        CallbackRecord.addCallbackRecordToLedger(callback.getId(), ((NMutableEnvironment) me).getId(), config, network.getNodesCount(), ledger);

        // run callback processor
        int startDelay = callback.getDelay();
        int repeatDelay = (int) config.getFollowerCallbackDelay().toMillis() * (network.getNodesCount() + 2);
        callback.setExecutor(executorService.scheduleWithFixedDelay(() -> callback.call(), startDelay, repeatDelay, TimeUnit.MILLISECONDS));

        synchronized (callbackProcessors) {
            callbackProcessors.put(callback.getId(), callback);

            node.report(DatagramAdapter.VerboseLevel.DETAILED, "notifyFollowerSubscribers: put callback ", callback.getId().toBase64String());

            CallbackNotification deferredNotification = deferredCallbackNotifications.get(callback.getId());
            if (deferredNotification != null) {
                // do deferred notification
                callback.obtainNotification(deferredNotification);

                deferredCallbackNotifications.remove(callback.getId());

                node.report(DatagramAdapter.VerboseLevel.DETAILED, "notifyFollowerSubscribers: remove deferred notification for callback ",
                            callback.getId().toBase64String());
            }
        }
    }

    /**
     * Request distant callback URL. Send new revision of following contract and signature (by node key).
     * Receive answer and return it if HTTP response code equals 200.
     *
     * @param callback is callback processor
     * @param callbackURL is callback URL
     * @param packedData is packed new revision of following contract or identifier of revoking following contract
     *
     * @return callback receipt (signed with callback key identifier of following contract) or null (if connection error)
     *
     */
    private byte[] requestFollowerCallback(CallbackProcessor callback, String callbackURL, byte[] packedData) throws IOException {
        synchronized (this) {
            String charset = "UTF-8";

            Binder call;

            if (callback.getState() == ItemState.APPROVED)
                call = Binder.fromKeysValues(
                        "event", "new",
                        "data", packedData,
                        "signature", nodeKey.sign(packedData, HashType.SHA512),
                        "key", nodeKey.getPublicKey().pack()
                );
            else if (callback.getState() == ItemState.REVOKED)
                call = Binder.fromKeysValues(
                        "event", "revoke",
                        "id", packedData,
                        "signature", nodeKey.sign(packedData, HashType.SHA512),
                        "key", nodeKey.getPublicKey().pack()
                );
            else
                return null;

            byte[] data = Boss.pack(call);

            final String CRLF = "\r\n"; // Line separator required by multipart/form-data.
            String boundary = "==boundary==" + Ut.randomString(48);

            URLConnection connection = new URL(callbackURL).openConnection();

            connection.setDoOutput(true);
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", "Universa Node");

            try (
                    OutputStream output = connection.getOutputStream();
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, charset), true);
            ) {
                // Send binary file.
                writer.append("--" + boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"callbackData\"; filename=\"callbackData.boss\"").append(CRLF);
                writer.append("Content-Type: application/octet-stream").append(CRLF);
                writer.append("Content-Transfer-Encoding: binary").append(CRLF);
                writer.append(CRLF).flush();
                output.write(data);
                output.flush(); // Important before continuing with writer!
                writer.append(CRLF).flush(); // CRLF is important! It indicates end of boundary.

                // End of multipart/form-data.
                writer.append("--" + boundary + "--").append(CRLF).flush();
            }

            callback.setItemSended();

            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            byte[] answer = null;

            if (httpConnection.getResponseCode() == 200)
                answer = Do.read(httpConnection.getInputStream());

            httpConnection.disconnect();

            // get receipt from answer
            if (answer == null)
                return null;

            Binder res = Boss.unpack(answer);
            if (!res.containsKey("receipt"))
                return null;

            return res.getBinary("receipt");
        }
    }

    /**
     * Obtain got callback notification. Depending on the type of notification, it sends the state of the requested
     * callback, accepts and process the requested state, or sends the notification to the method of obtaining
     * notifications of the corresponding callback. If a notification has been received for a specific callback,
     * but it has not yet been initialized, then the notification is saved to deferred notifications.
     *
     * @param notification callback notification
     *
     */
    public void obtainCallbackNotification(CallbackNotification notification) {
        CallbackProcessor callback;

        node.report(DatagramAdapter.VerboseLevel.DETAILED, "obtainCallbackNotification: callback ",
                    notification.getId().toBase64String(), " type ", notification.getType().name());

        if (notification.getType() == CallbackNotification.CallbackNotificationType.GET_STATE) {
            network.deliver(notification.getFrom(), new CallbackNotification(myInfo, notification.getId(),
                    CallbackNotification.CallbackNotificationType.RETURN_STATE, null,
                    ledger.getFollowerCallbackStateById(notification.getId())));
        } else if (notification.getType() == CallbackNotification.CallbackNotificationType.RETURN_STATE) {
            synchronized (callbackProcessors) {
                CallbackRecord record = callbacksToSynchronize.get(notification.getId());

                if ((record != null) && record.synchronizeState(notification.getState(), ledger, node)) {
                    callbacksToSynchronize.remove(notification.getId());

                    node.report(DatagramAdapter.VerboseLevel.DETAILED, "obtainCallbackNotification: callback ",
                            notification.getId().toBase64String(), " synchronized with state ", notification.getState().name());
                }
            }
        } else {
            synchronized (callbackProcessors) {
                callback = callbackProcessors.get(notification.getId());
                if (callback == null) {
                    node.report(DatagramAdapter.VerboseLevel.BASE, "obtainCallbackNotification not found callback ",
                                notification.getId().toBase64String());

                    deferredCallbackNotifications.put(notification.getId(), notification);
                    return;
                }
            }

            callback.obtainNotification(notification);
        }
    }

    /**
     * Check if there are deferred follower callback notifications.
     *
     * @return true if there are deferred follower callback notifications
     *
     */
    public boolean hasDeferredNotifications() {
        return deferredCallbackNotifications.size() > 0;
    }


    /**
     * CallbackProcessor performs callback processing sent by the network to a specific URL.
     * When the processor is initialized at the node, callback delay is calculated (for successive attempts to run
     * it on all the nodes of the network). Also callback expiration time is calculated and save callback information.
     *
     * CallbackProcessor contains methods for checking callback signature, checking callback for completable, obtaining
     * notifications, calling callback, completion callback, fail callback, stopping callback executor.
     */
    private class CallbackProcessor {
        private HashId id;
        private HashId itemId;
        private byte[] packedItem;
        private ItemState state;
        private long environmentId;
        private ZonedDateTime expiresAt;
        private int delay = 1;
        private String callbackURL;
        private PublicKey callbackKey;
        private ScheduledFuture<?> executor;
        private boolean isItemSended;
        private ConcurrentSkipListSet<Integer> nodesSendCallback = new ConcurrentSkipListSet<>();
        private final NCallbackService callbackService;

        public CallbackProcessor(Contract item, ItemState state, NSmartContract follower, long environmentId, NCallbackService callbackService) {
            // save item, environment and subscription
            itemId = item.getId();
            item.setTransactionPack(null);      // send only updated item without TransactionPack
            packedItem = item.getPackedTransaction();
            this.state = state;
            this.environmentId = environmentId;
            this.callbackService = callbackService;
            List<NodeInfo> allNodes = network.allNodes();
            isItemSended = false;

            // save callback information
            callbackURL = follower.getTrackingOrigins().get(item.getOrigin());
            callbackKey = follower.getCallbackKeys().get(callbackURL);

            // calculate callback hash
            byte[] digest = itemId.getDigest();
            byte[] URL = callbackURL.getBytes(StandardCharsets.UTF_8);
            byte[] concat = new byte[digest.length + URL.length + 1];
            concat[0] = (byte) state.ordinal();
            System.arraycopy(digest, 0, concat, 1, digest.length);
            System.arraycopy(URL, 0, concat, digest.length + 1, URL.length);
            id = HashId.of(concat);

            // calculate expiration time
            expiresAt = ZonedDateTime.now().plus(config.getFollowerCallbackExpiration());

            // calculate node callback delay
            TreeSet<byte[]> nodeDigests = new TreeSet<>((byte[] left, byte[] right) -> {
                for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
                    int a = (left[i] & 0xff);
                    int b = (right[j] & 0xff);
                    if (a != b)
                        return a - b;
                }
                return left.length - right.length;
            });

            byte[] callbackDigest = id.getDigest();
            byte[] myDigest = null;
            for (NodeInfo n: allNodes) {
                ByteBuffer bb = ByteBuffer.allocate(4).putInt(n.getNumber());
                byte[] nodeAndItemId = new byte[digest.length + 4];
                System.arraycopy(digest, 0, nodeAndItemId, 0, digest.length);
                System.arraycopy(bb.array(), 0, nodeAndItemId, digest.length, 4);

                node.report(DatagramAdapter.VerboseLevel.DETAILED, "CallbackProcessor calculate node ", n.getNumber(),
                            " hash: ", HashId.of(nodeAndItemId).toBase64String());

                byte[] nodeDigest = HashId.of(nodeAndItemId).getDigest();
                nodeDigests.add(nodeDigest);

                if (n.getNumber() == myInfo.getNumber())
                    myDigest = nodeDigest;
            }
            nodeDigests.add(callbackDigest);

            int skipNodes;
            if (nodeDigests.tailSet(myDigest).contains(callbackDigest))
                skipNodes = nodeDigests.subSet(myDigest, callbackDigest).size() - 1;
            else
                skipNodes = nodeDigests.size() - nodeDigests.subSet(callbackDigest, myDigest).size() - 1;

            delay += skipNodes * config.getFollowerCallbackDelay().toMillis();
            if (allNodes.size() % 2 == 1)
                delay += config.getFollowerCallbackDelay().toMillis() / 3;

            node.report(DatagramAdapter.VerboseLevel.DETAILED, "CallbackProcessor calculate callback hash ", id.toBase64String());
            node.report(DatagramAdapter.VerboseLevel.DETAILED, "CallbackProcessor calculate skipped nodes ", skipNodes);
            node.report(DatagramAdapter.VerboseLevel.DETAILED, "CallbackProcessor calculate delay before callback ", delay);
            node.report(DatagramAdapter.VerboseLevel.BASE, "CallbackProcessor started callback ", id.toBase64String());
        }

        public HashId getId() { return id; }

        public int getDelay() { return delay; }

        public ItemState getState() { return state; }

        public void setExecutor(ScheduledFuture<?> executor) { this.executor = executor; }

        public void setItemSended() { isItemSended = true; }

        private void addNodeToSended(int addedNodeNumber) { nodesSendCallback.add(addedNodeNumber); }

        private void checkForComplete() {
            // if some nodes (rate defined in config) also sended callback and received packed item (without answer)
            // callback is deemed complete
            if (nodesSendCallback.size() >= (int) Math.floor(network.allNodes().size() * config.getRateNodesSendFollowerCallbackToComplete()))
                complete();
        }

        private boolean checkCallbackSignature(byte[] signature) {
            try {
                return callbackKey.verify(itemId.getDigest(), signature, HashType.SHA512);
            } catch (EncryptionError e) {
                return false;
            }
        }

        public void obtainNotification(CallbackNotification notification) {
            node.report(DatagramAdapter.VerboseLevel.DETAILED, "Notify callback ", notification.getId().toBase64String(),
                        " type ", notification.getType().name(), " from node ", notification.getFrom().getName());

            if (notification.getType() == CallbackNotification.CallbackNotificationType.COMPLETED) {
                if (checkCallbackSignature(notification.getSignature()))
                    complete();
            } else if (notification.getType() == CallbackNotification.CallbackNotificationType.NOT_RESPONDING) {
                addNodeToSended(notification.getFrom().getNumber());
                checkForComplete();
            }
        }

        private void complete() {
            synchronized (callbackService) {
                // full environment
                Binder fullEnvironment = node.getFullEnvironment(environmentId);
                NSmartContract follower = (NSmartContract) fullEnvironment.get("follower");
                NMutableEnvironment environment = (NMutableEnvironment) fullEnvironment.get("environment");

                callbackProcessors.remove(id);

                node.report(DatagramAdapter.VerboseLevel.DETAILED, "CallbackProcessor.complete: Removed callback ", id.toBase64String());

                follower.onContractSubscriptionEvent(new ContractSubscription.CompletedEvent() {
                    @Override
                    public MutableEnvironment getEnvironment() {
                        return environment;
                    }
                });
                environment.save();
            }

            // save new callback state in DB record
            ledger.updateFollowerCallbackState(id, FollowerCallbackState.COMPLETED);

            node.report(DatagramAdapter.VerboseLevel.BASE, "Completed callback ", id.toBase64String());

            stop();
        }

        private void fail() {
            synchronized (callbackService) {
                // full environment
                Binder fullEnvironment = node.getFullEnvironment(environmentId);
                NSmartContract follower = (NSmartContract) fullEnvironment.get("follower");
                NMutableEnvironment environment = (NMutableEnvironment) fullEnvironment.get("environment");

                callbackProcessors.remove(id);

                node.report(DatagramAdapter.VerboseLevel.DETAILED, "CallbackProcessor.fail: Removed callback ", id.toBase64String());

                follower.onContractSubscriptionEvent(new ContractSubscription.FailedEvent() {
                    @Override
                    public MutableEnvironment getEnvironment() {
                        return environment;
                    }
                });
                environment.save();
            }

            // save new callback state in DB record
            ledger.updateFollowerCallbackState(id, FollowerCallbackState.EXPIRED);

            node.report(DatagramAdapter.VerboseLevel.BASE, "Failed callback ", id.toBase64String());

            stop();
        }

        private void stop() {
            if (executor != null)
                executor.cancel(true);
        }

        public void call() {
            if (ZonedDateTime.now().isAfter(expiresAt))
                fail();     // callback failed (expired)
            else {
                if (isItemSended) {       // callback has already been called and received packed item
                    // send notification to other nodes
                    network.broadcast(myInfo, new CallbackNotification(myInfo, id,
                            CallbackNotification.CallbackNotificationType.NOT_RESPONDING, null));

                    addNodeToSended(myInfo.getNumber());
                    checkForComplete();
                } else {     // callback not previously called
                    // request HTTP follower callback
                    byte[] signature = null;
                    try {
                        if (state == ItemState.APPROVED)
                            signature = requestFollowerCallback(this, callbackURL, packedItem);
                        else if (state == ItemState.REVOKED)
                            signature = requestFollowerCallback(this, callbackURL, itemId.getDigest());
                    } catch (IOException e) {
                        e.printStackTrace();
                        System.err.println("error request HTTP follower callback");
                    }

                    if ((signature != null) && checkCallbackSignature(signature)) {
                        network.broadcast(myInfo, new CallbackNotification(myInfo, id,
                                CallbackNotification.CallbackNotificationType.COMPLETED, signature));
                        complete();
                    }
                }
            }
        }
    }
}
