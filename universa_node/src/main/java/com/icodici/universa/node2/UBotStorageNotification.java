/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import net.sergeych.boss.Boss;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * The status notification for consensus creation procedure, carries information about some node item status and update
 * request
 */
public class UBotStorageNotification extends Notification {


    /**
     * If true, sending node asks receiving node to sent its status of this item back to sender. This overrides default
     * logic of sending only one broadcast about item status.
     *
     * @return
     */

    private static final int CODE_UBOT_STORAGE_NOTIFICATION = 6;
    private HashId value;
    private HashId executableContractId;
    private String storageName;
    private boolean hasConsensus;


    public UBotStorageNotification(NodeInfo from, HashId executableContractId, String storageName, HashId toValue, boolean hasConsensus) {
        super(from);
        this.hasConsensus = hasConsensus;
        this.executableContractId = executableContractId;
        this.storageName = storageName;
        this.value = toValue;
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        bw.writeObject(executableContractId.getDigest());
        bw.writeObject(storageName.getBytes());
        bw.writeObject(value.getDigest());
        bw.writeObject(hasConsensus ? 1 : 0);
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        executableContractId = HashId.withDigest(br.readBinary());
        storageName = new String(br.readBinary());
        value = HashId.withDigest(br.readBinary());
        hasConsensus = br.readInt() == 1;
    }

    protected UBotStorageNotification(NodeInfo from) throws IOException {
        super(from);
    }

    protected UBotStorageNotification() {
    }

    @Override
    protected int getTypeCode() {
        return CODE_UBOT_STORAGE_NOTIFICATION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UBotStorageNotification that = (UBotStorageNotification) o;

        NodeInfo from = getFrom();
        if (executableContractId.equals(that.executableContractId)) return false;
        if (storageName.equals(that.storageName)) return false;
        if (value.equals(that.value)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(executableContractId,storageName,value);
    }

    public String toString() {
        return "[UBotStorageNotification from " + getFrom()
                + " for item: " + executableContractId
                + ", storageName: " + storageName
                + ", value: " + value
                + "]";
    }

    static public void init() {
        registerClass(CODE_UBOT_STORAGE_NOTIFICATION, UBotStorageNotification.class);
    }

    public HashId getExecutableContractId() {
        return executableContractId;
    }

    public String getStorageName() {
        return storageName;
    }

    public HashId getValue() {
        return value;
    }

    public boolean hasConsensus() {
        return hasConsensus;
    }
}
