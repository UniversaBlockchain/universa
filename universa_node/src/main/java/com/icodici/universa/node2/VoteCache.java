/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import com.icodici.universa.node.ItemResult;
import net.sergeych.utils.Base64;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoteCache {

    private final Timer cleanerTimer = new Timer();
    private final Duration maxAge;

    public VoteCache(Duration maxAge) {
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




    public int size() {
        return records.size();
    }

    private Map<HashId,Record> records = new ConcurrentHashMap();

    public ZonedDateTime addVote(HashId itemId, PublicKey publicKey) {
        records.putIfAbsent(itemId, new Record(itemId));
        Record r = records.get(itemId);
        r.votes.add(publicKey);
        return ZonedDateTime.ofInstant(r.expiresAt, ZoneId.systemDefault());
    }

    public ZonedDateTime getVoteExpires(HashId itemId) {
        Record r = records.get(itemId);
        return r != null ?  ZonedDateTime.ofInstant(r.expiresAt, ZoneId.systemDefault()) : null;
    }

    public Set<PublicKey> getVoteKeys(HashId itemId) {
        Record r = records.get(itemId);
        return r != null ?  r.votes : null;
    }

    private class Record {
        private Instant expiresAt;
        private HashId itemId;
        private Set<PublicKey> votes;

        private Record(HashId itemId) {
            expiresAt = Instant.now().plus(maxAge);
            this.itemId = itemId;
            this.votes = ConcurrentHashMap.newKeySet();
        }

        private void checkExpiration(Instant now) {
            if( expiresAt.isBefore(now) ) {
//                System.out.println("cache expired "+item.getId());
                records.remove(itemId);
            }
        }
    }
}
