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

    private byte[] packedBinary;
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
            outputStream.write( payment.getContract().getId().getDigest().clone() );
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




    public TransactionPack getPayment() {
        return payment;
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
        System.out.println("Parcel serialize ");
        return Binder.of(
                "payload", s.serialize(payload),
                "payment", s.serialize(payment),
                "hashId", s.serialize(hashId));
    }

    @Override
    public synchronized void deserialize(Binder data, BiDeserializer ds) {
        System.out.println("Parcel deserialize ");
        payload = ds.deserialize(data.get("payload"));
        payment = ds.deserialize(data.get("payment"));
        hashId = ds.deserialize(data.get("hashId"));
    }

    /**
     * Unpack parcel.
     *
     * @param packOrContractBytes
     *
     * @return parcel
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
     * @return
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
