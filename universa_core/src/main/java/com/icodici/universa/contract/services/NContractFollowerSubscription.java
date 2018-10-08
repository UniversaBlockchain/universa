package com.icodici.universa.contract.services;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Contract;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Implements {@link NContractFollowerSubscription} interface for follower contract.
 */
public class NContractFollowerSubscription implements ContractSubscription, BiSerializable {

    private long id = 0;
    private long environmentId = 0;
    private ZonedDateTime expiresAt = ZonedDateTime.now().plusMonths(1);
    private ZonedDateTime mutedAt = ZonedDateTime.now().plusMonths(1);
    private boolean isReceiveEvents = false;
    private HashId origin;
    private double spent = 0;
    private int startedCallbacks = 0;

    public NContractFollowerSubscription() {

    }

    public NContractFollowerSubscription(HashId origin, ZonedDateTime expiresAt, ZonedDateTime mutedAt, double spent, int startedCallbacks) {
        this.origin = origin;
        this.expiresAt = expiresAt;
        this.mutedAt = mutedAt;
        this.spent = spent;
        this.startedCallbacks = startedCallbacks;
    }

    public NContractFollowerSubscription(HashId origin, ZonedDateTime expiresAt, ZonedDateTime mutedAt) {
        this.origin = origin;
        this.expiresAt = expiresAt;
        this.mutedAt = mutedAt;
    }

    @Override
    public void receiveEvents(boolean doReceive) {
        isReceiveEvents = doReceive;
    }

    @Override
    public ZonedDateTime expiresAt() {
        return expiresAt;
    }
    public ZonedDateTime mutedAt() {
        return mutedAt;
    }

    public void setExpiresAt(ZonedDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    public void setMutedAt(ZonedDateTime mutedAt) {
        this.mutedAt = mutedAt;
    }

    public void setId(long value) {
        id = value;
    }
    public long getId() {
        return id;
    }

    public void increaseCallbacksSpent(double addSpent) { spent += addSpent; }
    public double getCallbacksSpent() { return spent; }

    public void increaseStartedCallbacks() { startedCallbacks++; }
    public void decreaseStartedCallbacks() { startedCallbacks--; }
    public int getStartedCallbacks() { return startedCallbacks; }

    @Override
    public Contract getContract() {
        return null;
    }
    @Override
    public byte[] getPackedContract() {
        return null;
    }
    @Override
    public HashId getOrigin() {
        return origin;
    }


    public boolean isReceiveEvents() {
        return isReceiveEvents;
    }

    public long getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(long environmentId) {
        this.environmentId = environmentId;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) throws IOException {
        origin = deserializer.deserialize(data.get("origin"));
        expiresAt = data.getZonedDateTimeOrThrow("expiresAt");
        mutedAt = data.getZonedDateTimeOrThrow("mutedAt");
        spent = data.getDouble("spent");
        startedCallbacks = data.getIntOrThrow("startedCallbacks");
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        data.put("origin",serializer.serialize(origin));
        data.put("expiresAt", serializer.serialize(expiresAt));
        data.put("mutedAt", serializer.serialize(mutedAt));
        data.put("spent", spent);
        data.put("startedCallbacks", startedCallbacks);
        return data;
    }
}
