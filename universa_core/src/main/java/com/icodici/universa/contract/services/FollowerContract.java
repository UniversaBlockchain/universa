package com.icodici.universa.contract.services;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
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
import java.util.stream.Collectors;

@BiType(name = "FollowerContract")
/**
 * Follower contract is one of several types of smarts contracts that can be run on the node. Follower contract provides
 * ....
 */
public class FollowerContract extends NSmartContract {

    public static final String PREPAID_OD_FIELD_NAME = "prepaid_OD";
    public static final String PREPAID_FROM_TIME_FIELD_NAME = "prepaid_from";
    public static final String FOLLOWED_ORIGINS_FIELD_NAME = "followed_origins";
    public static final String SPENT_OD_FIELD_NAME = "spent_OD";
    public static final String SPENT_OD_TIME_FIELD_NAME = "spent_OD_time";
    public static final String CALLBACK_RATE_FIELD_NAME = "callback_rate";
    public static final String TRACKING_ORIGINS_FIELD_NAME = "tracking_origins";
    public static final String CALLBACK_KEYS_FIELD_NAME = "callback_keys";

    // in data (definition.data, state.data or transactional.data) of following contract
    public static final String FOLLOWER_ROLES_FIELD_NAME = "follower_roles";

    private Map<HashId, String> trackingOrigins = new HashMap<>();
    private Map<String, PublicKey> callbackKeys = new HashMap<>();

    // Calculate U paid with las revision of slot
    private int paidU = 0;
    // All OD (origins*days) prepaid from first revision (sum of all paidU, converted to OD)
    private double prepaidOriginDays = 0;
    // Time of first payment
    private ZonedDateTime prepaidFrom = null;
    // Followed origins for previous revision. Use for calculate spent ODs
    private long storedEarlyOrigins = 0;
    // Spent ODs for previous revision
    private double spentEarlyODs = 0;
    // Time of spent OD's calculation for previous revision
    private ZonedDateTime spentEarlyODsTime = null;
    // Spent ODs for current revision
    private double spentODs = 0;
    // Time of spent OD's calculation for current revision
    private ZonedDateTime spentODsTime = null;
    // Current revision callback rate
    private double callbackRate = 0;

    /**
     * Follower contract is one of several types of smarts contracts that can be run on the node. Slot contract provides
     * .....
     */
    public FollowerContract() {
        super();
    }

    /**
     * Follower contract is one of several types of smarts contracts that can be run on the node. Follower contract provides
     * ....
     * <br><br>
     * Create a default empty new follower contract using a provided key as issuer and owner and sealer. Will set
     * follower's specific permissions and values.
     * <p>
     * This constructor adds key as sealing signature so it is ready to {@link #seal()} just after construction, thought
     * it is necessary to put real data to it first. It is allowed to change owner, expiration and data fields after
     * creation (but before sealing).
     *
     * @param key is {@link PrivateKey} for creating roles "issuer", "owner", "creator" and sign contract
     */
    public FollowerContract(PrivateKey key) {
        super(key);

        addFollowerSpecific();
    }

