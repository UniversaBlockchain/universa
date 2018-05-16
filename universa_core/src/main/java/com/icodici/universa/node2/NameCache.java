package com.icodici.universa.node2;

import java.time.Duration;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class-helper for concurrency work with UNS1 ledger functions.
 *
 * Main workflow for case of new name_reduced registration:
 *
 *   if !ledger.isNameRegistered(name_reduced):
 *     if nameCache.lockName(name_reduced):
 *       someProcessingOfUnsContract()
 *       if weCanRegisterThisName:
 *         ledger.registerName(name_reduced)
 *       nameCache.unlockName(name_reduced)
 *
 */
public class NameCache {

    private final Timer cleanerTimer = new Timer();
    private final Duration maxAge;

    public NameCache(Duration maxAge) {
        this.maxAge = maxAge;
        cleanerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                cleanUp();
            }
        }, 5000, 5000);
    }

    final void cleanUp() {
        Instant now = Instant.now();
        records.values().forEach(r->r.checkExpiration(now));
    }

    public void shutdown() {
        cleanerTimer.cancel();
        cleanerTimer.purge();
    }

    public boolean lockName(String name_reduced) {
        Record prev = records.putIfAbsent(name_reduced, new Record(name_reduced));
        return (prev == null);
    }

    public void unlockName(String name_reduced) {
        records.remove(name_reduced);
    }

    private ConcurrentHashMap<String,Record> records = new ConcurrentHashMap();

    public int size() {
        return records.size();
    }

    private class Record {
        private Instant expiresAt;
        private String name_reduced;

        private Record(String name_reduced) {
            expiresAt = Instant.now().plus(maxAge);
            this.name_reduced = name_reduced;
        }

        private void checkExpiration(Instant now) {
            if( expiresAt.isBefore(now) ) {
                records.remove(name_reduced);
            }
        }
    }

}
