package com.icodici.universa.node2;

import net.sergeych.biserializer.BiAdapter;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.tools.Binder;

public enum ParcelProcessingState {
    NOT_EXIST,
    INIT,
    DOWNLOADING,
    PREPARING,
    PAYMENT_CHECKING,
    PAYLOAD_CHECKING,
    RESYNCING,
    GOT_RESYNCED_STATE,
    PAYMENT_POLLING,
    PAYLOAD_POLLING,
    GOT_CONSENSUS,
    SENDING_CONSENSUS,
    FINISHED,
    EMERGENCY_BREAK;


    /**
     * Status should break other processes and possibility to launch processes.
     *
     * @return true if consensus found
     */
    public boolean isProcessedToConsensus() {
        switch (this) {
            case GOT_CONSENSUS:
            case SENDING_CONSENSUS:
            case FINISHED:
                return true;
        }
        return false;
    }

    public boolean isConsensusSentAndReceived() {
        return this == FINISHED;
    }

    public boolean isGotConsensus() {
        return this == GOT_CONSENSUS;
    }

    public boolean isGotResyncedState() {
        return this == GOT_RESYNCED_STATE;
    }

    public boolean isResyncing() {
        return this == RESYNCING;
    }

    public boolean canContinue() {
        return this != EMERGENCY_BREAK;
    }

    public boolean canRemoveSelf() {
        switch (this) {
            case EMERGENCY_BREAK:
            case FINISHED:
                return true;
        }
        return false;
    }

    public boolean isProcessing() {
        return canContinue() && this != FINISHED && this != NOT_EXIST;
    }

    public Binder toBinder() {
        return Binder.fromKeysValues(
                "state", name()
        );
    }

    static {
        DefaultBiMapper.registerAdapter(ParcelProcessingState.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                return ((ParcelProcessingState) object).toBinder();
            }

            @Override
            public ParcelProcessingState deserialize(Binder binder, BiDeserializer deserializer) {
                return ParcelProcessingState.valueOf(binder.getStringOrThrow("state"));
            }

            @Override
            public String typeName() {
                return "ParcelProcessingState";
            }
        });
    }
}
