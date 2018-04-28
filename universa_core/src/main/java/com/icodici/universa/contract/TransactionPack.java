/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract;


import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.HashId;
import com.icodici.universa.HashIdentifiable;
import com.icodici.universa.contract.services.NSmartContract;
import com.icodici.universa.contract.services.SlotContract;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The main contract and its subItems and referenced contracts needed for registration submission bundled together.
 * The main contract is specified in constructor or with {@link #setContract(Contract)},
 * and obtained by {@link #getContract()}. The subItems are: new items (f. e. from split operation),
 * revoking contracts (f. e. from creating new revision, joining contracts and so on), all of them stores
 * in the {@link TransactionPack#subItems}. Referenced contracts needed for main contract stores
 * in the {@link TransactionPack#referencedItems}. Also you may need to store keys in the transaction pack
 * (f. e. when using addresses or anonymous ids), use {@link TransactionPack#keysForPack} for it.
 * <p>
 * Packed format of transaction pack could be very well used instead of {@link Contract#seal()} binaries as it holds
 * together the window around the contract graph (parent and siblings) needed to approve its state. It is advised to use
 * {@link Contract#getPackedTransaction()} to save the Contract and {@link Contract#fromPackedTransaction(byte[])} to
 * restore, respectively, as it holds together all relevant data. The file extension for it should be .unicon, as the
 * latter function is able to read and reconstruct legacy v2 sealed contracts, current v3 sealed contracts and packed
 * TransactionPack instances, it is a universal format and a universal routine to load it. See {@link
 * Contract#fromPackedTransaction(byte[])} for more.
 * <p>
 * Indeed, it is possible to keep all contract binaries separately and put them together for submission only, thus
 * saving the space.
 * <p>
 * The legacy v2 self-contained format is no more in use as the size of the hashed binary constantly grows.
 * <p>
 * Using {@link Contract#getTransactionPack()} will create transaction pack with needed subItems, referencedItems and keys.
 * <br>
 * But if you need to add some objects manually use {@link TransactionPack#addSubItem(Contract)} to add subItem,
 * use {@link TransactionPack#addReferencedItem(Contract)} to add referenced contracts
 * and {@link TransactionPack#addKeys(PublicKey...)} to add needed keys manually.
 * <p>
 * A word of terminology.
 * <p>
 * We name as the <i>transaction contract</i> any contract that is going to be passed to the network for verification
 * and approval; it could and should contain other contracts as revoking as well as new items (siblings) to create.
 * These <i>subItems</i> do not need to be added separately.
 * <p>
 * Note. To put several operations in an atomic transaction, put iy all into a single top-level contract.
 */
@BiType(name = "TransactionPack")
public class TransactionPack implements BiSerializable {

    private byte[] packedBinary;
    private boolean reconstructed = false;
    private Map<HashId, Contract> subItems = new HashMap<>();
    private Map<HashId, Contract> referencedItems = new HashMap<>();
    private Set<PublicKey> keysForPack = new HashSet<>();
    private Contract contract;

    /**
     * Create a transaction pack and add a contract to it. See {@link TransactionPack#TransactionPack()} and {@link
     * #setContract(Contract)} for more information.
     *
     * @param contract is {@link Contract} to be send with this {@link TransactionPack}
     */
    public TransactionPack(Contract contract) {
        this();
        setContract(contract);
    }

    /**
     * The main contract of this {@link TransactionPack}.
     *
     * @return a {@link Contract}
     */
    public Contract getContract() {
        return contract;
    }

    public Contract getSubItem(HashId id) {
        return subItems.get(id);
    }

    public Contract getSubItem(HashIdentifiable hid) {
        return getSubItem(hid.getId());
    }

    public Set<PublicKey> getKeysForPack() {
        return keysForPack;
    }

    public TransactionPack() {
    }

    /**
     * Add contract that already includes all its subItems, referenced items and keys. It will be added as a contract
     * per transaction, while its subItems will be added to subItems if not already included and refrenced items and keys too.
     * <p>
     * This is extremely important that the contract is properly sealed as well as its possibly new items, revoking
     * items and referenced items have binary image attached. <b>Do not ever seal the approved contract</b>:
     * it will break it's id and cancel the approval blockchain, so the new state will not be approved.
     * If it was done by mistake, reload the packed contract to continue.
     *
     * @param c is a contract to append to the list of transactions.
     */
    public void setContract(Contract c) {
        if (contract != null)
            throw new IllegalArgumentException("the contract is already added");
        contract = c;
        packedBinary = null;

        extractAllSubItemsAndReferenced(c);

        c.setTransactionPack(this);

        for (PrivateKey key : c.getKeysToSignWith())
            addKeys(key.getPublicKey());
    }


    protected synchronized void extractAllSubItemsAndReferenced(Contract c) {
        for (Contract r : c.getRevoking()) {
            putSubItem(r);
            for (Contract ref : r.getReferenced()) {
                addReferencedItem(ref);
            }
        }
        for (Contract n : c.getNew()) {
            putSubItem(n);
            extractAllSubItemsAndReferenced(n);
        }

        for (Contract ref : c.getReferenced()) {
            addReferencedItem(ref);
        }
    }


    /**
     * Direct add the subItem. Not recommended as {@link #setContract(Contract)} already does it for all subItems.
     * Use it to add subItems not mentioned in the added contracts.
     *
     * @param subItem is {@link Contract} for adding
     */
    public void addSubItem(Contract subItem) {
        if (!subItems.containsKey(subItem.getId())) {
            packedBinary = null;
            subItems.put(subItem.getId(), subItem);
        }
    }

    /**
     * Direct add the referenced items. Use it to add references not mentioned in the added contracts.
     *
     * @param referencedItem is {@link Contract} for adding
     */
    public void addReferencedItem(Contract referencedItem) {
        if (!referencedItems.containsKey(referencedItem.getId())) {
            packedBinary = null;
            referencedItems.put(referencedItem.getId(), referencedItem);
        }
    }

    /**
     * Add public key to {@link TransactionPack} that will match with anonymous ids or addresses in the {@link Contract} roles.
     *
     * @param keys is {@link PublicKey} that will be compare with anonymous ids or addresses.
     */
    public void addKeys(PublicKey... keys) {
        packedBinary = null;
        for (PublicKey key : keys) {
            if (!keysForPack.contains(key)) {
                keysForPack.add(key);
            }
        }
    }

    /**
     * Store the subItem. Called by the {@link #setContract(Contract)} and only useful if the latter is
     * overriden. SubItems are not processed by themselves but only as parts of the contracts add with
     * {@link #setContract(Contract)}
     *
     * @param subItem is {@link Contract} for putting
     */
    protected synchronized void putSubItem(Contract subItem) {
//        if (!contract.isOk())
//            throw new IllegalArgumentException("subItem has errors");
        subItems.put(subItem.getId(), subItem);
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) throws IOException {

        synchronized (this) {
            // It is local quantiser that should throw exception
            // if limit is got while deserializing TransactionPack.
            Quantiser quantiser = new Quantiser();
            quantiser.reset(Contract.getTestQuantaLimit());

            List<Object> keysList = deserializer.deserializeCollection(data.getList("keys", new ArrayList<>()));

            keysForPack = new HashSet<>();
            if(keysList != null) {
                for (Object x : keysList) {
                    if (x instanceof Bytes)
                        x = ((Bytes) x).toArray();
                    if (x instanceof byte[]) {
                        keysForPack.add(new PublicKey((byte[]) x));
                    } else {
                        throw new IllegalArgumentException("unsupported key object: " + x.getClass().getName());
                    }
                }
            }

            List<Bytes> foreignReferenceBytesList = deserializer.deserializeCollection(
                    data.getList("referencedItems", new ArrayList<>())
            );
            if(foreignReferenceBytesList != null) {
                for (Bytes b : foreignReferenceBytesList) {
                    Contract frc = new Contract(b.toArray(), this);
                    quantiser.addWorkCostFrom(frc.getQuantiser());
                    referencedItems.put(frc.getId(), frc);
                }
            }
            

            List<Bytes> subItemsBytesList = deserializer.deserializeCollection(
                    data.getListOrThrow("subItems")
            );

            HashMap<ContractDependencies, Bytes> allContractsTrees = new HashMap<>();
            List<HashId> allContractsHids = new ArrayList<>();
            HashMap<ContractDependencies, Bytes> sortedSubItemsBytesList = new HashMap<>();

            if (subItemsBytesList != null) {
                // First of all extract contracts dependencies from subItems
                for (Bytes b : subItemsBytesList) {
                    ContractDependencies ct = new ContractDependencies(b.toArray());
                    allContractsTrees.put(ct, b);
                    allContractsHids.add(ct.id);
                }

                // then recursively from ends of dependencies tree to top go throw it level by level
                // and add items to subItems on the each level of tree's hierarchy
                do {
                    // first add contract from ends of trees, means without own subitems
                    sortedSubItemsBytesList = new HashMap<>();
                    List<ContractDependencies> removingContractDependencies = new ArrayList<>();
                    for (ContractDependencies ct : allContractsTrees.keySet()) {
                        if (ct.dependencies.size() == 0) {
                            sortedSubItemsBytesList.put(ct, allContractsTrees.get(ct));
                            removingContractDependencies.add(ct);
                        }
                    }

                    // remove found items from tree's list
                    for (ContractDependencies ct : removingContractDependencies) {
                        allContractsTrees.remove(ct);
                    }

                    // then add contract with already exist subitems in the subItems or will never find in the tree
                    removingContractDependencies = new ArrayList<>();
                    for (ContractDependencies ct : allContractsTrees.keySet()) {
                        boolean allDependenciesSafe = true;
                        for (HashId hid : ct.dependencies) {
                            if (!subItems.containsKey(hid) && allContractsHids.contains(hid)) {
                                allDependenciesSafe = false;
                            }
                        }
                        if (allDependenciesSafe) {
                            sortedSubItemsBytesList.put(ct, allContractsTrees.get(ct));
                            removingContractDependencies.add(ct);
                        }
                    }

                    // remove found items from tree's list
                    for (ContractDependencies ct : removingContractDependencies) {
                        allContractsTrees.remove(ct);
                    }

                    // add found binaries on the hierarchy level to subItems
//                    for (int i = 0; i < sortedSubItemsBytesList.size(); i++) {
//                        Contract c = new Contract(sortedSubItemsBytesList.get(i).toArray(), this);
//                        quantiser.addWorkCostFrom(c.getQuantiser());
//                        subItems.put(c.getId(), c);
//                    }
                    for (ContractDependencies ct : sortedSubItemsBytesList.keySet()) {
                        Bytes b = sortedSubItemsBytesList.get(ct);
                        createNeededContractAndAddToSubItems(ct, b, quantiser);
                    }

                    // then repeat until we can find hierarchy
                } while (sortedSubItemsBytesList.size() != 0);

                // finally add not found binaries on the hierarchy levels to subItems
                for (ContractDependencies ct : allContractsTrees.keySet()) {
                    Bytes b = allContractsTrees.get(ct);
                    createNeededContractAndAddToSubItems(ct, b, quantiser);
                }
            }

            byte[] bb = data.getBinaryOrThrow("contract");
            String extendedType = data.getString("extended_type", null);
            SmartContract.SmartContractType scType = null;
            if(extendedType != null) {
                try {
                    scType = SmartContract.SmartContractType.valueOf(extendedType);
                } catch (IllegalArgumentException e) {
                }
            }
            if(scType != null) {
                switch(scType) {
                    case DEFAULT_SMART_CONTRACT:
                        contract = new SmartContract(bb, this);
                        break;

                    case N_SMART_CONTRACT:
                        contract = new NSmartContract(bb, this);
                        break;

                    case SLOT1:
                        contract = new SlotContract(bb, this);
                        break;
                }
            } else {
                contract = new Contract(bb, this);
            }
            quantiser.addWorkCostFrom(contract.getQuantiser());
        }
    }

    private void createNeededContractAndAddToSubItems(ContractDependencies ct, Bytes b, Quantiser quantiser) throws IOException {
        Contract c = null;
        SmartContract.SmartContractType scType = null;
        if(ct.extendedType != null) {
            try {
                scType = SmartContract.SmartContractType.valueOf(ct.extendedType);
            } catch (IllegalArgumentException e) {
            }
        }
        if(scType != null) {
            switch (scType) {
                case DEFAULT_SMART_CONTRACT:
                    c = new SmartContract(b.toArray(), this);
                    break;

                case N_SMART_CONTRACT:
                    c = new NSmartContract(b.toArray(), this);
                    break;

                case SLOT1:
                    c = new SlotContract(b.toArray(), this);
                    break;
            }
        } else {
            c = new Contract(b.toArray(), this);
        }
        if(c != null) {
            quantiser.addWorkCostFrom(c.getQuantiser());
            subItems.put(c.getId(), c);
        }
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        synchronized (this) {

            Binder of = Binder.of(
                    "contract", contract.getLastSealedBinary(),
                    "subItems",
                    serializer.serialize(
                            subItems.values().stream()
                                    .map(x -> x.getLastSealedBinary()).collect(Collectors.toList())
                    )

            );
            if(referencedItems.size() > 0) {
                of.set("referencedItems",
                        serializer.serialize(
                                referencedItems.values().stream()
                                        .map(x -> x.getLastSealedBinary()).collect(Collectors.toList())
                        ));
            }
            if(keysForPack.size() > 0) {
                of.set("keys", serializer.serialize(
                        keysForPack.stream()
                                .map(x -> x.pack()).collect(Collectors.toList())
                ));
            }

            if(contract instanceof SmartContract) {
                of.set("extended_type", ((SmartContract) contract).getExtendedType());
            }
            return of;
        }
    }

    public final boolean isReconstructed() {
        return reconstructed;
    }

    /**
     * Unpack either old contract binary (all included), or newer transaction pack. Could be used to load old contracts
     * to perform a transaction.
     *
     * @param packOrContractBytes is binary that was packed by {@link TransactionPack#pack()}
     * @param allowNonTransactions if false, non-transaction pack data will cause IOException.
     *
     * @return transaction, either unpacked or reconstructed from the self-contained v2 contract
     * @throws IOException if something went wrong
     */
    public static TransactionPack unpack(byte[] packOrContractBytes, boolean allowNonTransactions) throws IOException {

        Object x = Boss.load(packOrContractBytes);

        if (x instanceof TransactionPack) {
            return (TransactionPack) x;
        }

        if (!allowNonTransactions)
            throw new IOException("expected transaction pack");

        // This is an old v2 self-contained contract or a root v3 contract, no revokes, no siblings.
        TransactionPack tp = new TransactionPack();
        tp.reconstructed = true;
        tp.packedBinary = packOrContractBytes;
        tp.contract = new Contract(packOrContractBytes, tp);
        return tp;
    }

    /**
     * Unpack either old contract binary (all included), or newer transaction pack. Could be used to load old contracts
     * to perform a transaction.
     *
     * @param packOrContractBytes binary that was packed by {@link TransactionPack#pack()}
     *
     * @return transaction, either unpacked or reconstructed from the self-contained v2 contract
     * @throws IOException if something went wrong
     */
    public static TransactionPack unpack(byte[] packOrContractBytes) throws IOException {
        return unpack(packOrContractBytes, true);
    }


    /**
     * Shortcut to {@link Boss#pack(Object)} for this.
     *
     * @return packed binary
     */
    public synchronized byte[] pack() {
        if (packedBinary == null)
            packedBinary = Boss.pack(this);
        return packedBinary;
    }

    /**
     * @return map of subItems from new and revoke contracts
     */
    public Map<HashId, Contract> getSubItems() {
        return subItems;
    }

    /**
     * @return map of referenced items
     */
    public Map<HashId, Contract> getReferencedItems() {
        return referencedItems;
    }

    static {
        DefaultBiMapper.registerClass(TransactionPack.class);
    }

    /**
     * Trace the tree of contracts subItems on the stdout.
     */
    public void trace() {
        System.out.println("Transaction pack");
        System.out.println("\tContract:");
        System.out.println("\t\t" + contract.getId());
        contract.getNewItems().forEach(x -> System.out.println("\t\t\tnew: " + x.getId()));
        contract.getRevokingItems().forEach(x -> System.out.println("\t\t\trevoke: " + x.getId()));
        System.out.println("\tSubItems:");
        subItems.forEach((hashId, contract) -> System.out.println("\t\t" + hashId + " -> " + contract.getId()));
    }

    /**
     * Class that extracts subItems from given contract bytes and build dependencies. But do not do anything more.
     */
    public class ContractDependencies {
        private final Set<HashId> dependencies = new HashSet<>();
        private final HashId id;
        private final String extendedType;

        public ContractDependencies(byte[] sealed) throws IOException {
            this.id = HashId.of(sealed);
            Binder data = Boss.unpack(sealed);
            byte[] contractBytes = data.getBinaryOrThrow("data");

            // This must be explained. By default, Boss.load will apply contract transformation in place
            // as it is registered BiSerializable type, and we want to avoid it. Therefore, we decode boss
            // data without BiSerializer and then do it by hand calling deserialize:
            Binder payload = Boss.load(contractBytes, null);

            extendedType = payload.getBinder("contract").getBinder("definition").getString("extended_type", null);

            int apiLevel = data.getIntOrThrow("version");

            if (apiLevel < 3) {
                // no need to build tree - subitems will be reconstructed from binary, not from subItems
            } else {
                // new format: only subItems are included
                for (Binder b : (List<Binder>) payload.getList("revoking", Collections.EMPTY_LIST)) {
                    HashId hid = HashId.withDigest(b.getBinaryOrThrow("composite3"));
                    dependencies.add(hid);
                }
                for (Binder b : (List<Binder>) payload.getList("new", Collections.EMPTY_LIST)) {
                    HashId hid = HashId.withDigest(b.getBinaryOrThrow("composite3"));
                    dependencies.add(hid);
                }
            }
        }
    }
}
