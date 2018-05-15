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
/**
 * Slot contract is one of several types of smarts contracts that can be run on the node. Slot contract provides
 * paid storing of other contracts at the special storage, control storing time and control storing revisions of
 * tracking contract.
 */
public class SlotContract extends NSmartContract {

    public static final String PREPAID_KD_FIELD_NAME = "prepaid_KD";
    public static final String PREPAID_FROM_TIME_FIELD_NAME = "prepaid_from";
    public static final String STORED_BYTES_FIELD_NAME = "stored_bytes";
    public static final String SPENT_KD_FIELD_NAME = "spent_KD";
    public static final String SPENT_KD_TIME_FIELD_NAME = "spent_KD_time";
    public static final String KEEP_REVISIONS_FIELD_NAME = "keep_revisions";
    public static final String TRACKING_CONTRACT_FIELD_NAME = "tracking_contract";

    public Queue<byte[]> getPackedTrackingContracts() {
        return packedTrackingContracts;
    }

    public Queue<Contract> getTrackingContracts() {
        return trackingContracts;
    }

    private LinkedList<byte[]> packedTrackingContracts = new LinkedList<>();
    private LinkedList<Contract> trackingContracts = new LinkedList<>();
    private int keepRevisions = 1;

    // Calculate U paid with las revision of slot
    private int paidU = 0;
    // All KD (kilobytes*days) prepaid from first revision (sum of all paidU, converted to KD)
    private double prepaidKilobytesForDays = 0;
    // Time of first payment
    private ZonedDateTime prepaidFrom = null;
    // Stored bytes for previous revision of slot. Use for calculate spent KDs
    private long storedEarlyBytes = 0;
    // Spent KDs for previous revision
    private double spentEarlyKDs = 0;
    // Time of spent KD's calculation for previous revision
    private ZonedDateTime spentEarlyKDsTime = null;
    // Spent KDs for current revious revision
    private double spentKDs = 0;
    // Time of spent KD's calculation for current revision
    private ZonedDateTime spentKDsTime = null;

    public Config getNodeConfig() {
        return nodeConfig;
    }

    /**
     * Set node's {@link Config}. Slot needs for config to find and check U contract (needs u issuer keys).
     * @param nodeConfig is {@link Config}
     */
    public void setNodeConfig(Config nodeConfig) {
        this.nodeConfig = nodeConfig;
    }
    private Config nodeConfig;

    public Ledger getLedger() {
        return ledger;
    }

    /**
     * Set {@link Ledger} from the node. Slot contract needs with ledger for creation and update subscriptions.
     * @param ledger is {@link Ledger}
     */
    public void setLedger(Ledger ledger) {
        this.ledger = ledger;
    }
    private Ledger ledger;

    public NodeInfo getNodeInfo() {
        return nodeInfo;
    }

    /**
     * Set {@link NodeInfo}
     * @param nodeInfo
     */
    public void setNodeInfo(NodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }
    private NodeInfo nodeInfo;

    /**
     * Slot contract is one of several types of smarts contracts that can be run on the node. Slot contract provides
     * paid storing of other contracts at the special storage, control storing time and control storing revisions of
     * tracking contract.
     */
    public SlotContract() {
        super();
    }

