/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import net.sergeych.utils.Base64;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ItemCache {

    private final Thread cleaner;
    private final Duration maxAge;

    public ItemCache(Duration maxAge) {
        this.maxAge = maxAge;
        cleaner = new Thread( ()-> {
            try {
                Thread.sleep(5000);
                cleanUp();
            } catch (InterruptedException e) {
            }
        });
        cleaner.setName("item-cache-cleaner");
        cleaner.setDaemon(true);
        cleaner.setPriority(Thread.MIN_PRIORITY);
        cleaner.start();
    }

    final void cleanUp() {
        // we should avoid creating an object for each check:
        Instant now = Instant.now();
        records.values().forEach(r->r.checkExpiration(now));
    }

    public @Nullable Approvable get(HashId itemId) {
        Record i = records.get(itemId);
        if( i != null && i.item == null )
            throw new RuntimeException("cache: record with empty item");
        return i != null ? i.item : null;
    }

    public void put(Approvable item) {
        // this will plainly override current if any
        new Record(item);
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

        private Record(Approvable item) {
            expiresAt = Instant.now().plus(maxAge);
            this.item = item;
            records.put(item.getId(), this);
        }

        private void checkExpiration(Instant now) {
            if( expiresAt.isBefore(now) ) {
                System.out.println("cache expired "+item.getId());
                records.remove(item.getId());
            }
        }
    }
}
