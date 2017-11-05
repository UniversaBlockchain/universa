/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract;


import com.icodici.universa.HashId;
import com.icodici.universa.HashIdentifiable;
import net.sergeych.biserializer.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The bundle of linked contracts to be passed to the network to perform the operation. Consists of contracts to approve
 * and their counterparts (revoking versions, siblings), optional contracts to identify the calling party and to pay the
 * transaction and other stuff needed by the network to properly process.
 * <p>
 * A word of terminology.
 * <p>
 * We name as the <i>transaction contract</i> any contract that is going to be passed to the network for verification
 * and approval; it could and should contain other contracts as revoking as well as new items (siblings) to create.
 * These <i>referenced</i> contracts do not need to be addded separately.
 * <p>
 * The contracts in the list are processed by the Network in the order of addition. If it is not state otherwise, the
 * sequence of the contracts pack is not an atomic transaction, unlike each contract of it which is an atomic
 * transaction.
 * <p>
 * To put several operations in an atomic transaction, put iy all into a single top-level contract.
 * <p>
 * This implementation is not thread safe. Synchronize your access if need.
 */
@BiType(name = "TransactionPack")
public class TransactionPack implements BiSerializable {

    private static byte[] packedBinary;
    private Map<HashId, Contract> references = new HashMap<>();
    private List<Contract> contracts = new ArrayList<>();

    /**
     * Create a transaction pack and add a contract to it. See {@link TransactionPack#TransactionPack()} and {@link
     * #addContract(Contract)} for more information.
     *
     * @param contract
     */
    public TransactionPack(Contract contract) {
        this();
        addContract(contract);
    }

    /**
     * The list of contracts to approve.
     */
    public List<Contract> getContracts() {
        return contracts;
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
    public void addContract(Contract c) {
        if (contracts.contains(c))
            throw new IllegalArgumentException("the contract is already added");
        contracts.add(c);
        packedBinary = null;
        c.getRevokingItems().forEach(i -> putReference((Contract) i));
        c.getNewItems().forEach(i -> putReference((Contract) i));
        c.setTransactionPack(this);
    }

    /**
     * Direct add the reference. Not recommended as {@link #addContract(Contract)} already does it for all referenced
     * contracts. Use it to add references not mentioned in the added contracts.
     *
     * @param reference
     */
    public void addReference(Contract reference) {
        if( !references.containsKey(reference.getId())) {
            packedBinary = null;
            references.put(reference.getId(), reference);
        }
    }

    /**
     * store the referenced contract. Called by the {@link #addContract(Contract)} and only useful if the latter is
     * overriden. Referenced contracts are not processed by themselves but only as parts of the contracts add with
     * {@link #addContract(Contract)}
     *
     * @param contract
     */
    protected void putReference(Contract contract) {
        if (!contract.isOk())
            throw new IllegalArgumentException("referenced contract has errors");
        references.put(contract.getId(), contract);
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        try {
            List<Bytes> ll = deserializer.deserializeCollection(
                    data.getListOrThrow("references")
            );
            if (ll != null)
                for (Bytes b : ll) {
                    Contract c = new Contract(b.toArray(), this);
                    references.put(c.getId(), c);
                }
            ll = deserializer.deserialize(data.getList("contracts", null));
            if (ll != null)
                for (Bytes b : ll) {
                    Contract c = new Contract(b.toArray(), this);
                    contracts.add(c);
                }
        } catch (IOException e) {
            throw new RuntimeException("illegal data format in TransactionPack", e);
        }
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        return Binder.of(
                "contracts",
                serializer.serialize(
                        contracts.stream()
                                .map(x -> x.getLastSealedBinary()).collect(Collectors.toList())
                ),
                "references",
                serializer.serialize(
                        references.values().stream()
                                .map(x -> x.getLastSealedBinary()).collect(Collectors.toList())
                )
        );
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
    public static TransactionPack unpack(byte[] packOrContractBytes,boolean allowNonTransacions) throws IOException {
        packedBinary = packOrContractBytes;

        Object x = Boss.load(packOrContractBytes);

        if (x instanceof TransactionPack)
            return (TransactionPack) x;

        if(! allowNonTransacions)
            throw new IOException("expected transaction pack");

        // This is an old v2 self-contained contract or a root v3 contract, no revokes, no siblings.
        TransactionPack tp = new TransactionPack();
        Contract c = new Contract(packOrContractBytes, tp);
        tp.contracts.add(c);
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
    public static TransactionPack unpack(byte[] packOrContractBytes) throws IOException {
        return unpack(packOrContractBytes, true);
    }


    /**
     * Shortcut to {@link Boss#pack(Object)} for this.
     * @return
     */
    public byte[] pack() {
        if( packedBinary == null )
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
     * @param i index of the contract to retreive
     * @return ith contract from added
     */
    public Contract getContract(int i) {
        return contracts.get(0);
    }

    /**
     * Trace the tree of contracts references on the stdout.
     */
    public void trace() {
        System.out.println("Transaction pack");
        System.out.println("\tContracts:");
        for (Contract c : contracts) {
            System.out.println("\t\t" + c.getId());
            c.getNewItems().forEach(x -> System.out.println("\t\t\tnew: " + x.getId()));
            c.getRevokingItems().forEach(x -> System.out.println("\t\t\trevoke: " + x.getId()));
        }
        System.out.println("\tReferences:");
        references.forEach((hashId, contract) -> System.out.println("\t\t" + hashId + " -> " + contract.getId()));
    }
}