    /**
     * Slot contract is one of several types of smarts contracts that can be run on the node. Slot contract provides
     * paid storing of other contracts at the special storage, control storing time and control storing revisions of
     * tracking contract.
     * <br><br>
     * Create a default empty new slot contract using a provided key as issuer and owner and sealer. Will set slot's specific
     * permissions and values.
     * Default expiration is set to 5 years.
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
    }

    /**
     * Slot contract is one of several types of smarts contracts that can be run on the node. Slot contract provides
     * paid storing of other contracts at the special storage, control storing time and control storing revisions of
     * tracking contract.
     * <br><br>
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

        deserializeForSlot();
    }

    /**
     * Method adds slot's specific to contract:
     * <ul>
     *     <li><i>definition.extended_type</i> is sets to SLOT1</li>
     *     <li>adds permission <i>modify_data</i> with needed fields</li>
     * </ul>
     */
    public void addSlotSpecific() {
        if(getDefinition().getExtendedType() == null || !getDefinition().getExtendedType().equals(SmartContractType.SLOT1.name()))
            getDefinition().setExtendedType(SmartContractType.SLOT1.name());

        // add modify_data permission

        boolean permExist = false;
        Collection<Permission> mdps = getPermissions().get(ModifyDataPermission.FIELD_NAME);
        if(mdps != null) {
            for (Permission perm : mdps) {
                if (perm.getName() == ModifyDataPermission.FIELD_NAME) {
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
            fieldsMap.put(PREPAID_FROM_TIME_FIELD_NAME, null);
            fieldsMap.put(STORED_BYTES_FIELD_NAME, null);
            fieldsMap.put(SPENT_KD_FIELD_NAME, null);
            fieldsMap.put(SPENT_KD_TIME_FIELD_NAME, null);
            fieldsMap.put(TRACKING_CONTRACT_FIELD_NAME, null);
            Binder modifyDataParams = Binder.of("fields", fieldsMap);
            ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
            addPermission(modifyDataPermission);
        }
    }

    /**
     * Method calls from {@link SlotContract#fromDslFile(String)} and initialize contract from given binder.
     * @param root id binder with initialized data
     * @return created and ready {@link SlotContract} contract.
     * @throws EncryptionError if something went wrong
     */
    protected SlotContract initializeWithDsl(Binder root) throws EncryptionError {
        super.initializeWithDsl(root);
        int numRevisions = root.getBinder("state").getBinder("data").getInt(KEEP_REVISIONS_FIELD_NAME, -1);
        if(numRevisions > 0)
            keepRevisions = numRevisions;
        return this;
    }

    /**
     * Method creates {@link SlotContract} contract from dsl file where contract is described.
     * @param fileName is path to dsl file with yaml structure of data for contract.
     * @return created and ready {@link SlotContract} contract.
     * @throws IOException if something went wrong
     */
    public static SlotContract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new SlotContract().initializeWithDsl(binder);
        }
    }

    /**
     * @return last revision of the tracking contract packed as {@link TransactionPack}.
     */
    public byte[] getPackedTrackingContract() {
        if(packedTrackingContracts != null && packedTrackingContracts.size() > 0)
            return packedTrackingContracts.getFirst();

        return null;
    }

    /**
     * @return last revision of the tracking contract.
     */
    public Contract getTrackingContract() {
        if(trackingContracts != null && trackingContracts.size() > 0)
            return trackingContracts.getFirst();

        return null;
    }

    /**
     * @param hashId contract's id to check
     * @return true if hashId is present in tracking revisions
     */
    public boolean isContractTracking(HashId hashId) {
        if (trackingContracts != null) {
            for (Contract contract : trackingContracts) {
                if (contract.getId().equals(hashId))
                    return true;
            }
        }
        return false;
    }

    /**
     * Put contract to the tracking contract's revisions queue. Contract will be created from given bytes array.
     * If queue contains more then {@link SlotContract#keepRevisions} revisions then last one will removed.
     * @param packed is bytes array with packed {@link TransactionPack}.
     * @throws IOException if something went wrong.
     */
    public void putPackedTrackingContract(byte[] packed) throws IOException {

        Contract c = TransactionPack.unpack(packed).getContract();
        trackingContracts.addFirst(c);
        packedTrackingContracts.addFirst(packed);

        Binder forState = new Binder();
        for (Contract tc : trackingContracts) {
            forState.set(tc.getId().toBase64String(), tc.getPackedTransaction());
        }
        getStateData().set(TRACKING_CONTRACT_FIELD_NAME, forState);

        if(trackingContracts.size() > keepRevisions) {
            trackingContracts.removeLast();
        }
        if(packedTrackingContracts.size() > keepRevisions) {
            packedTrackingContracts.removeLast();
        }

        int storingBytes = 0;
        for(byte[] p : packedTrackingContracts) {
            storingBytes += p.length;
        }
        getStateData().set(STORED_BYTES_FIELD_NAME, storingBytes);
    }

