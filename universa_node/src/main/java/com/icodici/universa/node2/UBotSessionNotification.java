/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import net.sergeych.boss.Boss;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * The status notification for consensus creation procedure, carries information about some node item status and update
 * request
 */
public class UBotSessionNotification extends Notification {


    /**
     * If true, sending node asks receiving node to sent its status of this item back to sender. This overrides default
     * logic of sending only one broadcast about item status.
     *
     * @return
     */

    private static final int CODE_UBOT_SESSION_NOTIFICATION = 4;
    private boolean doAnswer;
    private boolean haveRequestContract;

    private HashId executableContractId;
    private List<Object> payload;

    public List<Object> getPayload() {
        return payload;
    }

    public HashId getExecutableContractId() {
        return executableContractId;
    }

    public boolean isHaveRequestContract() {
        return haveRequestContract;
    }

    public boolean isDoAnswer() {
        return doAnswer;
    }

    public UBotSessionNotification(NodeInfo from, HashId executableContractId, boolean doAnswer, boolean haveRequestContract, List<Object> payload) {
        super(from);
        this.executableContractId = executableContractId;
        this.payload = payload;
        this.haveRequestContract = haveRequestContract;
        this.doAnswer = doAnswer;
    }

    @Override
    protected void writeTo(Boss.Writer bw) throws IOException {
        bw.writeObject(executableContractId.getDigest());
        int flags = 0;
        if(haveRequestContract) {
            flags = flags | 1;
        }
        if(doAnswer) {
            flags = flags | 2;
        }
        bw.write(flags);
        bw.writeObject(payload);
    }

    @Override
    protected void readFrom(Boss.Reader br) throws IOException {
        executableContractId = HashId.withDigest(br.readBinary());
        int flags = br.readInt();
        haveRequestContract = (flags & 1) > 0;
        doAnswer = (flags & 2) > 0;
        payload = br.read();
    }

    protected UBotSessionNotification(NodeInfo from) throws IOException {
        super(from);
    }

    protected UBotSessionNotification() {
    }

    @Override
    protected int getTypeCode() {
        return CODE_UBOT_SESSION_NOTIFICATION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UBotSessionNotification that = (UBotSessionNotification) o;

        NodeInfo from = getFrom();
        if (executableContractId.equals(that.executableContractId)) return false;
        if (!payload.equals(that.payload)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(executableContractId,payload);
    }

    public String toString() {
        return "[ItemNotification from " + getFrom()
                + " for item: " + executableContractId
                + ", item payload: " + payload
                + "]";
    }

    static public void init() {
        registerClass(CODE_UBOT_SESSION_NOTIFICATION, UBotSessionNotification.class);
    }
}
