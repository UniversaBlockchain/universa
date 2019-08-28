package com.icodici.universa.contract.services;

import com.icodici.crypto.KeyAddress;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializable;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements {@link NameRecord} interface for UNS contract.
 */
public class NNameRecord implements NameRecord,BiSerializable {

    private long id = 0;
    private long environmentId = 0;
    private ZonedDateTime expiresAt;
    private String name;
    private String nameReduced;
    private String description;

    public NNameRecord() {

    }

    public NNameRecord(@NonNull UnsName unsName, @NonNull ZonedDateTime expiresAt) {
        name = unsName.getUnsName();
        nameReduced = unsName.getUnsReducedName();
        description = unsName.getUnsDescription();
        this.expiresAt = expiresAt;
    }

    public NNameRecord(@NonNull UnsName unsName, @NonNull ZonedDateTime expiresAt, Set<NNameRecordEntry> entries, long id, long environmentId) {
        this(unsName, expiresAt);
        this.id = id;
        this.environmentId = environmentId;
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


    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder data = new Binder();
        data.set("name", serializer.serialize(name));
        data.set("nameReduced", serializer.serialize(nameReduced));
        data.set("description", serializer.serialize(description));
        data.set("expiresAt", serializer.serialize(expiresAt));

        return data;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {

        name = data.getString("name");
        nameReduced = data.getString("nameReduced");
        description = data.getString("description",null);
        expiresAt = deserializer.deserialize(data.getZonedDateTimeOrThrow("expiresAt"));
    }
}

