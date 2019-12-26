/*
 * Copyright (c) 2018, iCodici S.n.C, All Rights Reserved
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.PaidOperation;
import net.sergeych.utils.Base64;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class PaidOperationCache {

    private final Timer cleanerTimer = new Timer();
    private final Duration maxAge;

    public PaidOperationCache(Duration maxAge) {
        this.maxAge = maxAge;
        cleanerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                cleanUp();
            }
        }, 5000, 5000);
    }

    final void cleanUp() {
        // we should avoid creating an object for each check:
        Instant now = Instant.now();
        records.values().forEach(r->r.checkExpiration(now));
    }

    public void shutdown() {
        cleanerTimer.cancel();
        cleanerTimer.purge();
    }

    public @Nullable PaidOperationCacheItem get(HashId itemId) {
        Record i = records.get(itemId);
        if( i != null && i.paidOperation == null )
            throw new RuntimeException("cache: record with empty paidOperation");
        return i != null ? i.paidOperation : null;
    }

    public void put(PaidOperationCacheItem paidOperation) {
        // this will plainly override current if any
        new Record(paidOperation);
    }

    private ConcurrentHashMap<HashId,Record> records = new ConcurrentHashMap();

    public void idsCheck(HashId itemId) {
        for(HashId x: records.keySet()) {
            System.out.println(" checking "+itemId+" eq "+x+ ": "+itemId.equals(x) + " / " + x.equals(itemId) );
            System.out.println(" codes: "+itemId.hashCode() + " / "+ x.hashCode());
            System.out.println(" digest check: "+ Base64.encodeString(itemId.getDigest()));
            System.out.println(" digest data : "+ Base64.encodeString(x.getDigest()));
        }
    }

    public int size() {
        return records.size();
    }

    private class Record {
        private Instant expiresAt;
        private PaidOperationCacheItem paidOperation;

        private Record(PaidOperationCacheItem paidOperation) {
            expiresAt = Instant.now().plus(maxAge);
            this.paidOperation = paidOperation;
            records.put(paidOperation.getId(), this);
        }

        private void checkExpiration(Instant now) {
            if( expiresAt.isBefore(now) ) {
                //System.out.println("cache expired "+paidOperation.getId());
                records.remove(paidOperation.getId());
            }
        }
    }
}
