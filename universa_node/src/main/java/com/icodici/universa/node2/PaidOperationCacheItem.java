package com.icodici.universa.node2;

import com.icodici.universa.ErrorRecord;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.PaidOperation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PaidOperationCacheItem {

    public HashId paidOperationId;
    public PaidOperation paidOperation = null;

    // for returning status to client
    public ParcelProcessingState processingState = ParcelProcessingState.INIT;
    public List<ErrorRecord> errors = new CopyOnWriteArrayList<>();

    HashId getId() {return paidOperationId;}

}
