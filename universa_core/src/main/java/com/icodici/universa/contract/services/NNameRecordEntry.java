package com.icodici.universa.contract.services;

import com.icodici.crypto.KeyAddress;
import com.icodici.universa.HashId;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements {@link ContractStorageSubscription} interface for slot contract.
 */
public class NNameRecordEntry implements NameRecordEntry {

    private long id = 0;
    private long nameRecordId = 0;
    private HashId origin;
    private String longAddress;
    private String shortAddress;

    public NNameRecordEntry(HashId origin, String shortAddress, String longAddress) {
        this.origin = origin;
        this.longAddress = longAddress;
        this.shortAddress = shortAddress;
    }

    @Override
    public String getLongAddress() {
        return longAddress;
    }

    @Override
    public String getShortAddress() {
        return shortAddress;
    }

    @Override
    public HashId getOrigin() {
        return origin;
    }

    public long getNameRecordId() {
        return nameRecordId;
    }

    public void setNameRecordId(long nameRecordId) {
        this.nameRecordId = nameRecordId;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
