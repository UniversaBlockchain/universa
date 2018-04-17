/*
 * Copyright (c) 2018, All Rights Reserved
 *
 * Written by Leonid Novikov <flint.emerald@gmail.com>
 */

package com.icodici.universa.contract;

import com.icodici.universa.HashId;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


@BiType(name = "Parcel")
public class Parcel implements BiSerializable {

    private byte[] packedBinary;
    private TransactionPack payload = null;
    private TransactionPack payment = null;
    private HashId hashId;

    public int getQuantasLimit() {
        return quantasLimit;
    }

    private int quantasLimit = 0;
    private boolean isTestPayment = false;

    public Parcel() {
    }

    public Parcel(TransactionPack payload, TransactionPack payment) {

        this.payload = payload;
        this.payment = payment;

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            outputStream.write( payment.getContract().getId().getDigest().clone() );
            outputStream.write( payload.getContract().getId().getDigest().clone() );

            byte[] bytes = outputStream.toByteArray( );

            hashId = HashId.of(bytes);

        } catch (IOException e) {
            e.printStackTrace();
        }

        prepareForNode();
    }

    //New constructor for initializing an object from a binder when deserializing
    public Parcel(Binder data) throws IOException {
        BiDeserializer biD = new BiDeserializer();
        deserialize(data, biD);
    }


    public TransactionPack getPayload() {
        return payload;
    }




    public TransactionPack getPayment() {
        return payment;
    }



    public void prepareForNode() {

        Contract parent = null;
        for(Contract c : payment.getContract().getRevoking()) {
            if(c.getId().equals(payment.getContract().getParent())) {
                parent = c;
                break;
            }
        }
        if(parent != null) {
            boolean hasTestTU = payment.getContract().getStateData().get("test_transaction_units") != null;
            // set pay quantasLimit for payload processing
            if (hasTestTU) {
                isTestPayment = true;
                quantasLimit = Quantiser.quantaPerUTN * (
                        parent.getStateData().getIntOrThrow("test_transaction_units")
                                - payment.getContract().getStateData().getIntOrThrow("test_transaction_units")
                );
                if (quantasLimit <= 0) {
                    isTestPayment = false;
                    quantasLimit = Quantiser.quantaPerUTN * (
                            parent.getStateData().getIntOrThrow("transaction_units")
                                    - payment.getContract().getStateData().getIntOrThrow("transaction_units")
                    );
                }
            } else {
                isTestPayment = false;
                quantasLimit = Quantiser.quantaPerUTN * (
                        parent.getStateData().getIntOrThrow("transaction_units")
                                - payment.getContract().getStateData().getIntOrThrow("transaction_units")
                );
            }
        }
        payment.getContract().setShouldBeTU(true);
        payment.getContract().setLimitedForTestnet(isTestPayment);
        payload.getContract().setLimitedForTestnet(isTestPayment);
        payload.getContract().getNew().forEach(c -> c.setLimitedForTestnet(isTestPayment));
    }




    public Contract getPayloadContract() {
        if (payload != null)
            return payload.getContract();
        return null;
    }




    public Contract getPaymentContract() {
        if (payment != null)
            return payment.getContract();
        return null;
    }



    public HashId getId() {
        return hashId;
    }


    @Override
    public synchronized Binder serialize(BiSerializer s) {
//        System.out.println("Parcel serialize ");
        return Binder.of(
                "payload", payload.pack(),
                "payment", payment.pack(),
                "hashId", s.serialize(hashId));
    }

    @Override
    public synchronized void deserialize(Binder data, BiDeserializer ds) throws IOException {
//        System.out.println("Parcel deserialize ");
        payload = TransactionPack.unpack(data.getBinary("payload"));
        payment = TransactionPack.unpack(data.getBinary("payment"));
        hashId = ds.deserialize(data.get("hashId"));

        prepareForNode();
    }

    /**
     * Unpack parcel.
     *
     * @param packOrContractBytes is binary that was packed by {@link Parcel#pack()}
     *
     * @return a {@link Parcel}
     * @throws IOException if something went wrong
     */
    public synchronized static Parcel unpack(byte[] packOrContractBytes) throws IOException {

        Object x = Boss.load(packOrContractBytes);

        if (x instanceof Parcel) {
            ((Parcel) x).packedBinary = packOrContractBytes;
            return (Parcel) x;
        }

        return null;
    }


    /**
     * Shortcut to {@link Boss#pack(Object)} for this.
     *
     * @return a packed binary
     */
    public synchronized byte[] pack() {
        if (packedBinary == null)
            packedBinary = Boss.pack(this);
        return packedBinary;
    }

    static {
        DefaultBiMapper.registerClass(Parcel.class);
    }

}
