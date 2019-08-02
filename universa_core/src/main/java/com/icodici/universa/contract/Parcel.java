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

    /**
     * Terms.
     <ul>
     <li><b>parcel</b>: the payment transaction and the payload transaction packed together.
     The unit of data that node expects from the client to perform approval.</li>

     <li><b>payload</b>: the client's transaction he or she needs to approve with the Universa</li>

     <li><b>payment</b>: the client's transaction that spends one or more U to pay for the payload processing.</li>

     <li><b>cost</b>: payload processing cost in U, positive integer.</li>

     <li><b>payment</b>: transaction in U contracts owned by the client reducing its remaining value by some value, or this value.</li>
     </ul>
     <br><br>
     This class implements Parcel.
     <br>
     Parcel sends via network as packed {@link Parcel#pack()} byte array. When the node get byte array it
     unpack it via {@link Parcel#unpack(byte[])}, while unpacking payment and payload is unpacking as {@link TransactionPack},
     then {@link Parcel#prepareForNode()} is called and unpacked contracts is preparing for the
     node (set need flags and quanta's limits).
     */
    public Parcel() {
    }

    /**
     * Terms.
     <ul>
     <li><b>parcel</b>: the payment transaction and the payload transaction packed together.
     The unit of data that node expects from the client to perform approval.</li>

     <li><b>payload</b>: the client's transaction he or she needs to approve with the Universa</li>

     <li><b>payment</b>: the client's transaction that spends one or more U to pay for the payload processing.</li>

     <li><b>cost</b>: payload processing cost in U, positive integer.</li>

     <li><b>payment</b>: transaction in U contracts owned by the client reducing its remaining value by some value, or this value.</li>
     </ul>
     <br><br>
     This class implements Parcel.
     <br>
     Parcel sends via network as packed {@link Parcel#pack()} byte array. When the node get byte array it
     unpack it via {@link Parcel#unpack(byte[])}, while unpacking payment and payload is unpacking as {@link TransactionPack},
     then {@link Parcel#prepareForNode()} is called and unpacked contracts is preparing for the
     node (set need flags and quanta's limits).
     */
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

    /**
     * Terms.
     <ul>
     <li><b>parcel</b>: the payment transaction and the payload transaction packed together.
     The unit of data that node expects from the client to perform approval.</li>

     <li><b>payload</b>: the client's transaction he or she needs to approve with the Universa</li>

     <li><b>payment</b>: the client's transaction that spends one or more U to pay for the payload processing.</li>

     <li><b>cost</b>: payload processing cost in U, positive integer.</li>

     <li><b>payment</b>: transaction in U contracts owned by the client reducing its remaining value by some value, or this value.</li>
     </ul>
     <br><br>
     This class implements Parcel.
     <br>
     Parcel sends via network as packed {@link Parcel#pack()} byte array. When the node get byte array it
     unpack it via {@link Parcel#unpack(byte[])}, while unpacking payment and payload is unpacking as {@link TransactionPack},
     then {@link Parcel#prepareForNode()} is called and unpacked contracts is preparing for the
     node (set need flags and quanta's limits).

     */
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


    /**
     * Method check parcel's specific behavior and prepare Parcel for the node. It do while unpacking on the Node.
     * First of all, method extract set payment in U amount convert it to quantas and set quantas for payload.
     * Method check if payment is test and set special flag in the payment and payload contracts.
     * And finally set special flag for payment that contract should be U and node should check it in special mode.
     */
    protected void prepareForNode() {

        // general idea - take U from payment's parent and take U from payment itself and calculate difference - it will be payment amount in U.
        // then check test or real payment by field names.
        Contract parent = null;
        for(Contract c : payment.getContract().getRevoking()) {
            if(c.getId().equals(payment.getContract().getParent())) {
                parent = c;
                break;
            }
        }
        if(parent != null) {
            boolean hasTestU = payment.getContract().getStateData().get("test_transaction_units") != null;
            // set pay quantasLimit for payload processing
            if (hasTestU) {
                isTestPayment = true;
                quantasLimit = Quantiser.quantaPerU * (
                        parent.getStateData().getIntOrThrow("test_transaction_units")
                                - payment.getContract().getStateData().getIntOrThrow("test_transaction_units")
                );
                if (quantasLimit <= 0) {
                    isTestPayment = false;
                    quantasLimit = Quantiser.quantaPerU * (
                            parent.getStateData().getIntOrThrow("transaction_units")
                                    - payment.getContract().getStateData().getIntOrThrow("transaction_units")
                    );
                }
            } else {
                isTestPayment = false;
                quantasLimit = Quantiser.quantaPerU * (
                        parent.getStateData().getIntOrThrow("transaction_units")
                                - payment.getContract().getStateData().getIntOrThrow("transaction_units")
                );
            }
        }

        payment.getContract().setShouldBeU(true);
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
        return Binder.of(
                "payload", payload.pack(),
                "payment", payment.pack(),
                "hashId", s.serialize(hashId));
    }

    @Override
    public synchronized void deserialize(Binder data, BiDeserializer ds) throws IOException {
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
