/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.services.*;
import com.icodici.universa.node.ItemResult;
import com.icodici.universa.node.ItemState;
import com.icodici.universa.node.StateRecord;
import com.icodici.universa.node.network.BasicHTTPService;
import com.icodici.universa.node2.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.BufferedLogger;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHTTPServer extends BasicHttpServer {


    private static final String API_VERSION = "3.1.0";

    private final BufferedLogger log;
    private ItemCache cache;
    private ParcelCache parcelCache;
    private EnvCache envCache;
    private NetConfig netConfig;
    private Config config;

    private boolean localCors = false;

    private ExecutorService es = Executors.newFixedThreadPool(40);

    public ClientHTTPServer(PrivateKey privateKey, int port, BufferedLogger logger) throws IOException {
        super(privateKey, port, 32, logger);
        log = logger;

        addSecureEndpoint("status", (params, session) -> Binder.of(
                "status", "initializing",
                "log", log.getLast(10)
        ));

        on("/contracts", (request, response) -> {
            String encodedString = request.getPath().substring(11);

            // this is a bug - path has '+' decoded as ' '
            encodedString = encodedString.replace(' ', '+');

            byte[] data = null;
            if (encodedString.equals("cache_test")) {
                data = "the cache test data".getBytes();
            } else {
                HashId id = HashId.withDigest(encodedString);
                if (cache != null) {
                    Contract c = (Contract) cache.get(id);
                    if (c != null) {
                        data = c.getPackedTransaction();
                    }
                }
                if (data == null) {
                    data = node.getLedger().getContractInStorage(id);
                }
                if ((data == null) && node.getConfig().isPermanetMode())
                    data = node.getLedger().getKeepingItem(id);
            }

            if (data != null) {
                // contracts are immutable: cache forever
                Binder hh = response.getHeaders();
                hh.put("Expires", "Thu, 31 Dec 2037 23:55:55 GMT");
                hh.put("Cache-Control", "max-age=315360000");
                response.setBody(data);
            } else
                response.setResponseCode(404);
        });

        on("/parcels", (request, response) -> {
            String encodedString = request.getPath().substring(9);

            // this is a bug - path has '+' decoded as ' '
            encodedString = encodedString.replace(' ', '+');

            byte[] data = null;
            if (encodedString.equals("cache_test")) {
                data = "the cache test data".getBytes();
            } else {
                HashId id = HashId.withDigest(encodedString);
                if (parcelCache != null) {
                    Parcel p = (Parcel) parcelCache.get(id);
                    if (p != null) {
                        data = p.pack();
                    }
                }
            }
            if (data != null) {
                // contracts are immutable: cache forever
                Binder hh = response.getHeaders();
                hh.put("Expires", "Thu, 31 Dec 2037 23:55:55 GMT");
                hh.put("Cache-Control", "max-age=315360000");
                response.setBody(data);
            } else
                response.setResponseCode(404);
        });

        on("/environments", (request, response) -> {
            String encodedString = request.getPath().substring(14);
            // this is a bug - path has '+' decoded as ' '
            encodedString = encodedString.replace(' ', '+');

            System.out.println("/environments " + encodedString);

            HashId id = HashId.withDigest(encodedString);



            byte[] data = null;
            //TODO: implement envCache
            /*if (envCache != null) {
                NImmutableEnvironment nie =  envCache.get(id);
                if (nie != null) {
                    data = Boss.pack(nie);
                }
            }*/


            NImmutableEnvironment nie =  node.getLedger().getEnvironment(id);


            if (nie != null) {
                data = Boss.pack(nie);
            }

            if (data != null) {
                // contracts are immutable: cache forever
                Binder hh = response.getHeaders();
                hh.put("Expires", "Thu, 31 Dec 2037 23:55:55 GMT");
                hh.put("Cache-Control", "max-age=315360000");
                response.setBody(data);
            } else
                response.setResponseCode(404);
        });

        addEndpoint("/network", (Binder params, Result result) -> {
            if (networkData == null) {
                List<Binder> nodes = new ArrayList<Binder>();
                result.putAll(
                        "version", Main.NODE_VERSION,
                        "number", node.getNumber(),
                        "nodes", nodes
                );
                if (netConfig != null) {
                    netConfig.forEachNode(node -> {
                        nodes.add(Binder.of(
                                "url", node.publicUrlString(),
                                "key", node.getPublicKey().pack(),
                                "number", node.getNumber()
                        ));
                    });
                }
            }

        });

        addSecureEndpoint("getStats", this::getStats);
        addSecureEndpoint("getState", this::getState);
        addSecureEndpoint("getParcelProcessingState", this::getParcelProcessingState);
        addSecureEndpoint("approve", this::approve);
        addSecureEndpoint("resyncItem", this::resyncItem);
        addSecureEndpoint("setVerbose", this::setVerbose);
        addSecureEndpoint("approveParcel", this::approveParcel);
        addSecureEndpoint("startApproval", this::startApproval);
        addSecureEndpoint("throw_error", this::throw_error);
        addSecureEndpoint("storageGetRate", this::storageGetRate);
        addSecureEndpoint("querySlotInfo", this::querySlotInfo);
        addSecureEndpoint("queryContract", this::queryContract);
        addSecureEndpoint("unsRate", this::unsRate);
        addSecureEndpoint("queryNameRecord", this::queryNameRecord);
        addSecureEndpoint("queryNameContract", this::queryNameContract);
        addSecureEndpoint("getBody", this::getBody);
        addSecureEndpoint("getContract", this::getContract);

        addSecureEndpoint("followerGetRate", this::followerGetRate);
        addSecureEndpoint("queryFollowerInfo", this::queryFollowerInfo);

    }

    @Override
    public void shutdown() {
        es.shutdown();
        node.shutdown();
        super.shutdown();
    }

    private Binder throw_error(Binder binder, Session session) throws IOException {
        throw new IOException("just a test");
    }

    private Binder unsRate(Binder params, Session session) throws IOException {

        checkNode(session, true);

        BigDecimal rate = config.rate.get(NSmartContract.SmartContractType.UNS1.name());
        String str = rate.toString();
        Binder b = new Binder();
        b.put("U", str);

        return b;
    }

    private Binder queryNameRecord(Binder params, Session session) throws IOException {

        checkNode(session, true);

        Binder b = new Binder();
        NNameRecord loadedNameRecord;
        String address = params.getString("address",null);
        byte[] origin = params.getBinary("origin");

        if (((address == null) && (origin == null)) || ((address != null) && (origin != null)))
            throw new IOException("invalid arguments");

        if (address != null)
            loadedNameRecord = node.getLedger().getNameByAddress(address);
        else
            loadedNameRecord = node.getLedger().getNameByOrigin(origin);

        if (loadedNameRecord != null) {
            b.put("name", loadedNameRecord.getName());
            b.put("description", loadedNameRecord.getDescription());
            b.put("url", loadedNameRecord.getUrl());
        }
        return b;
    }

    private Binder queryNameContract(Binder params, Session session) throws IOException {

        checkNode(session, true);

        Binder b = new Binder();
        String nameContract = params.getStringOrThrow("name");
        NNameRecord nr = node.getLedger().getNameRecord(nameContract);
        if (nr != null) {
            NImmutableEnvironment env = node.getLedger().getEnvironment(nr.getEnvironmentId());
            if (env != null) {
                byte[] packedContract = env.getContract().getPackedTransaction();
                b.put("packedContract", packedContract);
            }
        }
        return b;
    }

    private Binder getBody(Binder params, Session session) throws IOException {

        checkNode(session, true);

        Binder res = new Binder();

        if (!node.getConfig().isPermanetMode())
            return res;

        HashId itemId = (HashId) params.get("itemId");

        byte[] body = node.getLedger().getKeepingItem(itemId);
        if (body != null) {
            res.put("packedContract", body);
            return res;
        }

        node.resync(itemId);
        ItemResult itemResult = node.checkItem(itemId);

        if (itemResult.state == ItemState.UNDEFINED)
            return res;

        Approvable item = node.getKeepingItemFromNetwork(itemId);
        if (item == null)
            return res;

        if ((item instanceof Contract) &&
            (item.getId().equals(itemId)) &&
            (HashId.of(((Contract) item).getLastSealedBinary()).equals(itemId))) {
            StateRecord record = node.getLedger().getRecord(itemId);
            node.getLedger().putKeepingItem(record, item);

            body = ((Contract) item).getPackedTransaction();
            res.put("packedContract", body);
        }

        return res;
    }

    private Binder getContract(Binder params, Session session) throws IOException {

        checkNode(session, true);

        Binder res = new Binder();

        if (!node.getConfig().isPermanetMode())
            return res;

        if(params.containsKey("origin") && params.containsKey("parent") || !params.containsKey("origin") && !params.containsKey("parent")) {
            throw new IllegalArgumentException("Invalid params. Should contain ether origin or parent");
        }

        HashId id = null;
        String getBy = null;
        if(params.containsKey("origin")) {
            id = (HashId) params.get("origin");
            getBy = "state.origin";
        }

        if(params.containsKey("parent")) {
            id = (HashId) params.get("parent");
            getBy = "state.parent";
        }

        int limit = params.getInt("limit", node.getConfig().getQueryContractsLimit());

        if (limit > node.getConfig().getQueryContractsLimit())
            limit = node.getConfig().getQueryContractsLimit();
        if (limit < 1)
            limit = 1;

        int offset = params.getInt("offset", 0);

        String sortBy = params.getString("sortBy", "state.createdAt");
        String sortOrder = params.getString("sortOrder", "ASC");


        Object keeping = node.getLedger().getKeepingBy(getBy,id, limit, offset,sortBy,sortOrder);
        if (keeping == null)
            return res;

        if (keeping instanceof byte[])
            res.put("packedContract", keeping);
        else if (keeping instanceof List)
            res.put("contractIds", keeping);

        if(getBy.equals("state.origin")) {
            res.put("origin",id);
        } else if(getBy.equals("state.parent")) {
            res.put("parent",id);
        }

        res.put("limit",limit);
        res.put("offset",offset);
        res.put("sortBy",sortBy);
        res.put("sortOrder",sortOrder);

        return res;
    }

    private ItemResult itemResultOfError(Errors error, String object, String message) {
        Binder binder = new Binder();
        binder.put("state",ItemState.UNDEFINED.name());
        binder.put("haveCopy",false);
        binder.put("createdAt", new Date());
        binder.put("expiresAt", new Date());
        ArrayList<ErrorRecord> errorRecords = new ArrayList<>();
        errorRecords.add(new ErrorRecord(error,object,message));
        binder.put("errors",errorRecords);
        return new ItemResult(binder);
    }

    private Binder approve(Binder params, Session session) throws IOException, Quantiser.QuantiserException {

        Contract contract;
        checkNode(session);

        if (config.limitFreeRegistrations() &&
            (!(
                config.getNetworkAdminKeyAddress().isMatchingKey(session.getPublicKey()) ||
                config.getKeysWhiteList().contains(session.getPublicKey()) ||
                config.getAddressesWhiteList().stream().anyMatch(addr -> addr.isMatchingKey(session.getPublicKey()))
            ))) {

            try {
                contract = Contract.fromPackedTransaction(params.getBinaryOrThrow("packedItem"));
            } catch (Exception e) {
                System.out.println("approve ERROR: " + e.getMessage());

                return Binder.of(
                        "itemResult", itemResultOfError(Errors.COMMAND_FAILED, "approve", e.getMessage()));
            }

            if ((contract == null) || !contract.isUnlimitKeyContract(config)) {
                if (!contract.isOk()) {
                    contract.traceErrors();

                    return Binder.of(
                            "itemResult", itemResultOfError(Errors.FAILED_CHECK, "approve",
                                    contract.getErrors().get(contract.getErrors().size() - 1).getMessage()));
                } else {
                    System.out.println("approve ERROR: command needs client key from whitelist");

                    return Binder.of(
                            "itemResult", itemResultOfError(Errors.BAD_CLIENT_KEY, "approve", "command needs client key from whitelist"));
                }
            }
        }

        try {
            return Binder.of(
                    "itemResult",
                    node.registerItem(Contract.fromPackedTransaction(params.getBinaryOrThrow("packedItem")))
            );
        } catch (Exception e) {
            System.out.println("approve ERROR: " + e.getMessage());

            return Binder.of(
                    "itemResult", itemResultOfError(Errors.COMMAND_FAILED,"approve", e.getMessage()));
        }
    }

    private Binder approveParcel(Binder params, Session session) throws IOException, Quantiser.QuantiserException {
        checkNode(session);
        try {
    //        System.out.println("Request to approve parcel, package size: " + params.getBinaryOrThrow("packedItem").length);
            return Binder.of(
                    "result",
                    node.registerParcel(Parcel.unpack(params.getBinaryOrThrow("packedItem")))
            );
        } catch (Exception e) {
            System.out.println("approveParcel ERROR: " + e.getMessage());
            return Binder.of(
                    "itemResult", itemResultOfError(Errors.COMMAND_FAILED,"approveParcel", e.getMessage()));
        }
    }

    static AtomicInteger asyncStarts = new AtomicInteger();

    private Binder startApproval(final Binder params, Session session) throws IOException, Quantiser.QuantiserException {
        if (config == null || config.limitFreeRegistrations())
            if(config == null || (
            !config.getKeysWhiteList().contains(session.getPublicKey()) &&
            !config.getAddressesWhiteList().stream().anyMatch(addr -> addr.isMatchingKey(session.getPublicKey())))) {
                System.out.println("startApproval ERROR: session key shoild be in the white list");

                return Binder.of(
                        "itemResult", itemResultOfError(Errors.BAD_CLIENT_KEY,"startApproval", "command needs client key from whitelist"));
            }

        int n = asyncStarts.incrementAndGet();
        AtomicInteger k = new AtomicInteger();
        params.getListOrThrow("packedItems").forEach((item) ->
                es.execute(() -> {
                    try {
                        checkNode(session);
                        System.out.println("Request to start registration #"+n+":"+k.incrementAndGet());
                        node.registerItem(Contract.fromPackedTransaction(((Bytes)item).toArray()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })
        );

        //TODO: return ItemResult
        return new Binder();
    }

    private Binder getState(Binder params, Session session) throws CommandFailedException {

        checkNode(session, true);

        try {
            return Binder.of("itemResult",
                    node.checkItem((HashId) params.get("itemId")));
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("getState ERROR: " + e.getMessage());
            return Binder.of(
                    "itemResult", itemResultOfError(Errors.COMMAND_FAILED,"approveParcel", e.getMessage()));
        }
    }

    private Binder resyncItem(Binder params, Session session) throws CommandFailedException {

        checkNode(session, true);

        KeyAddress tmpAddress = null;
        try {
            tmpAddress = new KeyAddress("JKEgDs9CoCCymD9TgmjG8UBLxuJwT5GZ3PaZyG6o2DQVGRQPjXHCG8JouC8eZw5Nd1w9krCS");
        } catch (KeyAddress.IllegalAddressException e) {
            e.printStackTrace();
        }

        if (config.limitFreeRegistrations())

            if(!(
                    tmpAddress.isMatchingKey(session.getPublicKey()) ||
                    config.getNetworkAdminKeyAddress().isMatchingKey(session.getPublicKey()) ||
                    config.getKeysWhiteList().contains(session.getPublicKey()) ||
                    config.getAddressesWhiteList().stream().anyMatch(addr -> addr.isMatchingKey(session.getPublicKey()))
            )) {
                System.out.println("approve ERROR: command needs client key from whitelist");

                return Binder.of(
                        "itemResult", itemResultOfError(Errors.BAD_CLIENT_KEY,"resyncItem", "command needs client key from whitelist"));
            }

        try {
            Binder result = Binder.of("itemResult",
                    node.checkItem((HashId) params.get("itemId")));
            node.resync((HashId) params.get("itemId"));
            return result;
        } catch (Exception e) {
            System.out.println("getState ERROR: " + e.getMessage());
            return Binder.of(
                    "itemResult", itemResultOfError(Errors.COMMAND_FAILED,"resyncItem", e.getMessage()));
        }
    }

    private Binder setVerbose(Binder params, Session session) throws CommandFailedException {

        checkNode(session, true);

        KeyAddress tmpAddress = null;
        try {
            tmpAddress = new KeyAddress("JKEgDs9CoCCymD9TgmjG8UBLxuJwT5GZ3PaZyG6o2DQVGRQPjXHCG8JouC8eZw5Nd1w9krCS");
        } catch (KeyAddress.IllegalAddressException e) {
            e.printStackTrace();
        }


        if (config.limitFreeRegistrations())
            if(!(tmpAddress.isMatchingKey(session.getPublicKey()) ||
                    config.getNetworkAdminKeyAddress().isMatchingKey(session.getPublicKey()) ||
                    config.getKeysWhiteList().contains(session.getPublicKey()) ||
                    config.getAddressesWhiteList().stream().anyMatch(addr -> addr.isMatchingKey(session.getPublicKey()))
            )) {
                System.out.println("approve ERROR: command needs client key from whitelist");

                return Binder.of(
                        "itemResult", itemResultOfError(Errors.BAD_CLIENT_KEY,"setVerbose", "command needs client key from whitelist"));
            }

        try {
            String nodeLevel = params.getString("node");
            if(nodeLevel != null) {
                if("nothing".equals(nodeLevel)) {
                    node.setVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
                } else if("base".equals(nodeLevel)) {
                    node.setVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
                } else if("detail".equals(nodeLevel)) {
                    node.setVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);
                }
            }

            String networkLevel = params.getString("network");
            if(networkLevel != null) {
                if("nothing".equals(networkLevel)) {
                    node.setNeworkVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
                } else if("base".equals(networkLevel)) {
                    node.setNeworkVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
                } else if("detail".equals(networkLevel)) {
                    node.setNeworkVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);
                }
            }

            String udpLevel = params.getString("udp");
            if(udpLevel != null) {
                if("nothing".equals(udpLevel)) {
                    node.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.NOTHING);
                } else if("base".equals(udpLevel)) {
                    node.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.BASE);
                } else if("detail".equals(udpLevel)) {
                    node.setUDPVerboseLevel(DatagramAdapter.VerboseLevel.DETAILED);
                }
            }
            return Binder.of("itemResult",ItemResult.UNDEFINED);
        } catch (Exception e) {
            System.out.println("getState ERROR: " + e.getMessage());
            return Binder.of(
                    "itemResult", itemResultOfError(Errors.COMMAND_FAILED,"resyncItem", e.getMessage()));
        }
    }

    private Binder getStats(Binder params, Session session) throws CommandFailedException {

        checkNode(session, true);

        if (config == null || node == null || !(
                config.getNetworkAdminKeyAddress().isMatchingKey(session.getPublicKey()) ||
                node.getNodeKey().equals(session.getPublicKey()) ||
                config.getKeysWhiteList().contains(session.getPublicKey()) ||
                config.getAddressesWhiteList().stream().anyMatch(addr -> addr.isMatchingKey(session.getPublicKey()))
        )) {
            System.out.println("command needs admin key");
            return Binder.of(
                    "itemResult", itemResultOfError(Errors.BAD_CLIENT_KEY,"getStats", "command needs admin key"));
        }
        return node.provideStats(params.getInt("showDays",null));
    }

    private Binder getParcelProcessingState(Binder params, Session session) throws CommandFailedException {

        checkNode(session, true);

        try {
            return Binder.of("processingState",
                    node.checkParcelProcessingState((HashId) params.get("parcelId")));
        } catch (Exception e) {
            System.out.println("getParcelProcessingState ERROR: " + e.getMessage());
            //TODO: return processing state not String
            return Binder.of(
                    "processingState",
                    "getParcelProcessingState ERROR: " + e.getMessage()
            );
        }
    }

    private void checkNode(Session session) throws CommandFailedException {
        checkNode(session, false);
    }

    private void checkNode(Session session, boolean checkKeyLimit) throws CommandFailedException {

        // checking node
        if (node == null) {
            throw new CommandFailedException(Errors.NOT_READY, "", "please call again after a while");
        }

        if(node.isSanitating()) {
            //WHILE NODE IS SANITATING IT COMMUNICATES WITH THE OTHER NODES ONLY
            if(netConfig.toList().stream().anyMatch(nodeInfo -> nodeInfo.getPublicKey().equals(session.getPublicKey())))
                return;

            throw new CommandFailedException(Errors.NOT_READY, "", "please call again after a while");
        }

        // checking key limit
        if (checkKeyLimit)
            if (!node.checkKeyLimit(session.getPublicKey()))
                throw new CommandFailedException(Errors.COMMAND_FAILED, "", "exceeded the limit of requests for key per minute, please call again after a while");
    }

    static private Binder networkData = null;

    @Override
    public void on(String path, BasicHTTPService.Handler handler) {
        super.on(path, (request, response) -> {
            if (localCors) {
                Binder hh = response.getHeaders();
                hh.put("Access-Control-Allow-Origin", "*");
                hh.put("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                hh.put("Access-Control-Allow-Headers", "DNT,X-CustomHeader,Keep-Alive,User-Age  nt,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Content-Range,Range");
                hh.put("Access-Control-Expose-Headers", "DNT,X-CustomHeader,Keep-Alive,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Content-Range,Range");
            }
            handler.handle(request, response);
        });
    }

    private Binder storageGetRate(Binder params, Session session) throws IOException {

        checkNode(session, true);

        BigDecimal rate = config.rate.get(NSmartContract.SmartContractType.SLOT1.name());
        String str = rate.toString();
        Binder b = new Binder();
        b.put("U", str);

        return b;
    }

    private Binder querySlotInfo(Binder params, Session session) throws IOException {

        checkNode(session, true);

        Binder res = new Binder();
        res.set("slot_state", null);
        byte[] slot_id = params.getBinary("slot_id");
        byte[] slotBin = node.getLedger().getSmartContractById(HashId.withDigest(slot_id));
        if (slotBin != null) {
            SlotContract slotContract = (SlotContract) Contract.fromPackedTransaction(slotBin);
            res.set("slot_state", slotContract.getStateData());
        }
        return res;
    }

    private Binder queryContract(Binder params, Session session) throws IOException {

        checkNode(session, true);

        Binder res = new Binder();
        res.set("contract", null);
        byte[] slot_id = params.getBinary("slot_id");
        byte[] origin_id = params.getBinary("origin_id");
        byte[] contract_id = params.getBinary("contract_id");
        if ((origin_id == null) && (contract_id == null))
            throw new IOException("invalid arguments (both origin_id and contract_id are null)");
        if ((origin_id != null) && (contract_id != null))
            throw new IOException("invalid arguments (only one origin_id or contract_id is allowed)");
        byte[] slotBin = node.getLedger().getSmartContractById(HashId.withDigest(slot_id));
        if (slotBin != null) {
            SlotContract slotContract = (SlotContract) Contract.fromPackedTransaction(slotBin);
            if (contract_id != null) {
                HashId contractHashId = HashId.withDigest(contract_id);
                res.set("contract", node.getLedger().getContractInStorage(contractHashId));
            } else if (origin_id != null) {
                HashId originHashId = HashId.withDigest(origin_id);
                List<byte[]> storedRevisions = node.getLedger().getContractsInStorageByOrigin(slotContract.getId(), originHashId);
                if (storedRevisions.size() == 1) {
                    res.set("contract", storedRevisions.get(0));
                } else if (storedRevisions.size() > 1) {
                    byte[] latestContract = null;
                    int latestRevision = 0;
                    for (byte[] bin : storedRevisions) {
                        Contract c = Contract.fromPackedTransaction(bin);
                        if (latestRevision < c.getRevision()) {
                            latestRevision = c.getRevision();
                            latestContract = bin;
                        }
                    }
                    res.set("contract", latestContract);
                }
            }
        }
        return res;
    }

    private Binder followerGetRate(Binder params, Session session) throws IOException {

        checkNode(session, true);

        BigDecimal rateOriginDays = config.rate.get(NSmartContract.SmartContractType.FOLLOWER1.name());
        BigDecimal rateCallback = config.rate.get(NSmartContract.SmartContractType.FOLLOWER1.name() + ":callback").divide(rateOriginDays);

        Binder b = new Binder();
        b.put("rateOriginDays", rateOriginDays.toString());
        b.put("rateCallback", rateCallback.toString());

        return b;
    }

    private Binder queryFollowerInfo(Binder params, Session session) throws IOException {

        checkNode(session, true);

        Binder res = new Binder();
        res.set("follower_state", null);
        byte[] follower_id = params.getBinary("follower_id");
        byte[] followerBin = node.getLedger().getSmartContractById(HashId.withDigest(follower_id));

        if (followerBin != null) {
            FollowerContract followerContract = (FollowerContract) Contract.fromPackedTransaction(followerBin);
            res.set("follower_state", followerContract.getStateData());
        }
        return res;
    }

    private Node node;

    public ItemCache getCache() {
        return cache;
    }

    public void setCache(ItemCache cache) {
        this.cache = cache;
    }

    public ParcelCache getParcelCache() {
        return parcelCache;
    }

    public void setParcelCache(ParcelCache cache) {
        this.parcelCache = cache;
    }

    public void setEnvCache(EnvCache cache) {
        this.envCache = cache;
    }

    public void setNetConfig(NetConfig netConfig) {
        this.netConfig = netConfig;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public boolean isLocalCors() {
        return localCors;
    }

    public void setLocalCors(boolean localCors) {
        this.localCors = localCors;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    //    @Override
//    public void start() throws Exception {
//        super.start();
//        addSecureEndpoint("state", (params) -> getState());
//    }
//
//    private Binder getState(Binder params) {
//        response.set("status", "establishing");
//    }
}
