package com.icodici.universa.contract.services;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Parcel;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.permissions.ChangeOwnerPermission;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.RevokePermission;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node2.Config;
import com.icodici.universa.node2.network.Client;
import com.icodici.universa.node2.network.ClientError;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@BiType(name = "UnsContract")
public class UnsContract extends NSmartContract {
    public static final String NAMES_FIELD_NAME = "names";
    public static final String ENTRIES_FIELD_NAME = "entries";

    public static final String PREPAID_ND_FIELD_NAME = "prepaid_ND";

    private static final String REFERENCE_CONDITION_PREFIX = "ref.state.origin==";
    private static final String REFERENCE_CONDITION_LEFT = "ref.state.origin";
    private static final int REFERENCE_CONDITION_OPERATOR = 7;       // EQUAL

    private List<UnsName> storedNames = new ArrayList<>();
    private List<UnsRecord> storedRecords = new ArrayList<>();

    private double prepaidNameDays = 0;

    private Map<HashId,Contract> originContracts = new HashMap<>();
    private int paidU;


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
    public UnsContract(byte[] sealed, @NonNull TransactionPack pack) throws IOException {
        super(sealed, pack);

        deserializeForUns(BossBiMapper.newDeserializer());
    }

    public UnsContract() {
        super();
    }

    /**
     * Create a default empty new contract using a provided key as issuer and owner and sealer.
     * <p>
     * This constructor adds key as sealing signature so it is ready to {@link #seal()} just after construction, thought
     * it is necessary to put real data to it first. It is allowed to change owner, expiration and data fields after
     * creation (but before sealing).
     *
     * @param key is {@link PrivateKey} for creating roles "issuer", "owner", "creator" and sign contract
     */
    public UnsContract(PrivateKey key) {
        super(key);

        RevokePermission revokePerm1 = new RevokePermission(new RoleLink("@owner", this,"owner"));
        addPermission(revokePerm1);

        RevokePermission revokePerm2 = new RevokePermission(new RoleLink("@issuer", this,"issuer"));
        addPermission(revokePerm2);

        addUnsSpecific();
    }

    /**
     * Initialize UnsContract internal data structure with specific UNS1 parameters.
     */
    public void addUnsSpecific() {
        if(getDefinition().getExtendedType() == null || !getDefinition().getExtendedType().equals(SmartContractType.UNS1.name()))
            getDefinition().setExtendedType(SmartContractType.UNS1.name());

        RoleLink ownerLink = new RoleLink("owner_link",this, "owner");
        HashMap<String, Object> fieldsMap = new HashMap<>();
        fieldsMap.put("action", null);
        fieldsMap.put("/expires_at", null);
        fieldsMap.put("/references", null);
        fieldsMap.put(NAMES_FIELD_NAME, null);
        fieldsMap.put(ENTRIES_FIELD_NAME, null);
        fieldsMap.put(PAID_U_FIELD_NAME, null);
        fieldsMap.put(PREPAID_ND_FIELD_NAME, null);
        Binder modifyDataParams = Binder.of("fields", fieldsMap);
        ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
        addPermission(modifyDataPermission);

        RevokePermission revokePermission = new RevokePermission(ownerLink);
        addPermission(revokePermission);
    }

    @Deprecated
    public double getPrepaidNameDays() {
        return prepaidNameDays;
    }

    public BigDecimal getPrepaidNamesDays() {
        return new BigDecimal(prepaidNameDays);
    }

    private int getStoredUnitsCount() {
        return storedNames.size();
        //TODO: return max(storedNames.size(),storedDataSize/FREE_KILO_PER_NAME)
    }

