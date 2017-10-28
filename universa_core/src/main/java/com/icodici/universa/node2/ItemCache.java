/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
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
        return i != null ? i.item : null;
    }

    public void put(Approvable item) {
        // this will plainly override current if any
        new Record(item);
        System.out.println("cache: stored item "+item.getId());
    }

    private ConcurrentHashMap<HashId,Record> records = new ConcurrentHashMap();

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
