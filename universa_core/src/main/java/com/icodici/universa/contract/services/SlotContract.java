package com.icodici.universa.contract.services;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node.Ledger;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.NodeInfo;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@BiType(name = "SlotContract")
public class SlotContract extends NSmartContract {

    public static final String PREPAID_KD_FIELD_NAME = "prepaid_KD";
    public static final String KEEP_REVISIONS_FIELD_NAME = "keep_revisions";
    public static final String TRACKING_CONTRACT_FIELD_NAME = "tracking_contract";

    public Queue<byte[]> getPackedTrackingContracts() {
        return packedTrackingContracts;
    }

    public Queue<Contract> getTrackingContracts() {
        return trackingContracts;
    }

    //    private byte[] packedTrackingContract;
//    private Contract trackingContract;
    private LinkedList<byte[]> packedTrackingContracts = new LinkedList<>();
    private LinkedList<Contract> trackingContracts = new LinkedList<>();
    private Binder trackingHashes = new Binder();
    private int keepRevisions = 1;

    private int paidU = 0;
    private int prepaidKilobytesForDays = 0;

    public Config getNodeConfig() {
        return nodeConfig;
    }
    public void setNodeConfig(Config nodeConfig) {
        this.nodeConfig = nodeConfig;
    }
    private Config nodeConfig;

    public Ledger getLedger() {
        return ledger;
    }
    public void setLedger(Ledger ledger) {
        this.ledger = ledger;
    }
    private Ledger ledger;

    public NodeInfo getNodeInfo() {
        return nodeInfo;
    }
    public void setNodeInfo(NodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }
    private NodeInfo nodeInfo;

