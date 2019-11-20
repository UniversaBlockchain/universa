package com.icodici.universa.contract;

import com.icodici.universa.HashId;
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
     * Pack stored payment and operation data to binary, recalculate packedBinary and hashId.
     * @return a packed binary
     */
    public byte[] pack() {
        packedBinary = Boss.pack(this);
        hashId = HashId.of(packedBinary);
        return packedBinary;
    }

    /**
     * Return packed binary. Does pack() only if it was not called previously, else return cached binary.
     * @return a packed binary
     */
    public byte[] getPackedBinary() {
        if (packedBinary == null)
            pack();
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

    static {
        DefaultBiMapper.registerClass(PaidOperation.class);
    }

}