    /**
     * Put contract to the tracking contract's revisions queue.
     * If queue contains more then {@link SlotContract#keepRevisions} revisions then last one will removed.
     * @param c is revision of tracking {@link Contract}.
     */
    public void putTrackingContract(Contract c) {

        trackingContracts.addFirst(c);
        packedTrackingContracts.addFirst(c.getPackedTransaction());

        Binder forState = new Binder();
        for (Contract tc : trackingContracts) {
            forState.set(tc.getId().toBase64String(), tc.getPackedTransaction());
        }
        getStateData().set(TRACKING_CONTRACT_FIELD_NAME, forState);

        if(trackingContracts.size() > keepRevisions) {
            trackingContracts.removeLast();
        }
        if(packedTrackingContracts.size() > keepRevisions) {
            packedTrackingContracts.removeLast();
        }

        int storingBytes = 0;
        for(byte[] p : packedTrackingContracts) {
            storingBytes += p.length;
        }
        getStateData().set(STORED_BYTES_FIELD_NAME, storingBytes);
    }

    /**
     * Sets number of revisions of tracking contract to hold in the storage.
     * @param keepRevisions is number of revisions to keep.
     */
    public void setKeepRevisions(int keepRevisions) {
        this.keepRevisions = keepRevisions;

        while(trackingContracts.size() > keepRevisions) {
            trackingContracts.removeLast();
        }
        while(packedTrackingContracts.size() > keepRevisions) {
            packedTrackingContracts.removeLast();
        }

        getStateData().set(KEEP_REVISIONS_FIELD_NAME, keepRevisions);
    }

    /**
     * @return number of revisions of tracking contract to hold in the storage.
     */
    public int getKeepRevisions() {
        return keepRevisions;
    }

    /**
     * It is private method that looking for U contract in the new items of this slot contract. Then calculates
     * new payment, looking for already paid, summize it and calculate new prepaid period for storing, that sets to
     * {@link SlotContract#prepaidKilobytesForDays}. This field is measured in the kilobytes*days, means how many kilobytes
     * storage can hold for how many days.
     * But if withSaveToState param is false, calculated value
     * do not saving to state. It is useful for checking set state.data values.
     * <br><br> Additionally will be calculated new times of payment refilling, and storing info for previous revision of slot.
     * It is also useful for slot checking.
     * @param withSaveToState if true, calculated values is saving to  state.data
     * @return calculated {@link SlotContract#prepaidKilobytesForDays}.
     */
    private double calculatePrepaidKilobytesForDays(boolean withSaveToState) {

        // first of all looking for U contract and calculate paid U amount.
        for (Contract nc : getNew()) {
            if (nc.isU(nodeConfig.getTransactionUnitsIssuerKeys(), nodeConfig.getTUIssuerName())) {
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

        // then looking for prepaid early U that can be find at the stat.data
        // additionally we looking for and calculate times of payment fillings and some other data
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        double wasPrepaidKilobytesForDays;
        long wasPrepaidFrom = now.toEpochSecond();
        long spentEarlyKDsTimeSecs = now.toEpochSecond();
        Contract parentContract = getRevokingItem(getParent());
        if(parentContract != null) {
            wasPrepaidKilobytesForDays = parentContract.getStateData().getDouble(PREPAID_KD_FIELD_NAME);
            wasPrepaidFrom = parentContract.getStateData().getLong(PREPAID_FROM_TIME_FIELD_NAME, now.toEpochSecond());
            storedEarlyBytes = parentContract.getStateData().getLong(STORED_BYTES_FIELD_NAME, 0);
            spentEarlyKDs = parentContract.getStateData().getDouble(SPENT_KD_FIELD_NAME);
            spentEarlyKDsTimeSecs = parentContract.getStateData().getLong(SPENT_KD_TIME_FIELD_NAME, now.toEpochSecond());
        } else {
            wasPrepaidKilobytesForDays = 0;
        }

        spentEarlyKDsTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(spentEarlyKDsTimeSecs), ZoneId.systemDefault());
        prepaidFrom = ZonedDateTime.ofInstant(Instant.ofEpochSecond(wasPrepaidFrom), ZoneId.systemDefault());
        prepaidKilobytesForDays = wasPrepaidKilobytesForDays + paidU * Config.kilobytesAndDaysPerU;

        spentKDsTime = now;

        // if true we save it to stat.data
        if(withSaveToState) {
            getStateData().set(PREPAID_KD_FIELD_NAME, prepaidKilobytesForDays);
            if(getRevision() == 1) {
                getStateData().set(PREPAID_FROM_TIME_FIELD_NAME, now.toEpochSecond());
            }

            int storingBytes = 0;
            for(byte[] p : packedTrackingContracts) {
                storingBytes += p.length;
            }
            getStateData().set(STORED_BYTES_FIELD_NAME, storingBytes);

            long spentSeconds = (spentKDsTime.toEpochSecond() - spentEarlyKDsTime.toEpochSecond());
            double spentDays = (double) spentSeconds / (3600 * 24);
            spentKDs = spentEarlyKDs + spentDays * (storedEarlyBytes / 1024);

            getStateData().set(SPENT_KD_FIELD_NAME, spentKDs);
            getStateData().set(SPENT_KD_TIME_FIELD_NAME, spentKDsTime.toEpochSecond());
        }

        return prepaidKilobytesForDays;
    }

    /**
     * Own private slot's method for saving subscription. It calls
     * from {@link SlotContract#onContractStorageSubscriptionEvent(ContractStorageSubscription.Event)} (when tracking
     * contract have registered new revision, from {@link SlotContract#onCreated(MutableEnvironment)} and
     * from {@link SlotContract#onUpdated(MutableEnvironment)} (both when this slot contract have registered new revision).
     * It recalculate storing params (storing time) and update expiring dates for each revision at the ledger.
     * @param me is {@link MutableEnvironment} object with some data.
     */
    private void saveSubscriptionsToLedger(MutableEnvironment me) {

        // recalculate storing info without saving to state to get valid storing data
        calculatePrepaidKilobytesForDays(false);

        int storingBytes = 0;
        for(byte[] packed : packedTrackingContracts) {
            storingBytes += packed.length;
        }

        ZonedDateTime newExpires = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());

        // calculate time that will be added to now as new expiring time
        // it is difference of all prepaid KD (kilobytes*days) and already spent divided to new storing volume.
        double days = (prepaidKilobytesForDays - spentKDs) * Config.kilobytesAndDaysPerU * 1024 / storingBytes;
        double hours = days * 24;
        long seconds = (long) (days * 24 * 3600);
        newExpires = newExpires.plusSeconds(seconds);

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
    }

