/*
 * Copyright (c) 2018, All Rights Reserved
 *
 * Written by Leonid Novikov <nil182@mail.ru>
 */

package com.icodici.universa.contract;

import com.icodici.universa.HashId;
import net.sergeych.biserializer.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.utils.Bytes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


@BiType(name = "Parcel")
public class Parcel implements BiSerializable {

    private static byte[] packedBinary;
    private TransactionPack payload = null;
    private TransactionPack payment = null;
    private HashId hashId;

    public Parcel() {
    }

    public Parcel(TransactionPack payload, TransactionPack payment) {

        this.payload = payload;
        this.payment = payment;

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
            outputStream.write( payload.getContract().getId().getDigest().clone() );
            outputStream.write( payload.getContract().getId().getDigest().clone() );

            byte[] bytes = outputStream.toByteArray( );

            hashId = HashId.of(bytes);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //New constructor for initializing an object from a binder when deserializing
    public Parcel(Binder data)
    {
        BiDeserializer biD = new BiDeserializer();
        deserialize(data, biD);
    }


    public TransactionPack getPayload() {
        return payload;
    }



//    public void setPayload(TransactionPack payload) {
//        this.payload = payload;
//    }



    public TransactionPack getPayment() {
        return payment;
    }



//    public void setPayment(TransactionPack payment) {
//        this.payment = payment;
//    }



    public Contract getPayloadContract() {
        if (payload != null)
            return payload.getContract();
        return null;
    }



//    public void setPayloadContract(Contract c) {
//        if (payload == null)
//            payload = new TransactionPack();
//        payload.setContract(c);
//    }



    public Contract getPaymentContract() {
        if (payment != null)
            return payment.getContract();
        return null;
    }



//    public void setPaymentContract(Contract c) {
//        if (payment == null)
//            payment = new TransactionPack();
//        payment.setContract(c);
//    }



    public HashId getId() {
        return hashId;
    }


    @Override
    public Binder serialize(BiSerializer s) {
        return Binder.of(
                "payload", s.serialize(payload),
                "payment", s.serialize(payment)
        );
    }

    @Override
    public void deserialize(Binder data, BiDeserializer ds) {
        payload = ds.deserialize(data.get("payload"));
        payment = ds.deserialize(data.get("payment"));
    }

    /**
     * Unpack parcel
     * @param packOrContractBytes
     * @param allowNonTransactions
     *
     * @return transaction
     */
    public static Parcel unpack(byte[] packOrContractBytes, boolean allowNonTransactions) throws IOException {
        packedBinary = packOrContractBytes;

        Object x = Boss.load(packOrContractBytes);

        if (x instanceof Parcel)
            return (Parcel) x;

        if (!allowNonTransactions)
            throw new IOException("expected parcel");

        return null;
    }

    /**
     * Unpack parcel
     *
     * @param packOrContractBytes
     *
     * @return parcel
     */
    public static Parcel unpack(byte[] packOrContractBytes) throws IOException {
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

    static {
        DefaultBiMapper.registerClass(Parcel.class);
    }

}
