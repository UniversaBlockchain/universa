/*
 * Copyright (c) 2018, All Rights Reserved
 *
 * Written by Leonid Novikov <nil182@mail.ru>
 */

package com.icodici.universa.contract;

import com.icodici.universa.HashId;
import net.sergeych.biserializer.*;
import net.sergeych.tools.Binder;



@BiType(name = "Parcel")
public class Parcel implements BiSerializable {

    private TransactionPack payload = null;
    private TransactionPack payment = null;


    public TransactionPack getPayload() {
        return payload;
    }



    public void setPayload(TransactionPack payload) {
        this.payload = payload;
    }



    public TransactionPack getPayment() {
        return payment;
    }



    public void setPayment(TransactionPack payment) {
        this.payment = payment;
    }


    public HashId getId() {
        if(payload != null)
            return payload.getContract().getId();

        return null;
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



    static {
        DefaultBiMapper.registerClass(Parcel.class);
    }

}
