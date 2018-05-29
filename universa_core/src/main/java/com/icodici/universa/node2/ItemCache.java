/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import net.sergeych.utils.Base64;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class ItemCache {

    private final Timer cleanerTimer = new Timer();
    private final Duration maxAge;

    public ItemCache(Duration maxAge) {
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

    public @Nullable Approvable get(HashId itemId) {
        Record i = records.get(itemId);
        if( i != null && i.item == null )
            throw new RuntimeException("cache: record with empty item");
        return i != null ? i.item : null;
    }

    public @Nullable ItemResult getResult(HashId itemId) {
        Record r = records.get(itemId);
        if( r != null && r.item == null )
            throw new RuntimeException("cache: record with empty item");
        return r != null ? r.result : null;
    }

    public void put(Approvable item, ItemResult result) {
        // this will plainly override current if any
        Record r = new Record(item, result);
    }

    public void update(HashId itemId, ItemResult result) {
        Record r = records.get(itemId);
        if( r != null && r.item == null )
            throw new RuntimeException("cache: record with empty item");

        if( r != null ) {
            r.result = result;
        }
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
        private Approvable item;
        private ItemResult result;

        private Record(Approvable item, ItemResult result) {
            expiresAt = Instant.now().plus(maxAge);
            this.item = item;
            this.result = result;
            records.put(item.getId(), this);
        }

        private void checkExpiration(Instant now) {
            if( expiresAt.isBefore(now) ) {
//                System.out.println("cache expired "+item.getId());
                records.remove(item.getId());
            }
        }
    }
}
