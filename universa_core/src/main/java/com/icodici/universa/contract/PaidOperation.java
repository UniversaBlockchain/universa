package com.icodici.universa.contract;

import com.icodici.universa.HashId;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.*;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

import java.io.IOException;

@BiType(name = "PaidOperation")
public class PaidOperation implements BiSerializable {

    private TransactionPack payment = null;
    private Binder operation = null;

    private byte[] packedBinary = null;
    private HashId hashId = null;

    private int quantaLimit = 0;
    private boolean isTestPayment = false;

    public PaidOperation() {
    }

    public PaidOperation(TransactionPack payment, String operationType, Binder operationData) {
        this.payment = payment;
        this.operation = Binder.of("operationType", operationType, "operationData", operationData);
        this.packedBinary = pack();
    }

    public TransactionPack getPayment() {
        return payment;
    }

    public Contract getPaymentContract() {
        if (payment != null)
            return payment.getContract();
        return null;
    }

    public String getOperationType() {
        return operation.getStringOrThrow("operationType");
    }

    public Binder getOperationData() {
        return operation.getBinderOrThrow("operationData");
    }

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
                quantaLimit = Quantiser.quantaPerU * (
                        parent.getStateData().getIntOrThrow("test_transaction_units")
                                - payment.getContract().getStateData().getIntOrThrow("test_transaction_units")
                );
                if (quantaLimit <= 0) {
                    isTestPayment = false;
                    quantaLimit = Quantiser.quantaPerU * (
                            parent.getStateData().getIntOrThrow("transaction_units")
                                    - payment.getContract().getStateData().getIntOrThrow("transaction_units")
                    );
                }
            } else {
                isTestPayment = false;
                quantaLimit = Quantiser.quantaPerU * (
                        parent.getStateData().getIntOrThrow("transaction_units")
                                - payment.getContract().getStateData().getIntOrThrow("transaction_units")
                );
            }
        }

        payment.getContract().setShouldBeU(true);
    }

    @Override
    public synchronized Binder serialize(BiSerializer s) {
        return Binder.of(
                "payment", payment.pack(),
                "operation", operation
        );
    }

    @Override
    public synchronized void deserialize(Binder data, BiDeserializer ds) throws IOException {
        payment = TransactionPack.unpack(data.getBinary("payment"));
        operation = data.getBinder("operation");

        prepareForNode();
    }

    /**
     * Unpack PaidOperation from binary.
     * @param packedBin binary result from {@link PaidOperation#pack()}
     * @return {@link PaidOperation}
     */
    public static PaidOperation unpack(byte[] packedBin) {
        Object x = Boss.load(packedBin);
        if (x instanceof PaidOperation) {
            PaidOperation po = (PaidOperation) x;
            po.packedBinary = packedBin;
            po.hashId = HashId.of(packedBin);
            return po;
        }
        return null;
    }

    /**
     * Return packed binary. Does recreatePackedBinary() only if it was not called previously, else return cached binary.
     * @return a packed binary
     */
    public byte[] pack() {
        if (packedBinary == null)
            recreatePackedBinary();
        return packedBinary;
    }

    /**
     * Pack stored payment and operation data to binary, recalculate packedBinary and hashId.
     * @return a packed binary
     */
    public byte[] recreatePackedBinary() {
        packedBinary = Boss.pack(this);
        hashId = HashId.of(packedBinary);
        return packedBinary;
    }

    /**
     * @return {@link HashId} of packedBinary.
     */
    public HashId getId() {
        if (hashId == null)
            pack();
        return hashId;
    }

    public int getQuantaLimit() {
        return quantaLimit;
    }

    static {
        DefaultBiMapper.registerClass(PaidOperation.class);
    }

}
