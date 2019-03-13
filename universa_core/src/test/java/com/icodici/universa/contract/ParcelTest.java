/*
 * Copyright (c) 2018 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by WildCats <kalinkineo@gmail.com>, February 2018.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.TestKeys;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;
import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;

public class ParcelTest  {
    protected String rootPath = "./src/test_contracts/";
    protected final String ROOT_CONTRACT = rootPath + "simple_root_contract.yml";

    Parcel parcel;
    Parcel des_parcel;
    Parcel parcelFromFile;
    Parcel des_parcelFromFile;

    @Before
    public void setUp() throws Exception {

        TransactionPack payloadTpFromFile = new TransactionPack();
        TransactionPack paymentTpFromFile = new TransactionPack();
        TransactionPack payload_tp = new TransactionPack();
        TransactionPack payment_tp = new TransactionPack();

        //fill in with the values
        Contract payload = new Contract();
        payload.setIssuerKeys(TestKeys.publicKey(3));
        payload.addSignerKey(TestKeys.privateKey(3));
        payload.registerRole(new RoleLink("owner", "issuer"));
        payload.registerRole(new RoleLink("creator", "issuer"));
        payload.setExpiresAt(ZonedDateTime.now().plusDays(2));
        payload.addNewItems(new Contract(TestKeys.privateKey(3)));
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

        parcel = new Parcel(payload_tp, payment_tp);

        PrivateKey privateKey = TestKeys.privateKey(3);

        Contract payloadFromFile = Contract.fromDslFile(ROOT_CONTRACT);
        payloadFromFile.addSignerKey(privateKey);
        payloadFromFile.seal();

        Contract paymentFromFile = Contract.fromDslFile(ROOT_CONTRACT);
        paymentFromFile.addSignerKey(privateKey);
        paymentFromFile.seal();

        payloadTpFromFile.setContract(payloadFromFile);
        paymentTpFromFile.setContract(paymentFromFile);

        parcelFromFile = new Parcel(payloadTpFromFile, paymentTpFromFile);
    }

    public Binder serialize(Parcel parcel) throws Exception {
        BiSerializer biS = new BiSerializer();
        return parcel.serialize(biS);
    }

    public Parcel deserialize(Binder binder) throws Exception {
        return new Parcel(binder);
    }

    public void parcelAssertions(Parcel equal1, Parcel equal2 ) throws Exception {
        //few assertions
        assertEquals(equal1.getPayload().getContract().getId(), equal2.getPayload().getContract().getId());
        assertEquals(equal1.getPayment().getContract().getId(), equal2.getPayment().getContract().getId());

        assertEquals(equal1.getPayload().getContract().getState().getBranchId(), equal2.getPayload().getContract().getState().getBranchId());
        assertEquals(equal1.getPayment().getContract().getState().getBranchId(), equal2.getPayment().getContract().getState().getBranchId());

        assertEquals(equal1.getPayload().getContract().getState().getCreatedAt().getSecond(), equal2.getPayload().getContract().getState().getCreatedAt().getSecond());
        assertEquals(equal1.getPayment().getContract().getState().getCreatedAt().getSecond(), equal2.getPayment().getContract().getState().getCreatedAt().getSecond());

        assertEquals(equal1.getPayload().getContract().getExpiresAt().getDayOfYear(), equal2.getPayload().getContract().getExpiresAt().getDayOfYear());
        assertEquals(equal1.getPayment().getContract().getExpiresAt().getDayOfYear(), equal2.getPayment().getContract().getExpiresAt().getDayOfYear());

        assertEquals(equal1.getPayload().getSubItems().size(), equal2.getPayload().getSubItems().size());
        assertEquals(equal1.getPayment().getSubItems(), equal2.getPayment().getSubItems());
    }


    @Test
    public void serializeDeserialize() throws Exception {
        //serialize
        Binder b1 = serialize(parcel);
        Binder b2 = serialize(parcelFromFile);

        //deserialize
        des_parcel = deserialize(b1);
        des_parcelFromFile = deserialize(b2);

        parcelAssertions(parcel, des_parcel);
        parcelAssertions(des_parcelFromFile, des_parcelFromFile);

        assertEquals(1, des_parcel.getPayload().getSubItems().size());
        assertEquals(1, des_parcel.getPayload().getContract().getNew().size());
    }

    @Test
    public void packUnpack() throws Exception {
        //pack
        byte[] array = parcel.pack();
        byte[] array1 = parcelFromFile.pack();

        //unpack
        des_parcel = Parcel.unpack(array);
        des_parcelFromFile = Parcel.unpack(array1);

        parcelAssertions(parcel, des_parcel);
        parcelAssertions(parcelFromFile, des_parcelFromFile);

        assertEquals(1, des_parcel.getPayload().getSubItems().size());
        assertEquals(1, des_parcel.getPayload().getContract().getNew().size());
    }
}