    /**
     * @return calculated prepaid KD (kilobytes*days) for all time, from first revision
     */
    public double getPrepaidKilobytesForDays() {
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
            ledger.removeSlotContractWithAllSubscriptions(getId());

            // and save new
            saveSubscriptionsToLedger(me);

        } else if(event instanceof ContractStorageSubscription.RevokedEvent) {
            // remove subscriptions
            ledger.removeSlotContractWithAllSubscriptions(getId());
        }

        event.getSubscription().destroy();
    }

    @Override
    /**
     * We override seal method to recalculate holding at the state.data values
     */
    public byte[] seal() {
        calculatePrepaidKilobytesForDays(true);

        return super.seal();
    }


    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        deserializeForSlot();
    }

    /**
     * Extract values from deserializing object for slot fields.
     */
    private void deserializeForSlot() {

        if(packedTrackingContracts == null) {
            packedTrackingContracts = new LinkedList<>();
        }
        if(trackingContracts == null) {
            trackingContracts = new LinkedList<>();
        }

        // extract keep_revisions value
        int numRevisions = getStateData().getInt(KEEP_REVISIONS_FIELD_NAME, -1);
        if(numRevisions > 0)
            keepRevisions = numRevisions;

        // extract saved prepaid KD (kilobytes*days) value
        prepaidKilobytesForDays = getStateData().getInt(PREPAID_KD_FIELD_NAME, 0);

        // and extract time when first time payment was
        long prepaidFromSeconds = getStateData().getLong(PREPAID_FROM_TIME_FIELD_NAME, 0);
        prepaidFrom = ZonedDateTime.ofInstant(Instant.ofEpochSecond(prepaidFromSeconds), ZoneId.systemDefault());

        // extract and sort by revision number
        try {
            List<Contract> contracts = new ArrayList<>();
            Binder trackingHashesAsBase64 = getStateData().getBinder(TRACKING_CONTRACT_FIELD_NAME);
            for (String k : trackingHashesAsBase64.keySet()) {
                byte[] packed = trackingHashesAsBase64.getBinary(k);
                if(packed != null) {
                    Contract c = Contract.fromPackedTransaction(packed);
                    if(c != null) {
                        contracts.add(c);
                    } else {
                        System.err.println("reconstruction storing contract from slot.state.data failed: null");
                    }
                }
            }
            Collections.sort(contracts, new Comparator<Contract>() {

                public int compare(Contract o1, Contract o2) {
                    return o1.getRevision() - o2.getRevision();
                }
            });
            for (Contract c : contracts) {
                if(trackingContracts != null) {
                    trackingContracts.addFirst(c);
                    packedTrackingContracts.addFirst(c.getPackedTransaction());
                } else {
                    System.err.println("trackingContracts: " + trackingContracts +
                            " packedTrackingContracts: " + packedTrackingContracts);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean beforeCreate(ImmutableEnvironment c) {

        boolean checkResult = false;

        // recalculate storing info without saving to state to get valid storing data
        calculatePrepaidKilobytesForDays(false);

        // check that slot has payment and payment is valid
        boolean hasPayment = false;
        for (Contract nc : getNew()) {
            if(nc.isU(nodeConfig.getTransactionUnitsIssuerKeys(), nodeConfig.getTUIssuerName())) {
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

        // check that payment was not hacked
        checkResult = prepaidKilobytesForDays == getStateData().getInt(PREPAID_KD_FIELD_NAME, 0);
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_KD_FIELD_NAME + "] value. " +
                    "Should be sum of early paid U and paid U by current revision.");
            return checkResult;
        }

        // and call common slot check
        checkResult = additionallySlotCheck(c);

        return checkResult;
    }

    @Override
    public boolean beforeUpdate(ImmutableEnvironment c) {
        boolean checkResult = false;

        // recalculate storing info without saving to state to get valid storing data
        calculatePrepaidKilobytesForDays(false);

        // check that payment was not hacked
        checkResult = prepaidKilobytesForDays == getStateData().getInt(PREPAID_KD_FIELD_NAME, 0);
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_KD_FIELD_NAME + "] value. " +
                    "Should be sum of early paid U and paid U by current revision.");
            return checkResult;
        }

        // and call common slot check
        checkResult = additionallySlotCheck(c);

        return checkResult;
    }

    @Override
    public boolean beforeRevoke(ImmutableEnvironment c) {
        return additionallySlotCheck(c);
    }

    private boolean additionallySlotCheck(ImmutableEnvironment ime) {

        boolean checkResult = false;

        // check slot environment
        checkResult = ime != null;
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Environment should be not null");
            return checkResult;
        }

        // check that slot has known and valid type of smart contract
        checkResult = getExtendedType().equals(SmartContractType.SLOT1.name());
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "definition.extended_type", "illegal value, should be " + SmartContractType.SLOT1.name() + " instead " + getExtendedType());
            return checkResult;
        }

        // check for tracking contract existing
        checkResult = trackingContracts.size() == 0 || getTrackingContract() != null;
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Tracking contract is missed");
            return checkResult;
        }

        if(getTrackingContract() != null) {
            // check for that last revision of tracking contract has same owner as creator of slot
            checkResult = getTrackingContract().getOwner().isAllowedForKeys(getCreator().getKeys());
            if (!checkResult) {
                addError(Errors.FAILED_CHECK, "Creator of Slot-contract must has allowed keys for owner of tracking contract");
                return checkResult;
            }

            // check for all revisions of tracking contract has same origin
            for(Contract tc : trackingContracts) {
                checkResult = getTrackingContract().getOrigin().equals(tc.getOrigin());
                if (!checkResult) {
                    addError(Errors.FAILED_CHECK, "Slot-contract should store only contracts with same origin");
                    return checkResult;
                }
            }
        }

        return checkResult;
    }

    @Override
    public @Nullable Binder onCreated(MutableEnvironment me) {
        saveSubscriptionsToLedger(me);

        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public Binder onUpdated(MutableEnvironment me) {
        saveSubscriptionsToLedger(me);

        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public void onRevoked(ImmutableEnvironment ime) {
        // remove subscriptions
        ledger.removeSlotContractWithAllSubscriptions(getId());
    }

    static {
        Config.forceInit(SlotContract.class);
        DefaultBiMapper.registerClass(SlotContract.class);
    }
}