    private double calculatePrepaidNameDays(boolean withSaveToState) {

        paidU = getPaidU();

        UnsContract parentContract = (UnsContract) getRevokingItem(getParent());
        double prepaidNameDaysLeft = 0;
        if(parentContract != null) {
            prepaidNameDaysLeft = parentContract.getStateData().getDouble(PREPAID_ND_FIELD_NAME);
            prepaidNameDaysLeft -= parentContract.getStoredUnitsCount()*((double)(getCreatedAt().toEpochSecond()-parentContract.getCreatedAt().toEpochSecond()))/(3600*24);
        }

        prepaidNameDays = prepaidNameDaysLeft + paidU * getRate().doubleValue();

        if(withSaveToState) {
            getStateData().set(PAID_U_FIELD_NAME, paidU);
            getStateData().set(PREPAID_ND_FIELD_NAME, prepaidNameDays);
        }

        return prepaidNameDays;
    }

    @Override
    public byte[] seal() {
        saveNamesAndRecordsToState();
        saveOriginReferencesToState();
        calculatePrepaidNameDays(true);

        byte[] res = super.seal();

        originContracts.values().forEach(oc->getTransactionPack().addReferencedItem(oc));
        return res;
    }

    private boolean isOriginCondition(Object condition, HashId origin) {

        if (condition instanceof String)
            return condition.equals(REFERENCE_CONDITION_PREFIX + origin.toBase64String());
        else if (((Binder) condition).containsKey("operator")) {
            int operator = ((Binder) condition).getIntOrThrow("operator");

            if (operator == REFERENCE_CONDITION_OPERATOR) {
                String leftOperand = ((Binder) condition).getString("leftOperand", null);

                if ((leftOperand != null) && leftOperand.equals(REFERENCE_CONDITION_LEFT)) {
                    String rightOperand = ((Binder) condition).getString("rightOperand", null);

                    if ((rightOperand != null) && rightOperand.equals(origin.toBase64String()))
                        return true;
                }
            }
        }

        return false;
    }

    private void saveOriginReferencesToState() {

        Set<HashId> newOrigins = new HashSet<>(getOrigins());
        UnsContract parentContract = (UnsContract) getRevokingItem(getParent());
        if(parentContract != null) {
            newOrigins.removeAll(parentContract.getOrigins());
        }

        Set<Reference> refsToRemove = new HashSet<>();

        getReferences().values().forEach(ref -> {
            ArrayList conditions = ref.getConditions().getArray(Reference.conditionsModeType.all_of.name());
            conditions.forEach(condition -> {
                HashId o = null;
                if (condition instanceof String) {
                    if (((String) condition).startsWith(REFERENCE_CONDITION_PREFIX))
                        o = HashId.withDigest(((String) condition).substring(REFERENCE_CONDITION_PREFIX.length()));
                } else if (((Binder) condition).containsKey("operator")) {
                    int operator = ((Binder) condition).getIntOrThrow("operator");

                    if (operator == REFERENCE_CONDITION_OPERATOR) {
                        String leftOperand = ((Binder) condition).getString("leftOperand", null);

                        if ((leftOperand != null) && leftOperand.equals(REFERENCE_CONDITION_LEFT)) {
                            String rightOperand = ((Binder) condition).getString("rightOperand", null);

                            if (rightOperand != null)
                                o = HashId.withDigest(rightOperand);
                        }
                    }
                }

                if ((o != null) && (!newOrigins.contains(o)))
                    refsToRemove.add(ref);
            });
        });

        refsToRemove.forEach(ref -> removeReference(ref));

        newOrigins.forEach( origin -> {
            if(!isOriginReferenceExists(origin))
                addOriginReference(origin);
        });
    }


