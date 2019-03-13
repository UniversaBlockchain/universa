package com.icodici.universa.contract.services;

import com.icodici.universa.node.Ledger;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.time.ZonedDateTime;

/**
 * Implements {@link ContractSubscription} interface for follower contract.
 */
public class NFollowerService implements FollowerService, BiSerializable {

    private long id = 0;
    private long environmentId;
    private ZonedDateTime expiresAt = ZonedDateTime.now().plusMonths(1);
    private ZonedDateTime mutedAt = ZonedDateTime.now().plusMonths(1);
    private double spent = 0;
    private int startedCallbacks = 0;
    private Ledger ledger;

    public NFollowerService() {}

    public NFollowerService(Ledger ledger, long environmentId) {
        this.ledger = ledger;
        this.environmentId = environmentId;
    }

    public NFollowerService(Ledger ledger, ZonedDateTime expiresAt, ZonedDateTime mutedAt, long environmentId, double spent, int startedCallbacks) {
        this.ledger = ledger;
        this.environmentId = environmentId;
        this.expiresAt = expiresAt;
        this.mutedAt = mutedAt;
        this.spent = spent;
        this.startedCallbacks = startedCallbacks;
    }

    @Override
    public ZonedDateTime expiresAt() {
        return expiresAt;
    }
    public ZonedDateTime mutedAt() {
        return mutedAt;
    }

    @Override
    public void setExpiresAt(ZonedDateTime expiresAt) { this.expiresAt = expiresAt; }

    @Override
    public void setMutedAt(ZonedDateTime mutedAt) {
        this.mutedAt = mutedAt;
    }

    @Override
    public void setExpiresAndMutedAt(ZonedDateTime expiresAt, ZonedDateTime mutedAt) {
        this.expiresAt = expiresAt;
        this.mutedAt = mutedAt;
    }

    @Override
    public void decreaseExpiresAt(int decreaseSeconds) {
        expiresAt = expiresAt.minusSeconds(decreaseSeconds);
    }

    @Override
    public void changeMutedAt(int deltaSeconds) {
        mutedAt = mutedAt.plusSeconds(deltaSeconds);
    }

    public void setId(long value) {
        id = value;
    }
    public long getId() {
        return id;
    }

    @Override
    public void increaseCallbacksSpent(double addSpent) { spent += addSpent; }

    @Override
    public double getCallbacksSpent() { return spent; }

    @Override
    public void increaseStartedCallbacks() { startedCallbacks++; }

    @Override
    public void decreaseStartedCallbacks() { startedCallbacks--; }

    @Override
    public int getStartedCallbacks() { return startedCallbacks; }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) throws IOException {
        expiresAt = data.getZonedDateTimeOrThrow("expiresAt");
        mutedAt = data.getZonedDateTimeOrThrow("mutedAt");
        spent = data.getDouble("spent");
        startedCallbacks = data.getIntOrThrow("startedCallbacks");
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        data.put("expiresAt", serializer.serialize(expiresAt));
        data.put("mutedAt", serializer.serialize(mutedAt));
        data.put("spent", spent);
        data.put("startedCallbacks", startedCallbacks);
        return data;
    }

    @Override
    public void save() {
        ledger.saveFollowerEnvironment(environmentId, expiresAt, mutedAt, spent, startedCallbacks);
    }
}
