/*
 * Copyright (c) 2018 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by WildCats <kalinkineo@gmail.com>, February 2018.
 *
 */

package com.icodici.universa.contract;

import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.node.network.TestKeys;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class ParcelTest {

    Parcel parcel;
    Parcel des_parcel;

    @Before
    public void setUp() throws Exception {
        TransactionPack payload_tp = new TransactionPack();
        TransactionPack payment_tp = new TransactionPack();

        //fill in with the values
        Contract payload = new Contract();
        payload.setIssuerKeys(TestKeys.publicKey(3));
        payload.addSignerKey(TestKeys.privateKey(3));
        payload.registerRole(new RoleLink("owner", "issuer"));
        payload.registerRole(new RoleLink("creator", "issuer"));
        payload.setExpiresAt(ZonedDateTime.now().plusDays(2));
        payload.seal();

        Contract payment = new Contract();
        payment.setIssuerKeys(TestKeys.publicKey(3));
        payment.addSignerKey(TestKeys.privateKey(3));
        payment.registerRole(new RoleLink("owner", "issuer"));
        payment.registerRole(new RoleLink("creator", "issuer"));
        payment.setExpiresAt(ZonedDateTime.now().plusDays(2));
        payment.seal();

        payload_tp.setContract(payload);
        payment_tp.setContract(payment);

        //create parcel
        parcel = new Parcel(payload_tp, payment_tp);
    }

    public Binder serialize(Parcel parcel) throws Exception {
        BiSerializer biS = new BiSerializer();
        return parcel.serialize(biS);
    }

    public Parcel deserialize(Binder binder) throws Exception {
        BiDeserializer biD = new BiDeserializer();
        return new Parcel(binder, biD);
    }

    public void parcelAssertions() throws Exception {
        //few assertions
        assertEquals(parcel.getPayload().getContract().getId(), des_parcel.getPayload().getContract().getId());
        assertEquals(parcel.getPayment().getContract().getId(), des_parcel.getPayment().getContract().getId());

        assertEquals(parcel.getPayload().getContract().getState().getBranchId(), des_parcel.getPayload().getContract().getState().getBranchId());
        assertEquals(parcel.getPayment().getContract().getState().getBranchId(), des_parcel.getPayment().getContract().getState().getBranchId());

        assertEquals(parcel.getPayload().getContract().getState().getCreatedAt().getSecond(), des_parcel.getPayload().getContract().getState().getCreatedAt().getSecond());
        assertEquals(parcel.getPayment().getContract().getState().getCreatedAt().getSecond(), des_parcel.getPayment().getContract().getState().getCreatedAt().getSecond());

        assertEquals(parcel.getPayload().getContract().getExpiresAt().getDayOfYear(), des_parcel.getPayload().getContract().getExpiresAt().getDayOfYear());
        assertEquals(parcel.getPayment().getContract().getExpiresAt().getDayOfYear(), des_parcel.getPayment().getContract().getExpiresAt().getDayOfYear());

        assertEquals(parcel.getPayload().getReferences(), des_parcel.getPayload().getReferences());
        assertEquals(parcel.getPayment().getReferences(), des_parcel.getPayment().getReferences());
    }

    @Test
    public void SerializeDeserialize() throws Exception {
        //serialize
        Binder b = serialize(parcel);

        //deserialize
        des_parcel = deserialize(b);

        parcelAssertions();
    }

    @Ignore
    @Test
    public void SerializeDeserializeWithPacking() throws Exception {
        //serialize
        Binder b = serialize(parcel);

        byte[] array = Boss.pack(b);
        Binder ub = Boss.unpack(array);

        //deserialize
        des_parcel = deserialize(ub);

        parcelAssertions();
    }
}