    /**
     * Follower contract is one of several types of smarts contracts that can be run on the node. Follower contract provides
     * ....
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
    public FollowerContract(byte[] sealed, @NonNull TransactionPack pack) throws IOException {
        super(sealed, pack);

        deserializeForFollower();
    }

    /**
     * Method adds follower's specific to contract:
     * <ul>
     *     <li><i>definition.extended_type</i> is sets to FOLLOWER1</li>
     *     <li>adds permission <i>modify_data</i> with needed fields</li>
     * </ul>
     */
    public void addFollowerSpecific() {
        if(getDefinition().getExtendedType() == null || !getDefinition().getExtendedType().equals(SmartContractType.FOLLOWER1.name()))
           getDefinition().setExtendedType(SmartContractType.FOLLOWER1.name());

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
            fieldsMap.put(PAID_U_FIELD_NAME, null);
            fieldsMap.put(PREPAID_OD_FIELD_NAME, null);
            fieldsMap.put(PREPAID_FROM_TIME_FIELD_NAME, null);
            fieldsMap.put(FOLLOWED_ORIGINS_FIELD_NAME, null);
            fieldsMap.put(SPENT_OD_FIELD_NAME, null);
            fieldsMap.put(SPENT_OD_TIME_FIELD_NAME, null);
            fieldsMap.put(CALLBACK_RATE_FIELD_NAME, null);
            fieldsMap.put(TRACKING_ORIGINS_FIELD_NAME, null);
            fieldsMap.put(CALLBACK_KEYS_FIELD_NAME, null);
               Binder modifyDataParams = Binder.of("fields", fieldsMap);
            ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
            addPermission(modifyDataPermission);
        }
    }

    /**
     * Method calls from {@link FollowerContract#fromDslFile(String)} and initialize contract from given binder.
     * @param root id binder with initialized data
     * @return created and ready {@link FollowerContract} contract.
     * @throws EncryptionError if something went wrong
     */
    protected FollowerContract initializeWithDsl(Binder root) throws EncryptionError {
        super.initializeWithDsl(root);
        return this;
    }

    /**
     * Method creates {@link FollowerContract} contract from dsl file where contract is described.
     * @param fileName is path to dsl file with yaml structure of data for contract.
     * @return created and ready {@link FollowerContract} contract.
     * @throws IOException if something went wrong
     */
    public static FollowerContract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new FollowerContract().initializeWithDsl(binder);
        }
    }

    public Map<HashId, String> getTrackingOrigins() {
        return trackingOrigins;
    }

    public Map<String, PublicKey> getCallbackKeys() {
        return callbackKeys;
    }

    /**
     * @param origin to check
     * @return true if origin is present in tracking revisions
     */
    public boolean isOriginTracking(HashId origin) {
        if (trackingOrigins != null)
            return trackingOrigins.containsKey(origin);

        return false;
    }

    /**
     * Put new tracking origin and his callback data (URL and callback public key) to the follower contract.
     * If origin already contained in follower contract, old callback data is replaced.
     * If callback URL already contained in follower contract, old callback key is replaced.
     * @param origin for tracking {@link HashId}.
     * @param URL for callback if registered new revision with tracking origin
     * @param key for checking receipt from callback by network
     */
    public void putTrackingOrigin(HashId origin, String URL, PublicKey key) {
        trackingOrigins.put(origin, URL);
        callbackKeys.put(URL, key);
    }

    /**
     * Remove tracking origin from the follower contract.
     * @param origin for remove {@link HashId}.
     */
    public void removeTrackingOrigin(HashId origin) {
        if (trackingOrigins.containsKey(origin)) {
            String URL = trackingOrigins.get(origin);

            trackingOrigins.remove(origin);

            if (!trackingOrigins.containsValue(URL))
                callbackKeys.remove(URL);
        }
    }

    /**
     * @param URL to check
     * @return true if callback URL is used in tracking origins
     */
    public boolean isCallbackURLUsed(String URL) {
        if (callbackKeys != null)
            return callbackKeys.containsKey(URL);

        return false;
    }

    /**
     * @param URL is updated callback URL
     * @param key is public check of callback for update on callback URL
     * @return true if callback URL was updated
     */
    public boolean updateCallbackKey(String URL, PublicKey key) {
        if ((callbackKeys != null) && callbackKeys.containsKey(URL)) {
            callbackKeys.put(URL, key);
            return true;
        }

        return false;
    }

    /**
     * @param contract for followable checking
     * @return true if {@link Contract} can be follow by this {@link FollowerContract}
     */
    public boolean canFollowContract(Contract contract) {
        // check for contract owner
        Role owner = contract.getOwner();
        if (owner.isAllowedForKeys(getSealedByKeys()))
            return true;

        // check for roles from field data.follower_roles in all sections of contract
        List<String> sections = Arrays.asList("definition", "state", "transactional");

        return (sections.stream().anyMatch(section -> {
            try {
                Object followerRoles = contract.get(section + ".data." + FOLLOWER_ROLES_FIELD_NAME);
                if (((followerRoles != null) && followerRoles instanceof Collection) &&
                        (((Collection)followerRoles).stream().anyMatch(
                                r -> ((r instanceof Role) && ((Role) r).isAllowedForKeys(getSealedByKeys()))
                        )))
                    return true;
            } catch (IllegalArgumentException e) {} // no followable roles in <section>.data

            return false;
        }));
    }

    /**
     * It is private method that looking for U contract in the new items of this follower contract. Then calculates
     * new payment, looking for already paid, summize it and calculate new prepaid period for storing, that sets to
     * {@link FollowerContract#prepaidOriginDays}. This field is measured in the origins*days, means how many origins
     * can follow for how many days.
     * But if withSaveToState param is false, calculated value
     * do not saving to state. It is useful for checking set state.data values.
     * @param withSaveToState if true, calculated values is saving to  state.data
     * @return calculated {@link FollowerContract#calculatePrepaidOriginDays}.
     */
    private double calculatePrepaidOriginDays(boolean withSaveToState) {

        paidU = getPaidU();

        if (callbackRate == 0)
            callbackRate = getRate("callback");

        // then looking for prepaid early U that can be find at the stat.data
        // additionally we looking for and calculate times of payment fillings and some other data
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        double wasPrepaidOriginDays;
        long wasPrepaidFrom = now.toEpochSecond();
        long spentEarlyODsTimeSecs = now.toEpochSecond();
        Contract parentContract = getRevokingItem(getParent());
        if(parentContract != null) {
            wasPrepaidOriginDays = parentContract.getStateData().getDouble(PREPAID_OD_FIELD_NAME);
            wasPrepaidFrom = parentContract.getStateData().getLong(PREPAID_FROM_TIME_FIELD_NAME, now.toEpochSecond());
            storedEarlyOrigins = parentContract.getStateData().getLong(FOLLOWED_ORIGINS_FIELD_NAME, 0);
            spentEarlyODs = parentContract.getStateData().getDouble(SPENT_OD_FIELD_NAME);
            spentEarlyODsTimeSecs = parentContract.getStateData().getLong(SPENT_OD_TIME_FIELD_NAME, now.toEpochSecond());
        } else {
            wasPrepaidOriginDays = 0;
        }

        spentEarlyODsTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(spentEarlyODsTimeSecs), ZoneId.systemDefault());
        prepaidFrom = ZonedDateTime.ofInstant(Instant.ofEpochSecond(wasPrepaidFrom), ZoneId.systemDefault());
        prepaidOriginDays = wasPrepaidOriginDays + paidU * getRate();

        spentODsTime = now;

        long spentSeconds = (spentODsTime.toEpochSecond() - spentEarlyODsTime.toEpochSecond());
        double spentDays = (double) spentSeconds / (3600 * 24);
        spentODs = spentEarlyODs + spentDays * storedEarlyOrigins;

        // if true we save it to stat.data
        if(withSaveToState) {
            getStateData().set(PAID_U_FIELD_NAME, paidU);

            getStateData().set(PREPAID_OD_FIELD_NAME, prepaidOriginDays);
            if(getRevision() == 1)
                getStateData().set(PREPAID_FROM_TIME_FIELD_NAME, now.toEpochSecond());

            getStateData().set(FOLLOWED_ORIGINS_FIELD_NAME, trackingOrigins.size());

            getStateData().set(SPENT_OD_FIELD_NAME, spentODs);
            getStateData().set(SPENT_OD_TIME_FIELD_NAME, spentODsTime.toEpochSecond());

            getStateData().set(CALLBACK_RATE_FIELD_NAME, callbackRate);
        }

        return prepaidOriginDays;
    }

    /**
     * Own private slot's method for saving subscription. It calls
     * from {@link FollowerContract#onContractSubscriptionEvent(ContractSubscription.Event)} (when tracking
     * contract have registered new revision, from {@link FollowerContract#onCreated(MutableEnvironment)} and
     * from {@link FollowerContract#onUpdated(MutableEnvironment)} (both when this slot contract have registered new revision).
     * It recalculate storing params (storing time) and update expiring dates for each revision at the ledger.
     * @param me is {@link MutableEnvironment} object with some data.
     */
    private void updateSubscriptions(MutableEnvironment me) {

        // recalculate prepaid origins*days without saving to state
        calculatePrepaidOriginDays(false);

        // recalculate time that will be added to now as new expiring time
        // it is difference of all prepaid ODs (origins*days) and already spent divided to new number of tracking origins.
        double days = (prepaidOriginDays - spentODs - me.getSubscriptionsCallbacksSpentODs()) / trackingOrigins.size();
        long seconds = (long) (days * 24 * 3600);
        ZonedDateTime newExpires = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault())
                .plusSeconds(seconds);

        // recalculate muted period of follower contract subscription
        days = (me.getSubscriptionsStartedCallbacks() + 1) * callbackRate / trackingOrigins.size();
        seconds = (long) (days * 24 * 3600);
        ZonedDateTime newMuted = newExpires.minusSeconds(seconds);

        Set<HashId> newOrigins = new HashSet<>(trackingOrigins.keySet());

        me.followerSubscriptions().forEach(sub -> {
            HashId origin = sub.getOrigin();
            if (newOrigins.contains(origin)) {
                me.setSubscriptionExpiresAtAndMutedAt(sub, newExpires, newMuted);
                newOrigins.remove(origin);
            } else
                me.destroySubscription(sub);
        });

        for (HashId origin: newOrigins) {
            try {
                ContractSubscription sub = me.createFollowerSubscription(origin, newExpires, newMuted);
                sub.receiveEvents(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * @return calculated prepaid origins*days for all time, from first revision
     */
    public double getPrepaidOriginsForDays() {
        return prepaidOriginDays;
    }

    @Override
    public void onContractSubscriptionEvent(ContractSubscription.Event event) {

        if (event instanceof ContractSubscription.ApprovedEvent) {
            MutableEnvironment me = ((ContractSubscription.ApprovedEvent) event).getEnvironment();

            ContractSubscription sub = event.getSubscription();
            me.increaseStartedCallbacks(sub);

            // decrease muted period of all follower subscription in environment of contract
            double deltaDays = -callbackRate / trackingOrigins.size();
            int deltaSeconds = (int) (deltaDays * 24 * 3600);

            me.followerSubscriptions().forEach(fsub -> me.changeSubscriptionMutedAt(fsub, deltaSeconds));
        } else if (event instanceof ContractSubscription.CompletedEvent) {
            MutableEnvironment me = ((ContractSubscription.CompletedEvent) event).getEnvironment();

            ContractSubscription sub = event.getSubscription();
            me.decreaseStartedCallbacks(sub);
            me.increaseCallbacksSpent(sub, callbackRate);

            // decrease expires period of all follower subscription in environment of contract
            double deltaDays = callbackRate / trackingOrigins.size();
            int deltaSeconds = (int) (deltaDays * 24 * 3600);

            me.followerSubscriptions().forEach(fsub -> me.decreaseSubscriptionExpiresAt(fsub, deltaSeconds));
        } else if (event instanceof ContractSubscription.FailedEvent) {
            MutableEnvironment me = ((ContractSubscription.FailedEvent) event).getEnvironment();

            ContractSubscription sub = event.getSubscription();
            me.decreaseStartedCallbacks(sub);

            // increase muted period of all follower subscription in environment of contract
            double deltaDays = callbackRate / trackingOrigins.size();
            int deltaSeconds = (int) (deltaDays * 24 * 3600);

            me.followerSubscriptions().forEach(fsub -> me.changeSubscriptionMutedAt(fsub, deltaSeconds));
        }
    }

    @Override
    /**
     * We override seal method to recalculate holding at the state.data values
     */
    public byte[] seal() {
        saveTrackingOriginsToState();
        calculatePrepaidOriginDays(true);

        return super.seal();
    }

    private void saveTrackingOriginsToState() {
        Binder origins = new Binder();
        for (Map.Entry<HashId, String> entry: trackingOrigins.entrySet()) {
            origins.set(entry.getKey().toBase64String(), entry.getValue());
        }
        getStateData().set(TRACKING_ORIGINS_FIELD_NAME, origins);

        Binder callbacks = new Binder();
        for (Map.Entry<String, PublicKey> entry: callbackKeys.entrySet()) {
            callbacks.set(entry.getKey(), entry.getValue().pack());
        }
        getStateData().set(CALLBACK_KEYS_FIELD_NAME, callbacks);
    }


    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        deserializeForFollower();
    }

    /**
     * Extract values from deserializing object for follower fields.
     */
    private void deserializeForFollower() {

        if(trackingOrigins == null)
            trackingOrigins = new HashMap<>();
        else
            trackingOrigins.clear();

        if(callbackKeys == null)
            callbackKeys = new HashMap<>();
        else
            callbackKeys.clear();

        // extract paided U
        paidU = getStateData().getInt(PAID_U_FIELD_NAME, 0);

        // extract saved rate of callback price for current revision
        callbackRate = getStateData().getDouble(CALLBACK_RATE_FIELD_NAME);

        // extract saved prepaid OD (origins*days) value
        prepaidOriginDays = getStateData().getInt(PREPAID_OD_FIELD_NAME, 0);

        // and extract time when first time payment was
        long prepaidFromSeconds = getStateData().getLong(PREPAID_FROM_TIME_FIELD_NAME, 0);
        prepaidFrom = ZonedDateTime.ofInstant(Instant.ofEpochSecond(prepaidFromSeconds), ZoneId.systemDefault());

        // extract tracking origins nad callbacks data
        Binder trackingOriginsAsBase64 = getStateData().getBinder(TRACKING_ORIGINS_FIELD_NAME);
        Binder callbacksData = getStateData().getBinder(CALLBACK_KEYS_FIELD_NAME);

        for (String URL: callbacksData.keySet()) {
            byte[] packedKey = callbacksData.getBinary(URL);
            try {
                PublicKey key = new PublicKey(packedKey);
                callbackKeys.put(URL, key);
            } catch (EncryptionError encryptionError) {}
        }

        for (String s: trackingOriginsAsBase64.keySet()) {
            String URL = trackingOriginsAsBase64.getString(s);
            HashId origin = HashId.withDigest(s);

            if (callbackKeys.containsKey(URL))
                trackingOrigins.put(origin, URL);
        }
    }

    @Override
    public boolean beforeCreate(ImmutableEnvironment c) {

        boolean checkResult = true;

        // recalculate prepaid origins*days without saving to state
        calculatePrepaidOriginDays(false);

        int paidU = getPaidU();
        if(paidU == 0) {
            if(getPaidU(true) > 0) {
                addError(Errors.FAILED_CHECK, "Test payment is not allowed for follower contracts");
            }
            checkResult = false;
        } else if(paidU < getMinPayment()) {
            addError(Errors.FAILED_CHECK, "Payment for follower contract is below minimum level of " + getMinPayment() + "U");
            checkResult = false;
        }

        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Follower contract hasn't valid payment");
            return checkResult;
        }

        // check that payment was not hacked
        checkResult = prepaidOriginDays == getStateData().getInt(PREPAID_OD_FIELD_NAME, 0);
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_OD_FIELD_NAME + "] value. " +
                    "Should be sum of early paid U and paid U by current revision.");
            return checkResult;
        }

        // and call common follower check
        checkResult = additionallyFollowerCheck(c);

        return checkResult;
    }

    @Override
    public boolean beforeUpdate(ImmutableEnvironment c) {
        boolean checkResult = false;

        // recalculate prepaid origins*days without saving to state
        calculatePrepaidOriginDays(false);

        // check that payment was not hacked
        checkResult = prepaidOriginDays == getStateData().getInt(PREPAID_OD_FIELD_NAME, 0);
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_OD_FIELD_NAME + "] value. " +
                    "Should be sum of early paid U and paid U by current revision.");
            return checkResult;
        }

        // and call common follower check
        checkResult = additionallyFollowerCheck(c);

        return checkResult;
    }

    @Override
    public boolean beforeRevoke(ImmutableEnvironment c) {
        return additionallyFollowerCheck(c);
    }

    private boolean additionallyFollowerCheck(ImmutableEnvironment ime) {

        boolean checkResult = false;

        // check slot environment
        checkResult = ime != null;
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Environment should be not null");
            return checkResult;
        }

        // check that slot has known and valid type of smart contract
        checkResult = getExtendedType().equals(SmartContractType.FOLLOWER1.name());
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "definition.extended_type", "illegal value, should be " + SmartContractType.FOLLOWER1.name() + " instead " + getExtendedType());
            return checkResult;
        }

        // check for tracking origins existing
        checkResult = trackingOrigins.size() > 0;
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Tracking origin is missed");
            return checkResult;
        }

        // check for any tracking origin contains callbacks data
        checkResult = true;
        for (String URL: trackingOrigins.values())
            if (!callbackKeys.containsKey(URL))
                checkResult = false;
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, "Callback key for tracking origin is missed");
            return checkResult;
        }

        return checkResult;
    }

    @Override
    public @Nullable Binder onCreated(MutableEnvironment me) {
        updateSubscriptions(me);

        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public Binder onUpdated(MutableEnvironment me) {
        updateSubscriptions(me);

        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public void onRevoked(ImmutableEnvironment ime) {
    }

    static {
        Config.forceInit(FollowerContract.class);
        DefaultBiMapper.registerClass(FollowerContract.class);
    }
}
