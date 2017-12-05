/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract;


import com.icodici.universa.HashId;
import com.icodici.universa.HashIdentifiable;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The main contract and its counterparts needed for registration submission bundled together. The main contract is
 * specified in constructor or with {@link #setContract(Contract)}, and obtained by {@link #getContract()}. The
 * counterparts are: possible siblings (for example, of split operation), and revoking contracts (when creating new
 * revision, joining contracts and so on). All of these should be presented the the Network in the form of sealed
 * binaries, that is when {@link TransactionPack} is used.
 * <p>
 * Packed format of transaction pack could be very well used instead of {@link Contract#seal()} binaries as it holds
 * together the window around the contract graph (parent and siblings) needed to approve its state. It is advised to use
 * {@link Contract#getPackedTransaction()} to save the Contract and {@link Contract#fromPackedTransaction(byte[])} to
 * restore, respectively, as it holds together all relevant data. The file extension for it should be .unicon, as the
 * latter function is able to read and reconstruct lefacy v2 sealed contracts, current v3 seald contracts and packed
 * TransactionPack instances, it is a universal format and a universal routine to load it. See {@link
 * Contract#fromPackedTransaction(byte[])} for more.
 * <p>
 * Indeed, it is possible to keep all contract binaries separately and put them together for submission only, thus
 * saving the space.
 * <p>
 * The legacy v2 self-contained format is no more in use as the size of the hashed binary constantly grows.
 * <p>
 * A word of terminology.
 * <p>
 * We name as the <i>transaction contract</i> any contract that is going to be passed to the network for verification
 * and approval; it could and should contain other contracts as revoking as well as new items (siblings) to create.
 * These <i>referenced</i> contracts do not need to be addded separately.
 * <p>
 * Note. To put several operations in an atomic transaction, put iy all into a single top-level contract.
 * <p>
 * This implementation is not thread safe. Synchronize your access if need.
 */
@BiType(name = "TransactionPack")
public class TransactionPack implements BiSerializable {

    private static byte[] packedBinary;
    private boolean reconstructed = false;
    private Map<HashId, Contract> references = new HashMap<>();
    private Contract contract;

    /**
     * Create a transaction pack and add a contract to it. See {@link TransactionPack#TransactionPack()} and {@link
     * #setContract(Contract)} for more information.
     *
     * @param contract
     */
    public TransactionPack(Contract contract) {
        this();
        setContract(contract);
    }

    /**
     * The list of contracts to approve.
     */
    public Contract getContract() {
        return contract;
    }

    public Contract getReference(HashId id) {
        return references.get(id);
    }

    public Contract getReference(HashIdentifiable hid) {
        return getReference(hid.getId());
    }

    public TransactionPack() {
    }

    /**
     * Add contract that already includes all its references. It will be added as a contract per transactions, while its
     * references will be added to references if not already included.
     * <p>
     * This is extremely importand that the contract is properly sealed as well as its possibly new items, and revoking
     * items have binary image attached. <b>Do not ever seal the approved contract</b>: it will break it's id and cancel
     * the approval blockchain, so the new state will not be approved. If it was done by mistake, reload the packed
     * contract to continue.
     *
     * @param c contract to append to the list of transactions.
     */
    public void setContract(Contract c) {
        if (contract != null)
            throw new IllegalArgumentException("the contract is already added");
        contract = c;
        packedBinary = null;
        c.getRevokingItems().forEach(i -> putReference((Contract) i));
        c.getNewItems().forEach(i -> putReference((Contract) i));
        c.setTransactionPack(this);
    }

    /**
     * Direct add the reference. Not recommended as {@link #setContract(Contract)} already does it for all referenced
     * contracts. Use it to add references not mentioned in the added contracts.
     *
     * @param reference
     */
    public void addReference(Contract reference) {
        if (!references.containsKey(reference.getId())) {
            packedBinary = null;
            references.put(reference.getId(), reference);
        }
    }

    /**
     * store the referenced contract. Called by the {@link #setContract(Contract)} and only useful if the latter is
     * overriden. Referenced contracts are not processed by themselves but only as parts of the contracts add with
     * {@link #setContract(Contract)}
     *
     * @param contract
     */
    protected void putReference(Contract contract) {
        if (!contract.isOk())
            throw new IllegalArgumentException("referenced contract has errors");
        references.put(contract.getId(), contract);
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) throws IOException {
        List<Bytes> ll = deserializer.deserializeCollection(
                data.getListOrThrow("references")
        );
        if (ll != null) {
            for (Bytes b : ll) {
                Contract c = null;
                try {
                    c = new Contract(b.toArray(), this);
                } catch (Quantiser.QuantiserException e) {
                    throw new IOException(e);
                }
                references.put(c.getId(), c);
            }
        }
        byte[] bb = data.getBinaryOrThrow("contract");
        try {
            contract = new Contract(bb, this);
        } catch (Quantiser.QuantiserException e) {
            throw new IOException(e);
        }
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        return Binder.of(
                "contract", contract.getLastSealedBinary(),
                "references",
                serializer.serialize(
                        references.values().stream()
                                .map(x -> x.getLastSealedBinary()).collect(Collectors.toList())
                )
        );
    }

    public final boolean isReconstructed() {
        return reconstructed;
    }

    /**
     * Unpack either old contract binary (all included), or newer transaction pack. Could be used to load old contarcts
     * to perform a transaction.
     *
     * @param packOrContractBytes
     * @param allowNonTransacions if false, non-trasnaction pack data will cause IOException.
     *
     * @return transaction, either unpacked or reconstructed from the self-contained v2 contract
     */
    public static TransactionPack unpack(byte[] packOrContractBytes, boolean allowNonTransacions) throws IOException, Quantiser.QuantiserException {
        packedBinary = packOrContractBytes;

        Object x = Boss.load(packOrContractBytes);

        if (x instanceof TransactionPack) {
            return (TransactionPack) x;
        }

        if (!allowNonTransacions)
            throw new IOException("expected transaction pack");

        // This is an old v2 self-contained contract or a root v3 contract, no revokes, no siblings.
        TransactionPack tp = new TransactionPack();
        tp.reconstructed = true;
        tp.contract = new Contract(packOrContractBytes, tp);
        return tp;
    }

    /**
     * Unpack either old contract binary (all included), or newer transaction pack. Could be used to load old contarcts
     * to perform a transaction.
     *
     * @param packOrContractBytes
     *
     * @return transaction, either unpacked or reconstructed from the self-contained v2 contract
     */
    public static TransactionPack unpack(byte[] packOrContractBytes) throws IOException, Quantiser.QuantiserException {
        return unpack(packOrContractBytes, true);
    }


    /**
     * Shortcut to {@link Boss#pack(Object)} for this.
     *
     * @return
     */
    public byte[] pack() {
        if (packedBinary == null)
            packedBinary = Boss.pack(this);
        return packedBinary;
    }

    /**
     * @return map of referenced contracts
     */
    public Map<HashId, Contract> getReferences() {
        return references;
    }

    static {
        DefaultBiMapper.registerClass(TransactionPack.class);
    }

    /**
     * Trace the tree of contracts references on the stdout.
     */
    public void trace() {
        System.out.println("Transaction pack");
        System.out.println("\tContract:");
        System.out.println("\t\t" + contract.getId());
        contract.getNewItems().forEach(x -> System.out.println("\t\t\tnew: " + x.getId()));
        contract.getRevokingItems().forEach(x -> System.out.println("\t\t\trevoke: " + x.getId()));
        System.out.println("\tReferences:");
        references.forEach((hashId, contract) -> System.out.println("\t\t" + hashId + " -> " + contract.getId()));
    }
}
