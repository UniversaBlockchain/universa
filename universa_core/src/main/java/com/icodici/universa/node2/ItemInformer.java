/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.HashId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The way to attach some information to the item to the clietn software, mainly, error traces.
 */
public class ItemInformer {

    public void inform(Approvable item) {
        getRecord(item.getId()).errorRecords.addAll(item.getErrors());
    }

    public class Record {
        private final HashId hashId;
        private Instant expiresAt;
        public final List<ErrorRecord> errorRecords = new ArrayList<>();
        public final List<String> messages = new ArrayList<>();

        private void checkExpiration(Instant now) {
            if (expiresAt.isBefore(now))
                records.remove(this);
        }

        private Record(HashId id) {
            hashId = id;
            resetExpiration();
        }

        private final void resetExpiration() {
            expiresAt = Instant.now().plusSeconds(300);
        }

        private synchronized void addError(ErrorRecord er) {
            resetExpiration();
            errorRecords.add(er);
        }

        private synchronized void addMessage(ErrorRecord er) {
            resetExpiration();
            errorRecords.add(er);
        }
    }

    private ConcurrentHashMap<HashId, Record> records = new ConcurrentHashMap();

    final void cleanUp() {
        // we should avoid creating an object for each check:
        Instant now = Instant.now();
        records.values().forEach(r -> r.checkExpiration(now));
    }

    public void inform(HashId itemId, ErrorRecord error) {
        getRecord(itemId).addError(error);
    }

    public synchronized Record getRecord(HashId itemId) {
        Record r = records.get(itemId);
        if (r == null) {
            r = new Record(itemId);
            records.put(itemId, r);
        }
        return r;
    }

    public Record takeFor(HashId id) {
        return records.remove(id);
    }

}