    private void saveNamesAndRecordsToState() {
        getStateData().put(NAMES_FIELD_NAME,storedNames);
        getStateData().put(ENTRIES_FIELD_NAME,storedRecords);
    }


    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        deserializeForUns(deserializer);
    }

    // this method should be only at the deserialize
    private void deserializeForUns(BiDeserializer deserializer) {

        storedNames = deserializer.deserialize(getStateData().getList(NAMES_FIELD_NAME, null));
        storedRecords = deserializer.deserialize(getStateData().getList(ENTRIES_FIELD_NAME, null));

        paidU = getStateData().getInt(PAID_U_FIELD_NAME, 0);
        prepaidNameDays = getStateData().getDouble(PREPAID_ND_FIELD_NAME);
    }

    protected UnsContract initializeWithDsl(Binder root) throws EncryptionError {
        super.initializeWithDsl(root);
        ArrayList<?> arrayNames = root.getBinder("state").getBinder("data").getArray(NAMES_FIELD_NAME);
        for (Object name: arrayNames) {
            Binder binder;
            if (name.getClass().getName().endsWith("Binder"))
                binder = (Binder) name;
            else
                binder = new Binder((Map) name);

            UnsName unsName = new UnsName().initializeWithDsl(binder);
            storedNames.add(unsName);
        }


        ArrayList<?> arrayRecords = root.getBinder("state").getBinder("data").getArray(ENTRIES_FIELD_NAME);
        for (Object record: arrayRecords) {
            Binder binder;
            if (record.getClass().getName().endsWith("Binder"))
                binder = (Binder) record;
            else
                binder = new Binder((Map) record);

            UnsRecord unsName = new UnsRecord().initializeWithDsl(binder);
            storedRecords.add(unsName);
        }
        return this;
    }

    /**
     * Method creates {@link UnsContract} contract from dsl file where contract is described.
     * @param fileName is path to dsl file with yaml structure of data for contract.
     */
    public static UnsContract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new UnsContract().initializeWithDsl(binder);
        }
    }

    @Override
    public boolean beforeCreate(ImmutableEnvironment c) {

        if(!checkPaymentAndRelatedFields(false)) {
            return false;
        }

        if(!additionallyUnsCheck(c)) {
            return false;
        }

        return true;
    }

    @Override
    public boolean beforeUpdate(ImmutableEnvironment c) {

        if(!checkPaymentAndRelatedFields(true)) {
            return false;
        }

        if(!additionallyUnsCheck(c)) {
            return false;
        }

        return true;
    }

    private boolean checkPaymentAndRelatedFields(boolean allowNoPayment) {
        boolean paymentCheck = true;

        calculatePrepaidNameDays(false);

        if(paidU == 0) {
            if(!allowNoPayment) {
                if (getPaidU(true) > 0) {
                    addError(Errors.FAILED_CHECK, "Test payment is not allowed for storing storing names");
                }
                paymentCheck = false;
            }
        } else if(paidU < getMinPayment()) {
            addError(Errors.FAILED_CHECK, "Payment for UNS contract is below minimum level of " + getMinPayment() + "U");
            paymentCheck = false;
        }

        if (!paymentCheck) {
            addError(Errors.FAILED_CHECK, "UNS contract hasn't valid payment");
            return false;
        }

        if (paidU != getStateData().getInt(PAID_U_FIELD_NAME, 0)) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PAID_U_FIELD_NAME + "] value. " +
                    "Should be amount of U paid by current paying parcel.");
            return false;
        }


        if (prepaidNameDays != getStateData().getDouble(PREPAID_ND_FIELD_NAME)) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_ND_FIELD_NAME + "] value. " +
                    "Should be sum of early prepaid name days left and prepaid name days of current revision. " +
                    "Make sure contract was prepared using correct UNS1 rate.");
            return false;
        }

        return true;
    }

    @Override
    public boolean beforeRevoke(ImmutableEnvironment c) {
        return true;
    }

    private boolean additionallyUnsCheck(ImmutableEnvironment ime) {
        if (ime == null) {
            addError(Errors.FAILED_CHECK, "Environment should be not null");
            return false;
        }

        Map<String, UnsName> newNames = storedNames.stream().collect(Collectors.toMap(UnsName::getUnsName, un -> un));

        
        try {

            ime.nameRecords().forEach(nameRecord -> {
                UnsName unsName = newNames.get(nameRecord.getName());
                if(unsName != null && unsName.equalsTo(nameRecord)) {
                    newNames.remove(nameRecord.getName());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        Set<UnsRecord> newRecords = storedRecords.stream().collect(Collectors.toSet());
        ime.nameRecordEntries().forEach(nre -> {
            Optional<UnsRecord> ur = newRecords.stream().filter(unsRecord -> unsRecord.equalsTo(nre)).findAny();
            if(ur.isPresent()) {
                newRecords.remove(ur.get());
            }
        });


        if (!getExtendedType().equals(SmartContractType.UNS1.name())) {
            addError(Errors.FAILED_CHECK, "definition.extended_type", "illegal value, should be " + SmartContractType.UNS1.name() + " instead " + getExtendedType());
            return false;
        }


        if (storedNames.size() == 0) {
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Names are missing");
            return false;
        }

        if (!newRecords.stream().allMatch(unsRecord -> {
            if(unsRecord.getOrigin() != null) {
                if(!unsRecord.getAddresses().isEmpty()) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "record referencing to origin AND addresses found. Should be either origin or addresses or data");
                    return false;
                }

                if(unsRecord.getData() != null) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "record referencing to origin AND data found. Should be either origin or addresses or data");
                    return false;
                }

                //check reference exists in contract (ensures that matching contract was checked by system for being approved)
                if(!isOriginReferenceExists(unsRecord.getOrigin())) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "record referencing to origin " + unsRecord.getOrigin().toString() + " but no corresponding reference is found");
                    return false;
                }

                List<Contract> matchingContracts = getTransactionPack().getReferencedItems().values().stream()
                        .filter(contract -> contract.getId().equals(unsRecord.getOrigin())
                                || contract.getOrigin() != null && contract.getOrigin().equals(unsRecord.getOrigin()))
                        .collect(Collectors.toList());

                if(matchingContracts.isEmpty()) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "record referencing to origin " + unsRecord.getOrigin().toString() + " but no corresponding referenced contract is found");
                    return false;
                }

                Contract contract = matchingContracts.get(0);
                if(!contract.getRole("issuer").isAllowedForKeys(getEffectiveKeys())) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "record referencing to origin " + unsRecord.getOrigin().toString() + ". UNS1 contract should be also signed by this contract issuer key.");
                    return false;
                }

                return true;
            }

            if(!unsRecord.getAddresses().isEmpty()) {

                if(unsRecord.getData() != null) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "record referencing to addresses AND data found. Should be either origin or addresses or data");
                    return false;
                }


                if (unsRecord.getAddresses().size() > 2)
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "Addresses list should not be contains more 2 addresses");

                if ((unsRecord.getAddresses().size() == 2) && unsRecord.getAddresses().get(0).isLong() == unsRecord.getAddresses().get(1).isLong())
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "Addresses list may only contain one short and one long addresses");

                if (!unsRecord.getAddresses().stream().allMatch(keyAddress -> getEffectiveKeys().stream().anyMatch(key -> keyAddress.isMatchingKey(key)))) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "Address used is missing corresponding key UNS contract signed with.");
                    return false;
                }
                return true;
            }

            if(unsRecord.getData() == null) {
                addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "Record is empty. Should reference to either origin or addresses or data");
                return false;
            }

            return true;
        })) {
            return false;
        }


        //only check name service signature is there are new/changed name->reduced

        if(!newNames.isEmpty() && !getAdditionalKeysAddressesToSignWith().stream().allMatch(ka -> getEffectiveKeys().stream().anyMatch(ek -> ka.isMatchingKey(ek)))) {
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Authorized name service signature is missing");
            return false;
        }

        List<String> reducedNamesToCkeck = getReducedNamesToCheck();
        List<HashId> originsToCheck = getOriginsToCheck();
        List<String> addressesToCheck = getAddressesToCheck();

        List<ErrorRecord> allocationErrors = ime.tryAllocate(reducedNamesToCkeck,originsToCheck,addressesToCheck);
        if (allocationErrors.size() > 0) {
            for (ErrorRecord errorRecord : allocationErrors)
                addError(errorRecord);
            return false;
        }

        return true;
    }


    private List<String> getReducedNamesToCheck() {
        Set<String> reducedNames = new HashSet<>();
        for (UnsName unsName : storedNames)
            reducedNames.add(unsName.getUnsReducedName());
        for (Approvable revoked : getRevokingItems())
            removeRevokedNames(revoked, reducedNames);
        return new ArrayList<>(reducedNames);
    }

    private void removeRevokedNames(Approvable approvable, Set<String> set) {
        if (approvable instanceof UnsContract) {
            UnsContract unsContract = (UnsContract) approvable;
            for (UnsName unsName : unsContract.storedNames)
                set.remove(unsName.getUnsReducedName());
        }
        for (Approvable revoked : approvable.getRevokingItems())
            removeRevokedNames(revoked, set);
    }

    private List<HashId> getOriginsToCheck() {
        Set<HashId> origins = new HashSet<>();
        for (UnsRecord unsRecord : storedRecords)
            if (unsRecord.getOrigin() != null)
                origins.add(unsRecord.getOrigin());
        for (Approvable revoked : getRevokingItems())
            removeRevokedOrigins(revoked, origins);
        return new ArrayList<>(origins);
    }

    private void removeRevokedOrigins(Approvable approvable, Set<HashId> set) {
        if (approvable instanceof UnsContract) {
            UnsContract unsContract = (UnsContract) approvable;
            for (UnsRecord unsRecord : unsContract.storedRecords)
                if (unsRecord.getOrigin() != null)
                    set.remove(unsRecord.getOrigin());
        }
        for (Approvable revoked : approvable.getRevokingItems())
            removeRevokedOrigins(revoked, set);
    }

    private List<String> getAddressesToCheck() {
        Set<String> addresses = new HashSet<>();
        for (UnsRecord unsRecord : storedRecords)
            for (KeyAddress keyAddress : unsRecord.getAddresses())
                addresses.add(keyAddress.toString());
        for (Approvable revoked : getRevokingItems())
            removeRevokedAddresses(revoked, addresses);
        return new ArrayList<>(addresses);
    }

    private void removeRevokedAddresses(Approvable approvable, Set<String> set) {
        if (approvable instanceof UnsContract) {
            UnsContract unsContract = (UnsContract) approvable;
            for (UnsRecord unsRecord : unsContract.storedRecords)
                    for (KeyAddress keyAddress : unsRecord.getAddresses())
                        set.remove(keyAddress.toString());
        }
        for (Approvable revoked : approvable.getRevokingItems())
            removeRevokedAddresses(revoked, set);
    }



    private boolean isOriginReferenceExists(HashId origin) {
        return getReferences().values().stream().anyMatch(ref ->
                ref.getConditions().getArray(Reference.conditionsModeType.all_of.name()).stream().anyMatch(cond ->
                        isOriginCondition(cond, origin)));
    }

    @Override
    public @Nullable Binder onCreated(MutableEnvironment me) {
        calculatePrepaidNameDays(false);
        ZonedDateTime expiresAt = calcExpiresAt();
        storedNames.forEach(sn -> me.createNameRecord(sn,expiresAt));
        storedRecords.forEach(sr ->me.createNameRecordEntry(sr));
        return Binder.fromKeysValues("status", "ok");
    }

    private ZonedDateTime calcExpiresAt() {
        // get number of entries
        int entries = getStoredUnitsCount();

        double days = prepaidNameDays / entries;
        long seconds = (long) (days * 24 * 3600);

        return getCreatedAt().plusSeconds(seconds);
    }

    @Override
    public Binder onUpdated(MutableEnvironment me) {
        calculatePrepaidNameDays(false);

        ZonedDateTime expiresAt = calcExpiresAt();

        Map<String, UnsName> newNames = storedNames.stream().collect(Collectors.toMap(UnsName::getUnsName, un -> un));

        me.nameRecords().forEach(nameRecord -> {
            UnsName unsName = newNames.get(nameRecord.getName());
            if(unsName != null && unsName.equalsTo(nameRecord)) {
                me.setNameRecordExpiresAt(nameRecord,expiresAt);
                newNames.remove(nameRecord.getName());
            } else
                me.destroyNameRecord(nameRecord);
        });

        newNames.values().forEach(sn -> me.createNameRecord(sn,expiresAt));

        Set<UnsRecord> newRecords = storedRecords.stream().collect(Collectors.toSet());
        me.nameRecordEntries().forEach(nre -> {
            Optional<UnsRecord> ur = newRecords.stream().filter(unsRecord -> unsRecord.equalsTo(nre)).findAny();
            if(ur.isPresent()) {
                newRecords.remove(ur.get());
            } else {
                me.destroyNameRecordEntry(nre);
            }
        });

        newRecords.forEach(sr -> me.createNameRecordEntry(sr));

        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public void onRevoked(ImmutableEnvironment ime) {

    }

    @Override
    public Binder getExtraResultForApprove() {
        return Binder.of("expires_at", calcExpiresAt().toEpochSecond());
    }

    private void addOriginReference(HashId origin) {
        Reference ref = new Reference(this);
        ref.type = Reference.TYPE_EXISTING_STATE;
        ref.setName(origin.toString());
        List<Object> conditionsList = new ArrayList<>();
        conditionsList.add(REFERENCE_CONDITION_PREFIX + "\"" + origin.toBase64String() + "\"");
        Binder conditions = Binder.of(Reference.conditionsModeType.all_of.name(),conditionsList);
        ref.setConditions(conditions);
        if(originContracts.containsKey(origin))
            ref.addMatchingItem(originContracts.get(origin));
        addReference(ref);

    }


    public void addName(String name,String reducedName,String description) {
        Optional<UnsName> exists = storedNames.stream().filter(unsName -> unsName.getUnsReducedName().equals(reducedName)).findAny();
        if(exists.isPresent()) {
            throw new IllegalArgumentException("Name '" + name + "'/'" + reducedName +"' already exists");
        }
        UnsName un = new UnsName(name, description);
        un.setUnsReducedName(reducedName);
        storedNames.add(un);
    }

    public boolean removeName(String name) {
        return storedNames.removeIf(unsName -> unsName.getUnsName().equals(name));
    }

    public Set<String>  getNames() {
        return storedNames.stream().map(unsName -> unsName.getUnsName()).collect(Collectors.toSet());
    }

    public void addOrigin(Contract contract) {
        HashId origin = contract.getOrigin();
        addOrigin(origin);
        originContracts.put(contract.getOrigin(),contract);
    }

    public void addOrigin(HashId origin) {
        Optional<UnsRecord> exists = storedRecords.stream().filter(unsRecord -> unsRecord.getOrigin() != null && unsRecord.getOrigin().equals(origin)).findAny();
        if(exists.isPresent()) {
            throw new IllegalArgumentException("Origin '" + origin + "' already exists");
        }
        storedRecords.add(new UnsRecord(origin));
    }

    public Set<HashId> getOrigins() {
        return storedRecords.stream().map(unsRecord -> unsRecord.getOrigin()).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public boolean removeOrigin(HashId origin) {
        return storedRecords.removeIf(unsRecord -> unsRecord.getOrigin() != null && unsRecord.getOrigin().equals(origin));
    }

    public void addKey(PublicKey publicKey) {
        Set<KeyAddress> addresses = getAddresses();
        KeyAddress shortAddress = publicKey.getShortAddress();
        KeyAddress longAddress = publicKey.getLongAddress();
        if(addresses.containsAll(Do.listOf(longAddress,shortAddress))) {
            throw new IllegalArgumentException("Key addresses '" + publicKey.getLongAddress() + "'/'" + publicKey.getShortAddress()+ "' already exist");
        }

        Optional<UnsRecord> record = storedRecords.stream().filter(unsRecord -> unsRecord.getAddresses().contains(shortAddress) || unsRecord.getAddresses().contains(longAddress)).findAny();
        if(record.isPresent()) {
            storedRecords.remove(record.get());
        }

        storedRecords.add(new UnsRecord(publicKey));

    }

    public void addAddress(KeyAddress keyAddress) {
        Set<KeyAddress> addresses = getAddresses();
        if(addresses.contains(keyAddress)) {
            throw new IllegalArgumentException("Key address '" + keyAddress +  "' already exist");
        }

        storedRecords.add(new UnsRecord(keyAddress));

    }

    public Set<KeyAddress> getAddresses() {
        Set<KeyAddress> result = new HashSet<>();
        storedRecords.forEach(unsRecord -> result.addAll(unsRecord.getAddresses()));
        return result;
    }

    public boolean removeAddress(KeyAddress keyAddress) {
        return storedRecords.removeIf(unsRecord -> unsRecord.getAddresses().contains(keyAddress));
    }

    public boolean removeKey(PublicKey publicKey) {
        return storedRecords.removeIf(unsRecord -> unsRecord.getAddresses().contains(publicKey.getShortAddress()) || unsRecord.getAddresses().contains(publicKey.getLongAddress()));
    }




    public void addData(Binder data) {
        storedRecords.add(new UnsRecord(data));
    }

    public List<Binder> getAllData() {
        return storedRecords.stream().map(unsRecord -> unsRecord.getData()).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public boolean removeData(Binder data) {
        return storedRecords.removeIf(unsRecord -> unsRecord.getData() != null && unsRecord.getData().equals(data));
    }

    //4 ключа больших + 4 кб тескта минимальный платеж за год
    //1 - 1 имя 4 + записи

    static {
        Config.forceInit(UnsRecord.class);
        Config.forceInit(UnsName.class);
        Config.forceInit(UnsContract.class);
        DefaultBiMapper.registerClass(UnsRecord.class);
        DefaultBiMapper.registerClass(UnsName.class);
        DefaultBiMapper.registerClass(UnsContract.class);
    }


    public UnsName getName(String name) {
        Optional<UnsName> k = storedNames.stream().filter(unsName -> unsName.getUnsName().equals(name)).findAny();
        return k.isPresent() ? k.get() : null;
    }



    public int getPayingAmount(ZonedDateTime unsExpirationDate ) {
        double nameDaysShouldBeValidFor = getStoredUnitsCount()*(unsExpirationDate.toEpochSecond() - getCreatedAt().toEpochSecond())/(3600.0*24);

        UnsContract parentContract = (UnsContract) getRevokingItem(getParent());
        double prepaidNameDaysLeft = 0;
        if(parentContract != null) {
            prepaidNameDaysLeft = parentContract.getStateData().getDouble(PREPAID_ND_FIELD_NAME);
            prepaidNameDaysLeft -= parentContract.getStoredUnitsCount()*((double)(getCreatedAt().toEpochSecond()-parentContract.getCreatedAt().toEpochSecond()))/(3600*24);
        }

        nameDaysShouldBeValidFor -= prepaidNameDaysLeft;
        int amount = (int) Math.ceil(nameDaysShouldBeValidFor / getRate().doubleValue());
        if(amount < getMinPayment()) {
            amount = getMinPayment();
        }
        return amount;
    }

    public Parcel createRegistrationParcel(ZonedDateTime unsExpirationDate, Contract uContract, Collection<PrivateKey> uKeys, Collection<PrivateKey> keysToSignUnsWith) {
        int amount = getPayingAmount(unsExpirationDate);
        Parcel parcel = Parcel.of(this, uContract, uKeys);
        parcel.addPayingAmount(amount,uKeys,keysToSignUnsWith);
        return parcel;
    }
}