    /**
     * Extract contract from v2 or v3 sealed form, getting revoking and new items from the transaction pack supplied. If
     * the transaction pack fails to resolve a link, no error will be reported - not sure it's a good idea. If need, the
     * exception could be generated with the transaction pack.
     * <p>
     * It is recommended to call {@link #check()} after construction to see the errors.
     *
     * @param sealed binary sealed contract.
     * @param pack   the transaction pack to resolve dependencies again.
     *
     * @throws IOException on the various format errors
     */
    public SlotContract(byte[] sealed, @NonNull TransactionPack pack) throws IOException {
        super(sealed, pack);

        // TODO: check, why trackingContracts is clearing here, so we need to fill it again, however it was done in the deserialization
        try {
            Binder trackingHashesAsBase64 = getStateData().getBinder(TRACKING_CONTRACT_FIELD_NAME);
            for (String k : trackingHashesAsBase64.keySet()) {
                byte[] packed = trackingHashesAsBase64.getBinary(k);
                if(packed != null) {
                    Contract c = Contract.fromPackedTransaction(packed);
//                    if(trackingContracts.size() > 0) {
//                        if (c.getRevision() >= trackingContracts.getFirst().getRevision()) {
//                            trackingContracts.addFirst(c);
//                            packedTrackingContracts.addFirst(packed);
//                        } else {
//                            trackingContracts.addFirst(c);
//                            packedTrackingContracts.addFirst(packed);
//                        }
//                    }
                    if(c != null) {
                        if(trackingContracts != null) {
                            trackingContracts.addFirst(c);
                            packedTrackingContracts.addFirst(packed);
                            trackingHashes.set(String.valueOf(c.getRevision()), c.getId());
                        } else {
                            System.err.println("trackingContracts: " + trackingContracts +
                                    " packedTrackingContracts: " + packedTrackingContracts +
                                    " trackingHashes: " + trackingHashes);
                        }
                    } else {
                        System.err.println("reconstruction storing contract from slot.state.data failed: null");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

//        createSlotSpecific();

//        calculatePrepaidKilobytesForDays();
    }

    public SlotContract() {
        super();

//        createSlotSpecific();

//        calculatePrepaidKilobytesForDays();
    }

    /**
     * Create a default empty new contract using a provided key as issuer and owner and sealer. Default expiration is
     * set to 5 years.
     * <p>
     * This constructor adds key as sealing signature so it is ready to {@link #seal()} just after construction, thought
     * it is necessary to put real data to it first. It is allowed to change owner, expiration and data fields after
     * creation (but before sealing).
     *
     * @param key is {@link PrivateKey} for creating roles "issuer", "owner", "creator" and sign contract
     */
    public SlotContract(PrivateKey key) {
        super(key);

        addSlotSpecific();

//        calculatePrepaidKilobytesForDays();
    }

    public void addSlotSpecific() {
        if(getDefinition().getExtendedType() == null || !getDefinition().getExtendedType().equals(SmartContractType.SLOT1.name()))
            getDefinition().setExtendedType(SmartContractType.SLOT1.name());

        // add modify_data permission

        boolean permExist = false;
        Collection<Permission> mdps = getPermissions().get(ModifyDataPermission.FIELD_NAME);
        if(mdps != null) {
            for (Permission perm : mdps) {
                if (perm.getName() == ModifyDataPermission.FIELD_NAME) {
                    System.out.println(perm.getName() + " " + perm.isAllowedForKeys(getOwner().getKeys()));
                    if (perm.isAllowedForKeys(getOwner().getKeys())) {
                        permExist = true;
                        break;
                    }
                }
            }
        }

        if(!permExist) {
            RoleLink ownerLink = new RoleLink("owner_link", "owner");
            registerRole(ownerLink);
            HashMap<String, Object> fieldsMap = new HashMap<>();
            fieldsMap.put("action", null);
            fieldsMap.put("/expires_at", null);
            fieldsMap.put(KEEP_REVISIONS_FIELD_NAME, null);
            fieldsMap.put(PREPAID_KD_FIELD_NAME, null);
            fieldsMap.put(TRACKING_CONTRACT_FIELD_NAME, null);
            Binder modifyDataParams = Binder.of("fields", fieldsMap);
            ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
            addPermission(modifyDataPermission);
        }
    }

    protected SlotContract initializeWithDsl(Binder root) throws EncryptionError {
        super.initializeWithDsl(root);
        int numRevisions = root.getBinder("state").getBinder("data").getInt(KEEP_REVISIONS_FIELD_NAME, -1);
        if(numRevisions > 0)
            keepRevisions = numRevisions;
        return this;
    }

    public static SlotContract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new SlotContract().initializeWithDsl(binder);
        }
    }

    public byte[] getPackedTrackingContract() {
        if(packedTrackingContracts != null && packedTrackingContracts.size() > 0)
            return packedTrackingContracts.getFirst();

        return null;
    }

    public Contract getTrackingContract() {
        if(trackingContracts != null && trackingContracts.size() > 0)
            return trackingContracts.getFirst();

        return null;
    }

    public void putPackedTrackingContract(byte[] packed) throws IOException {
//        trackingContract = TransactionPack.unpack(packed).getContract();
//        packedTrackingContract = packed;

        Contract c = TransactionPack.unpack(packed).getContract();
        trackingContracts.addFirst(c);
        packedTrackingContracts.addFirst(packed);
        trackingHashes.set(String.valueOf(c.getRevision()), c.getId());

        Binder forState = new Binder();
        for (Contract tc : trackingContracts) {
            forState.set(String.valueOf(tc.getRevision()), tc.getPackedTransaction());
        }
        getStateData().set(TRACKING_CONTRACT_FIELD_NAME, forState);

        if(trackingContracts.size() > keepRevisions) {
            trackingContracts.removeLast();
        }
        if(packedTrackingContracts.size() > keepRevisions) {
            packedTrackingContracts.removeLast();
        }

//        calculatePrepaidKilobytesForDays();
    }

    public void putTrackingContract(Contract c) {
//        packedTrackingContract = c.getPackedTransaction();
//        trackingContract = c;

        trackingContracts.addFirst(c);
        packedTrackingContracts.addFirst(c.getPackedTransaction());
        trackingHashes.set(String.valueOf(c.getRevision()), c.getId());

        Binder forState = new Binder();
        for (Contract tc : trackingContracts) {
            forState.set(String.valueOf(tc.getRevision()), tc.getPackedTransaction());
        }
        getStateData().set(TRACKING_CONTRACT_FIELD_NAME, forState);

        if(trackingContracts.size() > keepRevisions) {
            trackingContracts.removeLast();
        }
        if(packedTrackingContracts.size() > keepRevisions) {
            packedTrackingContracts.removeLast();
        }

//        calculatePrepaidKilobytesForDays();
    }

    public void setKeepRevisions(int keepRevisions) {
        this.keepRevisions = keepRevisions;
    }

    public int getKeepRevisions() {
        return keepRevisions;
    }

    private int calculatePrepaidKilobytesForDays(boolean withSaveToState) {

        for (Contract nc : getNew()) {
            if (nc.isTU(nodeConfig.getTransactionUnitsIssuerKeys(), nodeConfig.getTUIssuerName())) {
                int calculatedPayment = 0;
                boolean isTestPayment = false;
                Contract parent = null;
                for (Contract nrc : nc.getRevoking()) {
                    if (nrc.getId().equals(nc.getParent())) {
                        parent = nrc;
                        break;
                    }
                }
                if (parent != null) {
                    boolean hasTestTU = nc.getStateData().get("test_transaction_units") != null;
                    if (hasTestTU) {
                        isTestPayment = true;
                        calculatedPayment = parent.getStateData().getIntOrThrow("test_transaction_units")
                                - nc.getStateData().getIntOrThrow("test_transaction_units");

                        if (calculatedPayment <= 0) {
                            isTestPayment = false;
                            calculatedPayment = parent.getStateData().getIntOrThrow("transaction_units")
                                    - nc.getStateData().getIntOrThrow("transaction_units");
                        }
                    } else {
                        isTestPayment = false;
                        calculatedPayment = parent.getStateData().getIntOrThrow("transaction_units")
                                - nc.getStateData().getIntOrThrow("transaction_units");
                    }
                }

                if(!isTestPayment) {
                    paidU = calculatedPayment;
                }
            }
        }
        int wasPrepaidKilobytesForDays;
        Contract parentContract = getRevokingItem(getParent());
        if( parentContract!= null) {
            wasPrepaidKilobytesForDays = parentContract.getStateData().getInt(PREPAID_KD_FIELD_NAME, 0);
        } else {
            wasPrepaidKilobytesForDays = 0;
        }
        prepaidKilobytesForDays = wasPrepaidKilobytesForDays + paidU * Config.kilobytesAndDaysPerU;
        if(withSaveToState) {
            getStateData().set(PREPAID_KD_FIELD_NAME, prepaidKilobytesForDays);
        }
//        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
//        if(packedTrackingContract != null) {
//            setExpiresAt(now.plusDays(prepaidKilobytesForDays / (packedTrackingContract.length / 1024)));
//        } else {
//            setExpiresAt(now);
//        }
        return prepaidKilobytesForDays;
    }

    public int getPrepaidKilobytesForDays() {
        return prepaidKilobytesForDays;
    }

    @Override
    public void onContractStorageSubscriptionEvent(ContractStorageSubscription.Event event) {
        MutableEnvironment me;
        if(event instanceof ContractStorageSubscription.ApprovedEvent) {

            // recreate subscription:
            Contract newStoredItem = ((ContractStorageSubscription.ApprovedEvent)event).getNewRevision();

            putTrackingContract(newStoredItem);

            byte[] ebytes = ledger.getEnvironmentFromStorage(getId());
            if (ebytes != null) {
                Binder binder = Boss.unpack(ebytes);
                me = new SlotMutableEnvironment(this, binder);
            } else {
                me = new SlotMutableEnvironment(this);
            }


            // remove old subscriptions
            if(((Set<ContractStorageSubscription>)me.storageSubscriptions()).size() >= keepRevisions) {
                // todo: remove all old
                ledger.removeSlotContractWithAllSubscriptions(getId());
//                ledger.removeEnvironmentSubscriptionsByEnvId(environmentId);
            }

            int storingKiloBytes = 0;
            for(byte[] packed : packedTrackingContracts) {
                storingKiloBytes += packed.length / 1024;
            }
            ZonedDateTime newExpires = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
            newExpires = newExpires.plusDays(prepaidKilobytesForDays / storingKiloBytes);

            long environmentId = ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(me), getPackedTransaction());

            // recalculate storing time for all subscriptions and save them again
            for(Contract tc : trackingContracts) {
                try {
                    ContractStorageSubscription css = me.createStorageSubscription(tc.getId(), newExpires);
                    css.receiveEvents(true);
                    long contractStorageId = ledger.saveContractInStorage(tc.getId(), tc.getPackedTransaction(), css.expiresAt(), tc.getOrigin());
                    long subscriptionId = ledger.saveSubscriptionInStorage(contractStorageId, css.expiresAt());
                    ledger.saveEnvironmentSubscription(subscriptionId, environmentId);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
//            ContractStorageSubscription css = me.createStorageSubscription(newStoredItem.getId(), newExpires);
//            css.receiveEvents(true);
//            long environmentId = ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(me), getPackedTransaction());
//            long contractStorageId = ledger.saveContractInStorage(css.getContract().getId(), css.getContract().getPackedTransaction(), css.expiresAt(), css.getContract().getOrigin());
//            long subscriptionId = ledger.saveSubscriptionInStorage(contractStorageId, css.expiresAt());
//            ledger.saveEnvironmentSubscription(subscriptionId, environmentId);
        } else if(event instanceof ContractStorageSubscription.RevokedEvent) {
            // remove subscription
        }

        event.getSubscription().destroy();
    }

    @Override
    public byte[] seal() {
        calculatePrepaidKilobytesForDays(true);

        return super.seal();
    }

//    @Override
//    public Binder serialize(BiSerializer s) {
//        Binder b = super.serialize(s);
//        return b;
//    }


    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        if(packedTrackingContracts == null) {
            packedTrackingContracts = new LinkedList<>();
        }
        if(trackingContracts == null) {
            trackingContracts = new LinkedList<>();
        }
        if(trackingHashes == null) {
            trackingHashes = new Binder();
        }
        keepRevisions = 1;

        paidU = 0;
        prepaidKilobytesForDays = 0;

        int numRevisions = data.getBinder("state").getBinder("data").getInt(KEEP_REVISIONS_FIELD_NAME, -1);
        if(numRevisions > 0)
            keepRevisions = numRevisions;

        prepaidKilobytesForDays = data.getBinder("state").getBinder("data").getInt(PREPAID_KD_FIELD_NAME, 0);

//        try {
//            putPackedTrackingContract(data.getBinder("state").getBinder("data").getBinary(TRACKING_CONTRACT_FIELD_NAME));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        try {
            Binder trackingHashesAsBase64 = data.getBinder("state").getBinder("data").getBinder(TRACKING_CONTRACT_FIELD_NAME);
            for (String k : trackingHashesAsBase64.keySet()) {
                byte[] packed = trackingHashesAsBase64.getBinary(k);
                if(packed != null) {
                    Contract c = Contract.fromPackedTransaction(packed);
//                    if(trackingContracts.size() > 0) {
//                        if (c.getRevision() >= trackingContracts.getFirst().getRevision()) {
//                            trackingContracts.addFirst(c);
//                            packedTrackingContracts.addFirst(packed);
//                        } else {
//                            trackingContracts.addFirst(c);
//                            packedTrackingContracts.addFirst(packed);
//                        }
//                    }
                    if(c != null) {
                        if(trackingContracts != null) {
                            trackingContracts.addFirst(c);
                            packedTrackingContracts.addFirst(packed);
                            trackingHashes.set(String.valueOf(c.getRevision()), c.getId());
                        } else {
                            System.err.println("trackingContracts: " + trackingContracts +
                                    " packedTrackingContracts: " + packedTrackingContracts +
                                    " trackingHashes: " + trackingHashes);
                        }
                    } else {
                        System.err.println("reconstruction storing contract from slot.state.data failed: null");
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean beforeCreate(ImmutableEnvironment c) {

        boolean checkResult = false;

        calculatePrepaidKilobytesForDays(false);

        boolean hasPayment = false;
        for (Contract nc : getNew()) {
            if(nc.isTU(nodeConfig.getTransactionUnitsIssuerKeys(), nodeConfig.getTUIssuerName())) {
                hasPayment = true;

                int calculatedPayment = 0;
                boolean isTestPayment = false;
                Contract parent = null;
                for(Contract nrc : nc.getRevoking()) {
                    if(nrc.getId().equals(nc.getParent())) {
                        parent = nrc;
                        break;
                    }
                }
                if(parent != null) {
                    boolean hasTestTU = nc.getStateData().get("test_transaction_units") != null;
                    if (hasTestTU) {
                        isTestPayment = true;
                        if (calculatedPayment <= 0) {
                            isTestPayment = false;
                        }
                    } else {
                        isTestPayment = false;
                    }

                    if(isTestPayment) {
                        hasPayment = false;
                        addError(Errors.FAILED_CHECK, "Test payment is not allowed for storing contracts in slots");
                    }

                    if(paidU < nodeConfig.getMinSlotPayment()) {
                        hasPayment = false;
                        addError(Errors.FAILED_CHECK, "Payment for slot contract is below minimum level of " + nodeConfig.getMinSlotPayment() + "U");
                    }
                } else {
                    hasPayment = false;
                    addError(Errors.FAILED_CHECK, "Payment contract is missing parent contarct");
                }
            }
        }

        checkResult = hasPayment;
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Slot contract hasn't valid payment");
            return checkResult;
        }

        checkResult = prepaidKilobytesForDays == getStateData().getInt(PREPAID_KD_FIELD_NAME, 0);
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_KD_FIELD_NAME + "] value. " +
                    "Should be sum of early paid U and paid U by current revision.");
            return checkResult;
        }

        checkResult = additionallySlotCheck(c);

        return checkResult;
    }

    @Override
    public boolean beforeUpdate(ImmutableEnvironment c) {
        boolean checkResult = false;

        calculatePrepaidKilobytesForDays(false);

        checkResult = prepaidKilobytesForDays == getStateData().getInt(PREPAID_KD_FIELD_NAME, 0);
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_KD_FIELD_NAME + "] value. " +
                    "Should be sum of early paid U and paid U by current revision.");
            return checkResult;
        }

        checkResult = additionallySlotCheck(c);

        return checkResult;
    }

    @Override
    public boolean beforeRevoke(ImmutableEnvironment c) {
        return additionallySlotCheck(c);
    }

    private boolean additionallySlotCheck(ImmutableEnvironment ime) {

        boolean checkResult = false;

        checkResult = ime != null;
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Environment should be not null");
            return checkResult;
        }

        checkResult = getExtendedType().equals(SmartContractType.SLOT1.name());
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "definition.extended_type", "illegal value, should be " + SmartContractType.SLOT1.name() + " instead " + getExtendedType());
            return checkResult;
        }

        checkResult = trackingHashes.size() == 0 || getTrackingContract() != null;
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Tracking contract is missed");
            return checkResult;
        }

        if(getTrackingContract() != null) {
            checkResult = getTrackingContract().getOwner().isAllowedForKeys(getCreator().getKeys());
            if (!checkResult) {
                addError(Errors.FAILED_CHECK, "Creator of Slot-contract must has allowed keys for owner of tracking contract");
                return checkResult;
            }
        }

        return checkResult;
    }

    @Override
    public @Nullable Binder onCreated(MutableEnvironment me) {

        int storingKiloBytes = 0;
        for(byte[] packed : packedTrackingContracts) {
            storingKiloBytes += packed.length / 1024;
        }
        ZonedDateTime newExpires = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        newExpires = newExpires.plusDays(prepaidKilobytesForDays / storingKiloBytes);

        long environmentId = ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(me), getPackedTransaction());
        for(Contract tc : trackingContracts) {
            try {
                ContractStorageSubscription css = me.createStorageSubscription(tc.getId(), newExpires);
                css.receiveEvents(true);
                long contractStorageId = ledger.saveContractInStorage(tc.getId(), tc.getPackedTransaction(), css.expiresAt(), tc.getOrigin());
                long subscriptionId = ledger.saveSubscriptionInStorage(contractStorageId, css.expiresAt());
                ledger.saveEnvironmentSubscription(subscriptionId, environmentId);

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("onCreated " + newExpires + " " + prepaidKilobytesForDays / storingKiloBytes);
        }
//        try {
//            ZonedDateTime newExpires = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
//            newExpires = newExpires.plusDays(prepaidKilobytesForDays / (getPackedTrackingContract().length / 1024));
//            ContractStorageSubscription css = me.createStorageSubscription(getTrackingContract().getId(), newExpires);
//            css.receiveEvents(true);
//            long environmentId = ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(me), getPackedTransaction());
//            long contractStorageId = ledger.saveContractInStorage(css.getContract().getId(), css.getContract().getPackedTransaction(), css.expiresAt(), css.getContract().getOrigin());
//            long subscriptionId = ledger.saveSubscriptionInStorage(contractStorageId, css.expiresAt());
//            ledger.saveEnvironmentSubscription(subscriptionId, environmentId);
//            System.out.println(nodeInfo + " onCreated " + newExpires + " " + prepaidKilobytesForDays / (getPackedTrackingContract().length / 1024));
//            System.out.println(nodeInfo + " onCreated " + subscriptionId + " " + contractStorageId);
//
//        } catch (Exception e) {
////            e.printStackTrace();
//        }

        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public Binder onUpdated(MutableEnvironment me) {

        int storingKiloBytes = 0;
        for(byte[] packed : packedTrackingContracts) {
            storingKiloBytes += packed.length / 1024;
        }
        ZonedDateTime newExpires = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        newExpires = newExpires.plusDays(prepaidKilobytesForDays / storingKiloBytes);

        long environmentId = ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(me), getPackedTransaction());
        for(Contract tc : trackingContracts) {
            try {
                ContractStorageSubscription css = me.createStorageSubscription(tc.getId(), newExpires);
                css.receiveEvents(true);
                long contractStorageId = ledger.saveContractInStorage(tc.getId(), tc.getPackedTransaction(), css.expiresAt(), tc.getOrigin());
                long subscriptionId = ledger.saveSubscriptionInStorage(contractStorageId, css.expiresAt());
                ledger.saveEnvironmentSubscription(subscriptionId, environmentId);

            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("onUpdated " + newExpires + " " + prepaidKilobytesForDays / storingKiloBytes);
        }

//        long environmentId = ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(me), getPackedTransaction());
//        for(ContractStorageSubscription css : me.storageSubscriptions()) {
//            ZonedDateTime newExpires = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
//            newExpires = newExpires.plusDays(prepaidKilobytesForDays / (getPackedTrackingContract().length / 1024));
//            long subscriptionId = ledger.saveSubscriptionInStorage(((SlotContractStorageSubscription)css).getContractStorageId(), newExpires);
//            ledger.saveEnvironmentSubscription(subscriptionId, environmentId);
//            System.out.println("onUpdated " + newExpires + " " + prepaidKilobytesForDays / (getPackedTrackingContract().length / 1024));
//            System.out.println("onUpdated " + subscriptionId + " " + ((SlotContractStorageSubscription)css).getId());
//        }
        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public void onRevoked(ImmutableEnvironment ime) {
        ledger.removeSlotContractWithAllSubscriptions(getId());
        System.out.println("onRevoked ");
//        ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(ime), getPackedTransaction());
//
//        for (ContractStorageSubscription css : ime.storageSubscriptions()) {
//            // todo: may be here we can remove subscriptions by set of ids?
//            ledger.removeEnvironmentSubscription(((SlotContractStorageSubscription) css).getId());
//        }
//
//        ledger.removeEnvironment(getId());
    }

    static {
        Config.forceInit(SlotContract.class);
        DefaultBiMapper.registerClass(SlotContract.class);
    }
}
