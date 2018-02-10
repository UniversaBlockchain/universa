/*
 * Copyright (c) 2018, iCodici S.n.C, All Rights Reserved
 *
 * Written by Stepan Mamontov <micromillioner@yahoo.com>
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.Parcel;
import net.sergeych.utils.Base64;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class ParcelCache {

    private final Thread cleaner;
    private final Duration maxAge;

    public ParcelCache(Duration maxAge) {
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

    public @Nullable Parcel get(HashId itemId) {
        Record i = records.get(itemId);
        if( i != null && i.parcel == null )
            throw new RuntimeException("cache: record with empty item");
        return i != null ? i.parcel : null;
    }

    public void put(Parcel parcel) {
        // this will plainly override current if any
        new Record(parcel);
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
        private Parcel parcel;

        private Record(Parcel parcel) {
            expiresAt = Instant.now().plus(maxAge);
            this.parcel = parcel;
            records.put(parcel.getId(), this);
        }

        private void checkExpiration(Instant now) {
            if( expiresAt.isBefore(now) ) {
                System.out.println("cache expired "+parcel.getId());
                records.remove(parcel.getId());
            }
        }
    }
}
