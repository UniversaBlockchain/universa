package com.icodici.universa.contract.services;

import com.icodici.crypto.EncryptionError;
import com.icodici.crypto.KeyAddress;
import com.icodici.crypto.PrivateKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.contract.TransactionPack;
import com.icodici.universa.contract.permissions.ModifyDataPermission;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node.models.NameEntryModel;
import com.icodici.universa.node.models.NameRecordModel;
import com.icodici.universa.node2.Config;
import net.sergeych.biserializer.*;
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
import java.util.stream.Collectors;

@BiType(name = "UnsContract")
public class UnsContract extends NSmartContract {
    public static final String NAMES_FIELD_NAME = "names";
    public static final String ENTRIES_FIELD_NAME = "entries";

    public static final String PREPAID_ND_FIELD_NAME = "prepaid_ND";
    public static final String PREPAID_ND_FROM_TIME_FIELD_NAME = "prepaid_ND_from";
    public static final String STORED_ENTRIES_FIELD_NAME = "stored_entries";
    public static final String SPENT_ND_FIELD_NAME = "spent_ND";
    public static final String SPENT_ND_TIME_FIELD_NAME = "spent_ND_time";
    private static final String REFERENCE_CONDITION_PREFIX = "ref.state.origin==";

    private List<UnsName> storedNames = new ArrayList<>();
    private int paidU = 0;
    private double prepaidNamesForDays = 0;
    private long storedEarlyEntries = 0;
    private double spentEarlyNDs = 0;
    private double spentNDs = 0;
    private ZonedDateTime spentEarlyNDsTime = null;
    private ZonedDateTime prepaidFrom = null;
    private ZonedDateTime spentNDsTime = null;
    private Map<HashId,Contract> originContracts = new HashMap<>();


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
     * Create a default empty new contract using a provided key as issuer and owner and sealer. Default expiration is
     * set to 5 years.
     * <p>
     * This constructor adds key as sealing signature so it is ready to {@link #seal()} just after construction, thought
     * it is necessary to put real data to it first. It is allowed to change owner, expiration and data fields after
     * creation (but before sealing).
     *
     * @param key is {@link PrivateKey} for creating roles "issuer", "owner", "creator" and sign contract
     */
    public UnsContract(PrivateKey key) {
        super(key);

        addUnsSpecific();
    }

    public void addUnsSpecific() {
        if(getDefinition().getExtendedType() == null || !getDefinition().getExtendedType().equals(SmartContractType.UNS1.name()))
            getDefinition().setExtendedType(SmartContractType.UNS1.name());

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
            fieldsMap.put("/references", null);
            fieldsMap.put(NAMES_FIELD_NAME, null);
            fieldsMap.put(PREPAID_ND_FIELD_NAME, null);
            fieldsMap.put(PREPAID_ND_FROM_TIME_FIELD_NAME, null);
            fieldsMap.put(STORED_ENTRIES_FIELD_NAME, null);
            fieldsMap.put(SPENT_ND_FIELD_NAME, null);
            fieldsMap.put(SPENT_ND_TIME_FIELD_NAME, null);
            Binder modifyDataParams = Binder.of("fields", fieldsMap);
            ModifyDataPermission modifyDataPermission = new ModifyDataPermission(ownerLink, modifyDataParams);
            addPermission(modifyDataPermission);
        }
    }

    public double getPrepaidNamesForDays() {
        return prepaidNamesForDays;
    }

    private double calculatePrepaidNamesForDays(boolean withSaveToState) {

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

        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochSecond(ZonedDateTime.now().toEpochSecond()), ZoneId.systemDefault());
        double wasPrepaidNamesForDays;
        long wasPrepaidFrom = now.toEpochSecond();
        long spentEarlyNDsTimeSecs = now.toEpochSecond();
        Contract parentContract = getRevokingItem(getParent());
        if(parentContract != null) {
            wasPrepaidNamesForDays = parentContract.getStateData().getDouble(PREPAID_ND_FIELD_NAME);
            wasPrepaidFrom = parentContract.getStateData().getLong(PREPAID_ND_FROM_TIME_FIELD_NAME, now.toEpochSecond());
            storedEarlyEntries = parentContract.getStateData().getLong(STORED_ENTRIES_FIELD_NAME, 0);
            spentEarlyNDs = parentContract.getStateData().getDouble(SPENT_ND_FIELD_NAME);
            spentEarlyNDsTimeSecs = parentContract.getStateData().getLong(SPENT_ND_TIME_FIELD_NAME, now.toEpochSecond());
        } else {
            wasPrepaidNamesForDays = 0;
        }

        spentEarlyNDsTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(spentEarlyNDsTimeSecs), ZoneId.systemDefault());
        prepaidFrom = ZonedDateTime.ofInstant(Instant.ofEpochSecond(wasPrepaidFrom), ZoneId.systemDefault());
        prepaidNamesForDays = wasPrepaidNamesForDays + paidU * Config.namesAndDaysPerU;

        spentNDsTime = now;

        if(withSaveToState) {
            getStateData().set(PREPAID_ND_FIELD_NAME, prepaidNamesForDays);
            if(getRevision() == 1)
                getStateData().set(PREPAID_ND_FROM_TIME_FIELD_NAME, now.toEpochSecond());

            // calculate num of entries
            int storingEntries = 0;
            for (Object name: storedNames) {
                if (name.getClass().getName().endsWith("UnsName"))
                    storingEntries += ((UnsName) name).getRecordsCount();
                else {
                    Binder binder;
                    if (name.getClass().getName().endsWith("Binder"))
                        binder = (Binder) name;
                    else
                        binder = new Binder((Map) name);

                    ArrayList<?> entries = binder.getArray(ENTRIES_FIELD_NAME);
                    if (entries != null)
                        storingEntries += entries.size();
                }
            }
            getStateData().set(STORED_ENTRIES_FIELD_NAME, storingEntries);

            long spentSeconds = (spentNDsTime.toEpochSecond() - spentEarlyNDsTime.toEpochSecond());
            double spentDays = (double) spentSeconds / (3600 * 24);
            spentNDs = spentEarlyNDs + spentDays * (storedEarlyEntries / 1024);

            getStateData().set(SPENT_ND_FIELD_NAME, spentNDs);
            getStateData().set(SPENT_ND_TIME_FIELD_NAME, spentNDsTime.toEpochSecond());
        }

        return prepaidNamesForDays;
    }

    @Override
    public byte[] seal() {
        saveNamesToState();
        saveOriginReferencesToState();
        calculatePrepaidNamesForDays(true);

        return super.seal();
    }

    private void saveOriginReferencesToState() {
        Set<HashId> origins = new HashSet<>();

        storedNames.forEach(sn->sn.getUnsRecords().forEach(unsRecord -> {
            if(unsRecord.getOrigin()!=null) {
                origins.add(unsRecord.getOrigin());
            }
        }));

        Set<Reference> refsToRemove = new HashSet<>();

        getReferences().values().forEach(ref -> {
            ArrayList conditions = ref.getConditions().getArray(Reference.conditionsModeType.all_of.name());
            conditions.forEach(condition -> {
                if(condition instanceof String && ((String) condition).startsWith(REFERENCE_CONDITION_PREFIX)) {
                    HashId o = HashId.withDigest(((String) condition).substring(REFERENCE_CONDITION_PREFIX.length()));
                    if(!origins.contains(o)) {
                        refsToRemove.add(ref);
                    }
                }
            });
        });

        refsToRemove.forEach(ref -> removeReference(ref));

        origins.forEach( origin -> {
            if(!isOriginReferenceExists(origin)) {
                addOriginReference(origin);
            } else {
                Reference reference = getReferences().values().stream().filter(ref ->
                        ref.getConditions().getArray(Reference.conditionsModeType.all_of.name()).stream().anyMatch(cond ->
                                cond.equals(REFERENCE_CONDITION_PREFIX+origin.toBase64String()))).collect(Collectors.toList()).get(0);
                if(reference.matchingItems.isEmpty() && originContracts.containsKey(origin))
                    reference.addMatchingItem(originContracts.get(origin));
            }
        });
    }


    private void saveNamesToState() {
        getStateData().put(NAMES_FIELD_NAME,storedNames);
    }


    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);

        deserializeForUns(deserializer);
    }

    // this method should be only at the deserialize
    private void deserializeForUns(BiDeserializer deserializer) {

        storedNames = deserializer.deserialize(getStateData().getList(NAMES_FIELD_NAME, null));

        prepaidNamesForDays = getStateData().getInt(PREPAID_ND_FIELD_NAME, 0);

        long prepaidFromSeconds = getStateData().getLong(PREPAID_ND_FROM_TIME_FIELD_NAME, 0);
        prepaidFrom = ZonedDateTime.ofInstant(Instant.ofEpochSecond(prepaidFromSeconds), ZoneId.systemDefault());
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
        return this;
    }

    public static UnsContract fromDslFile(String fileName) throws IOException {
        Yaml yaml = new Yaml();
        try (FileReader r = new FileReader(fileName)) {
            Binder binder = Binder.from(DefaultBiMapper.deserialize((Map) yaml.load(r)));
            return new UnsContract().initializeWithDsl(binder);
        }
    }

    @Override
    public boolean beforeCreate(ImmutableEnvironment c) {

        boolean checkResult = false;

        calculatePrepaidNamesForDays(false);

        boolean hasPayment = false;
        for (Contract nc : getNew()) {
            if (nc.isU(nodeConfig.getTransactionUnitsIssuerKeys(), nodeConfig.getTUIssuerName())) {
                hasPayment = true;

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
                        if (calculatedPayment <= 0)
                            isTestPayment = false;
                    } else
                        isTestPayment = false;

                    if (isTestPayment) {
                        hasPayment = false;
                        addError(Errors.FAILED_CHECK, "Test payment is not allowed for storing names");
                    }

                    if (paidU < nodeConfig.getMinUnsPayment()) {
                        hasPayment = false;
                        addError(Errors.FAILED_CHECK, "Payment for UNS contract is below minimum level of " + nodeConfig.getMinUnsPayment() + "U");
                    }
                } else {
                    hasPayment = false;
                    addError(Errors.FAILED_CHECK, "Payment contract is missing parent contract");
                }
            }
        }

        checkResult = hasPayment;
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, "UNS contract hasn't valid payment");
            return checkResult;
        }

        checkResult = prepaidNamesForDays == getStateData().getDouble(PREPAID_ND_FIELD_NAME);
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_ND_FIELD_NAME + "] value. " +
                    "Should be sum of early paid U and paid U by current revision.");
            return checkResult;
        }

        checkResult = additionallyUnsCheck(c);

        return checkResult;
    }

    @Override
    public boolean beforeUpdate(ImmutableEnvironment c) {
        boolean checkResult = false;

        calculatePrepaidNamesForDays(false);

        checkResult = prepaidNamesForDays == getStateData().getDouble(PREPAID_ND_FIELD_NAME);
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, "Wrong [state.data." + PREPAID_ND_FIELD_NAME + "] value. " +
                    "Should be sum of early paid U and paid U by current revision.");
            return checkResult;
        }

        checkResult = additionallyUnsCheck(c);

        return checkResult;
    }

    @Override
    public boolean beforeRevoke(ImmutableEnvironment c) {
        return true;
    }

    private boolean additionallyUnsCheck(ImmutableEnvironment ime) {

        boolean checkResult;

        checkResult = ime != null;
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, "Environment should be not null");
            return checkResult;
        }

        checkResult = getExtendedType().equals(SmartContractType.UNS1.name());
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, "definition.extended_type", "illegal value, should be " + SmartContractType.UNS1.name() + " instead " + getExtendedType());
            return checkResult;
        }


        checkResult = (storedNames.size() > 0);
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Names for storing is missing");
            return checkResult;
        }

        checkResult = storedNames.stream().allMatch(n -> n.getUnsRecords().stream().allMatch(unsRecord -> {
            if(unsRecord.getOrigin() != null) {
                if(!unsRecord.getAddresses().isEmpty()) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "name " + n.getUnsName() + " referencing to origin AND addresses. Should be either origin or addresses");
                    return false;
                }

                //check reference exists in contract (ensures that matching contract was checked by system for being approved)
                if(!isOriginReferenceExists(unsRecord.getOrigin())) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "name " + n.getUnsName() + " referencing to origin " + unsRecord.getOrigin().toString() + " but no corresponding reference is found");
                    return false;
                }

                List<Contract> matchingContracts = getTransactionPack().getReferencedItems().values().stream()
                        .filter(contract -> contract.getId().equals(unsRecord.getOrigin())
                                || contract.getOrigin() != null && contract.getOrigin().equals(unsRecord.getOrigin()))
                        .collect(Collectors.toList());

                if(matchingContracts.isEmpty()) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "name " + n.getUnsName() + " referencing to origin " + unsRecord.getOrigin().toString() + " but no corresponding referenced contract is found");
                    return false;
                }

                Contract contract = matchingContracts.get(0);
                if(!contract.getRole("issuer").isAllowedForKeys(getSealedByKeys())) {
                    addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "name " + n.getUnsName() + " referencing to origin " + unsRecord.getOrigin().toString() + ". UNS1 contract should be also signed by this contract issuer key.");
                    return false;
                }

                return true;
            }

            if(unsRecord.getAddresses().isEmpty()) {
                addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "name " + n.getUnsName() + " is missing both addresses and origin.");
                return false;
            }


            if (unsRecord.getAddresses().size() > 2)
                addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "name " + n.getUnsName() + ": Addresses list should not be contains more 2 addresses");

            if ((unsRecord.getAddresses().size() == 2) && unsRecord.getAddresses().get(0).isLong() == unsRecord.getAddresses().get(1).isLong())
                addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "name " + n.getUnsName() + ": Addresses list may only contain one short and one long addresses");

            if(!unsRecord.getAddresses().stream().allMatch(keyAddress -> getSealedByKeys().stream().anyMatch(key -> keyAddress.isMatchingKey(key)))) {
                addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME, "name " + n.getUnsName() + " using address that missing corresponding key UNS contract signed with.");
                return false;
            }

            return true;
        }));

        if (!checkResult) {
            return checkResult;
        }

        checkResult = getSealedByKeys().contains(nodeConfig.getAuthorizedNameServiceCenterKey());
        if(!checkResult) {
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Authorized name service signature is missing");
            return checkResult;
        }

        List<String> reducedNamesToCkeck = getReducedNamesToCheck();
        List<HashId> originsToCheck = getOriginsToCheck();
        List<String> addressesToCheck = getAddressesToCheck();

        boolean checkResultNames = false;
        if (checkResult)
            checkResult = checkResultNames = additionallyUnsCheck_isNamesAvailable(ime, reducedNamesToCkeck);

        boolean checkResultOrigins = false;
        if (checkResult)
            checkResult = checkResultOrigins = additionallyUnsCheck_isOriginsAvailable(ime, originsToCheck);

        boolean checkResultAddresses = false;
        if (checkResult)
            checkResult = checkResultAddresses = additionallyUnsCheck_isAddressesAvailable(ime, addressesToCheck);

        if (!checkResult) {
            if (checkResultNames)
                nameCache.unlockNameList(reducedNamesToCkeck);
            if (checkResultOrigins)
                nameCache.unlockOriginList(originsToCheck);
            if (checkResultAddresses)
                nameCache.unlockAddressList(addressesToCheck);
        }

        return checkResult;
    }

    private List<String> getReducedNamesToCheck() {
        Set<String> reducedNames = new HashSet<>();
        for (UnsName unsName : storedNames)
            reducedNames.add(unsName.getUnsNameReduced());
        for (Approvable approvable : getRevokingItems()) {
            if (approvable instanceof UnsContract) {
                UnsContract revokingUns = (UnsContract) approvable;
                for (UnsName unsName : revokingUns.storedNames)
                    reducedNames.remove(unsName.getUnsNameReduced());
            }
        }
        return new ArrayList<>(reducedNames);
    }

    private List<HashId> getOriginsToCheck() {
        Set<HashId> origins = new HashSet<>();
        for (UnsName unsName : storedNames)
            for (UnsRecord unsRecord : unsName.getUnsRecords())
                if (unsRecord.getOrigin() != null)
                    origins.add(unsRecord.getOrigin());
        for (Approvable approvable : getRevokingItems()) {
            if (approvable instanceof UnsContract) {
                UnsContract revokingUns = (UnsContract) approvable;
                for (UnsName unsName : revokingUns.storedNames)
                    for (UnsRecord unsRecord : unsName.getUnsRecords())
                        if (unsRecord.getOrigin() != null)
                            origins.remove(unsRecord.getOrigin());
            }
        }
        return new ArrayList<>(origins);
    }

    private List<String> getAddressesToCheck() {
        List<String> addresses = new ArrayList<>();
        for (UnsName unsName : storedNames)
            for (UnsRecord unsRecord : unsName.getUnsRecords())
                for (KeyAddress keyAddress : unsRecord.getAddresses())
                    addresses.add(keyAddress.toString());
        for (Approvable approvable : getRevokingItems()) {
            if (approvable instanceof UnsContract) {
                UnsContract revokingUns = (UnsContract) approvable;
                for (UnsName unsName : revokingUns.storedNames)
                    for (UnsRecord unsRecord : unsName.getUnsRecords())
                        for (KeyAddress keyAddress : unsRecord.getAddresses())
                            addresses.remove(keyAddress.toString());
            }
        }
        return new ArrayList<>(addresses);
    }

    private boolean additionallyUnsCheck_isNamesAvailable(ImmutableEnvironment ime, List<String> reducedNames) {
        boolean checkResult;

        if (reducedNames.size() == 0)
            return true;

        checkResult = nameCache.lockNameList(reducedNames);
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Some of selected names are registering right now");
            return checkResult;
        }

        checkResult = ledger.isAllNameRecordsAvailable(reducedNames);
        if (!checkResult) {
            nameCache.unlockNameList(reducedNames);
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Some of selected names already registered");
            return checkResult;
        }

        return checkResult;
    }

    private boolean additionallyUnsCheck_isOriginsAvailable(ImmutableEnvironment ime, List<HashId> origins) {
        boolean checkResult;

        if (origins.size() == 0)
            return true;

        checkResult = nameCache.lockOriginList(origins);
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Some of selected origins are registering right now");
            return checkResult;
        }

        checkResult = ledger.isAllOriginsAvailable(origins);
        if (!checkResult) {
            nameCache.unlockOriginList(origins);
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Some of selected origins already registered");
            return checkResult;
        }

        return checkResult;
    }

    private boolean additionallyUnsCheck_isAddressesAvailable(ImmutableEnvironment ime, List<String> addresses) {
        boolean checkResult;

        if (addresses.size() == 0)
            return true;

        checkResult = nameCache.lockAddressList(addresses);
        if (!checkResult) {
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Some of selected addresses are registering right now");
            return checkResult;
        }

        checkResult = ledger.isAllAddressesAvailable(addresses);
        if (!checkResult) {
            nameCache.unlockAddressList(addresses);
            addError(Errors.FAILED_CHECK, NAMES_FIELD_NAME,"Some of selected addresses already registered");
            return checkResult;
        }

        return checkResult;
    }

    private boolean isOriginReferenceExists(HashId origin) {
        return getReferences().values().stream().anyMatch(ref ->
                ref.getConditions().getArray(Reference.conditionsModeType.all_of.name()).stream().anyMatch(cond ->
                        cond.equals(REFERENCE_CONDITION_PREFIX+origin.toBase64String())));
    }

    @Override
    public @Nullable Binder onCreated(MutableEnvironment me) {
        long environmentId = ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(me), getPackedTransaction());

        List<String> namesList = new ArrayList<>();
        List<String> addressesList = new ArrayList<>();
        List<HashId> originsList = new ArrayList<>();

        storedNames.forEach(sn -> {
                    namesList.add(sn.getUnsName());
                    NameRecordModel nrm = new NameRecordModel();
                    nrm.name_full = sn.getUnsName();
                    nrm.name_reduced = sn.getUnsNameReduced();
                    nrm.description = sn.getUnsDescription();
                    nrm.url = sn.getUnsURL();
                    nrm.expires_at = prepaidFrom.plusSeconds((long) (prepaidNamesForDays * 24 * 3600));
                    nrm.entries = new ArrayList<>();
                    sn.getUnsRecords().forEach(snr ->{
                        NameEntryModel nem = new NameEntryModel();
                        if(snr.getOrigin() != null) {
                            nem.origin = snr.getOrigin().getDigest();
                            originsList.add(snr.getOrigin());
                        }

                        snr.getAddresses().forEach(keyAddress -> {
                            addressesList.add(keyAddress.toString());
                            if(keyAddress.isLong()) {
                                nem.long_addr = keyAddress.toString();
                            } else {
                                nem.short_addr = keyAddress.toString();
                            }
                        });
                        nrm.entries.add(nem);
                    });
                    nrm.environment_id = environmentId;
                    ledger.saveNameRecord(nrm);
                }
        );
        nameCache.unlockNameList(namesList);
        nameCache.unlockAddressList(addressesList);
        nameCache.unlockOriginList(originsList);
        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public Binder onUpdated(MutableEnvironment me) {
        ledger.removeEnvironment(getId());

        long environmentId = ledger.saveEnvironmentToStorage(getExtendedType(), getId(), Boss.pack(me), getPackedTransaction());

        List<String> namesList = new ArrayList<>();
        List<String> addressesList = new ArrayList<>();
        List<HashId> originsList = new ArrayList<>();


        storedNames.forEach(sn -> {
                    namesList.add(sn.getUnsName());
                    NameRecordModel nrm = new NameRecordModel();
                    nrm.name_full = sn.getUnsName();
                    nrm.name_reduced = sn.getUnsNameReduced();
                    nrm.description = sn.getUnsDescription();
                    nrm.url = sn.getUnsURL();
                    nrm.expires_at = prepaidFrom.plusSeconds((long) (prepaidNamesForDays * 24 * 3600));
                    nrm.entries = new ArrayList<>();
                    sn.getUnsRecords().forEach(snr ->{
                        NameEntryModel nem = new NameEntryModel();
                        if(snr.getOrigin() != null) {
                            nem.origin = snr.getOrigin().getDigest();
                            originsList.add(snr.getOrigin());
                        }

                        snr.getAddresses().forEach(keyAddress -> {
                            addressesList.add(keyAddress.toString());
                            if(keyAddress.isLong()) {
                                nem.long_addr = keyAddress.toString();
                            } else {
                                nem.short_addr = keyAddress.toString();
                            }
                        });
                        nrm.entries.add(nem);
                    });
                    nrm.environment_id = environmentId;
                    ledger.saveNameRecord(nrm);
                }
        );
        nameCache.unlockNameList(namesList);
        nameCache.unlockAddressList(addressesList);
        nameCache.unlockOriginList(originsList);

        return Binder.fromKeysValues("status", "ok");
    }

    @Override
    public void onRevoked(ImmutableEnvironment ime) {
        ledger.removeEnvironment(getId());

    }



    private void addOriginReference(HashId origin) {
        Reference ref = new Reference(this);
        ref.type = Reference.TYPE_EXISTING_STATE;
        ref.setName(origin.toString());
        List<Object> conditionsList = new ArrayList<>();
        conditionsList.add(REFERENCE_CONDITION_PREFIX+origin.toBase64String());
        Binder conditions = Binder.of(Reference.conditionsModeType.all_of.name(),conditionsList);
        ref.setConditions(conditions);
        if(originContracts.containsKey(origin))
            ref.addMatchingItem(originContracts.get(origin));
        addReference(ref);

    }

    public void addOriginContract(Contract contract) {
        originContracts.put(contract.getOrigin() != null ? contract.getOrigin() : contract.getId(),contract);
    }



    public void addUnsName(UnsName unsName) {
        storedNames.add(unsName);
    }

    public UnsName getUnsName(String name) {
        for(UnsName unsName : storedNames) {
            if(unsName.getUnsName().equals(name)) {
                return unsName;
            }
        }
        return null;
    }

    static {
        Config.forceInit(UnsRecord.class);
        Config.forceInit(UnsName.class);
        Config.forceInit(UnsContract.class);
        DefaultBiMapper.registerClass(UnsRecord.class);
        DefaultBiMapper.registerClass(UnsName.class);
        DefaultBiMapper.registerClass(UnsContract.class);
    }

    public void removeName(String name) {
        UnsName nameToRemove = null;
        for(UnsName unsName : storedNames) {
            if(unsName.getUnsName().equals(name)) {
                nameToRemove = unsName;
                break;
            }
        }
        if(nameToRemove != null) {
            storedNames.remove(nameToRemove);
        }
    }
}

