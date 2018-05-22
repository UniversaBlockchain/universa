package com.icodici.universa.contract.services;

import com.icodici.crypto.KeyAddress;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements {@link ContractStorageSubscription} interface for slot contract.
 */
public class NNameRecord implements NameRecord {

    private long id = 0;
    private long environmentId = 0;
    private ZonedDateTime expiresAt;
    private String name;
    private String nameReduced;
    private String description;
    private String url;
    private Set<NNameRecordEntry> entries = new HashSet<>();

    public NNameRecord(@NonNull UnsName unsName, @NonNull ZonedDateTime expiresAt) {
        name = unsName.getUnsName();
        this.expiresAt = expiresAt;
        unsName.getUnsRecords().forEach(unsRecord -> {
            String longAddress = null;
            String shortAddress = null;
            for(KeyAddress keyAddress : unsRecord.getAddresses()) {
                if(keyAddress.isLong())
                    longAddress = keyAddress.toString();
                else
                    shortAddress = keyAddress.toString();
            }
            entries.add(new NNameRecordEntry(unsRecord.getOrigin(),shortAddress,longAddress));
        });
    }


    @Override
    public ZonedDateTime expiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(ZonedDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNameReduced() {
        return nameReduced;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public Collection<NameRecordEntry> getEntries() {
        return new HashSet<>(entries);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getEnvironmentId() {
        return environmentId;
    }

    public void setEnvironmentId(long environmentId) {
        this.environmentId = environmentId;
    }
}
