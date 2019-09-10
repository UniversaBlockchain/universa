/*
 * Copyright (c) 2018 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by WildCats <kalinkineo@gmail.com>, February 2018.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PrivateKey;
import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.helpers.Compound;
import com.icodici.universa.contract.roles.RoleLink;
import com.icodici.universa.TestKeys;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.junit.Before;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void getRemainingUAutoCreated() throws Exception {
        int amount = 100000;
        Contract u = InnerContractsService.createFreshU(amount, new HashSet<>(Do.listOf(TestKeys.publicKey(1))));
        u.seal();
        assertEquals(u.getRevision(),1);
        Contract payload = new Contract(TestKeys.privateKey(2));
        payload.seal();

        Parcel p = Parcel.of(payload,u,Do.listOf(TestKeys.privateKey(1)));
        Contract u2 = p.getRemainingU();
        amount -= payload.getProcessedCostU();
        assertEquals(u2.getRevision(),2);
        assertEquals(u2.getStateData().get("transaction_units"),amount);
        int payingAmount = 100;
        p.addPayingAmount(payingAmount,Do.listOf(TestKeys.privateKey(1)),Do.listOf(TestKeys.privateKey(2)));
        amount -= payingAmount;
        Contract u3 = p.getRemainingU();
        assertEquals(u3.getRevision(),3);
        assertEquals(u3.getStateData().get("transaction_units"),amount);

    }


    private static Contract createPayment(Contract uContract, Collection<PrivateKey> uKeys, int amount, boolean withTestPayment) throws Parcel.InsufficientFundsException, Parcel.OwnerNotResolvedException {
        Contract payment = uContract.createRevision(uKeys);
        String fieldName = withTestPayment ? "test_transaction_units" : "transaction_units";
        payment.getStateData().set(fieldName, uContract.getStateData().getIntOrThrow(fieldName) - amount);
        payment.seal();
        try {
            payment.getQuantiser().resetNoLimit();
            if(!payment.check()) {
                if(!payment.getOwner().isAllowedForKeys(new HashSet<>(uKeys))) {
                    throw new Parcel.OwnerNotResolvedException("Unable to create payment: Check that provided keys are enough to resolve U-contract owner.");
                } else if(payment.getStateData().getIntOrThrow(fieldName) < 0 ) {
                    throw new Parcel.InsufficientFundsException("Unable to create payment: Check provided U-contract to have at least " + amount + (withTestPayment ? " test":"") + " units available.");
                } else {
                    throw new Parcel.BadPaymentException("Unable to create payment: " + payment.getErrorsString());
                }
            }
        } catch (Quantiser.QuantiserException ignored) {

        }

        return payment;
    }

    @Test
    public void getRemainingUManualCreated() throws Exception {
        int amount = 100000;
        Contract u = InnerContractsService.createFreshU(amount, new HashSet<>(Do.listOf(TestKeys.publicKey(1))));
        u.seal();
        assertEquals(u.getRevision(),1);


        Contract u2 = createPayment(u,Do.listOf(TestKeys.privateKey(1)),20,false);
        amount -= 20;

        Contract payload1 = new Contract(TestKeys.privateKey(2));
        Contract u3 = createPayment(u2,Do.listOf(TestKeys.privateKey(1)),200,false);
        amount -= 200;
        payload1.addNewItems(u3);
        payload1.seal();

        Contract payload2 = new Contract(TestKeys.privateKey(2));
        Contract u4 = createPayment(u3,Do.listOf(TestKeys.privateKey(1)),300,false);
        amount -= 300;
        payload2.addNewItems(u4);
        payload2.seal();

        Contract payload3 = new Contract(TestKeys.privateKey(2));
        payload3.seal();

        Contract payload4 = new Contract(TestKeys.privateKey(2));
        Contract u5 = createPayment(u4,Do.listOf(TestKeys.privateKey(1)),302,false);
        amount -= 302;
        payload4.addNewItems(u5);
        payload4.seal();


        Compound compound = new Compound();
        compound.addContract("tag1",payload1,new Binder());
        compound.addContract("tag2",payload2,new Binder());
        compound.addContract("tag3",payload3,new Binder());
        compound.addContract("tag4",payload4,new Binder());



        Parcel p = new Parcel(compound.getCompoundContract().getTransactionPack(),u2.getTransactionPack());
        p = Parcel.unpack(p.pack());
        Contract remaining = p.getRemainingU();
        assertEquals(remaining.getRevision(),5);
        assertEquals(remaining.getStateData().get("transaction_units"),amount);

    }
}