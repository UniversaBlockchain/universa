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
 * The bundle of linked contracts to be passed to the network to perform the operation. Constists of contracts to
 * approve, their counterparts (revoking versions, siblings), optional contracts to identify and to pay the transaction
 * and other stuff needed by the network to properly process.
 * <p>
 * A word of terminology.
 * <p>
 * We name as the <i>transacton contract</i> any contract that will be passed to the network for verification and
 * approval, it could and should contain other contracts as revoking end new items. These <i>referenced</i> contracts do
 * not need to be addded separately.
 * <p>
 * This implementatino is not thread safe. Synchronize your access if need.
 */
@BiType(name = "TransactionPack")
public class TransactionPack implements BiSerializable {

    private Map<HashId, Contract> references = new HashMap<>();
    private List<Contract> contracts = new ArrayList<>();

    public TransactionPack(Contract contract) {
        this();
        addContract(contract);
    }

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
     *
     * @param c contract to append to the list of transactions.
     */
    public void addContract(Contract c) {
        if (contracts.contains(c))
            throw new IllegalArgumentException("the contract is already added");
        contracts.add(c);
        c.getRevokingItems().forEach(i -> putReference((Contract) i));
        c.getNewItems().forEach(i -> putReference((Contract) i));
    }

    /**
     * Direct add the reference. Not recommended as {@link #addContract(Contract)} already does it for all referenced
     * contracts. Use it to add references not mentioned in the added contracts.
     *
     * @param reference
     */
    public void addReference(Contract reference) {
        references.put(reference.getId(), reference);
    }

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
     * Unpack either old contract binary (all included), or newer transaction pack.
     *
     * @param packOrContractBytes
     *
     * @return
     */
    public static TransactionPack unpack(byte[] packOrContractBytes) throws IOException {
        Object x = Boss.load(packOrContractBytes);

        if (x instanceof TransactionPack)
            return (TransactionPack) x;

        // This is an old v7 self-contained contract
        TransactionPack tp = new TransactionPack();
        Contract c = new Contract(packOrContractBytes, (Binder) x, tp);
        tp.contracts.add(c);
        return tp;
    }

    public byte[] pack() {
        return Boss.pack(this);
    }

    public Map<HashId, Contract> getReferences() {
        return references;
    }

    static {
        DefaultBiMapper.registerClass(TransactionPack.class);
    }

    public Contract getContract(int i) {
        return contracts.get(0);
    }

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
