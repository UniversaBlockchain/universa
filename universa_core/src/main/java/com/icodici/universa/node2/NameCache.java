package com.icodici.universa.node2;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    final private static String NAME_PREFIX = "n_";
    final private static String ORIGIN_PREFIX = "o_";
    final private static String ADDRESS_PREFIX = "a_";

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

    private boolean lockStringValue(String name_reduced) {
        Record prev = records.putIfAbsent(name_reduced, new Record(name_reduced));
        return (prev == null);
    }

    private void unlockStringValue(String name_reduced) {
        records.remove(name_reduced);
    }

    private boolean lockStringList(List<String> reducedNameList) {
        boolean isAllNamesLocked = true;
        List<String> lockedByThisCall = new ArrayList<>();
        for (String reducedName : reducedNameList) {
            if (lockStringValue(reducedName)) {
                lockedByThisCall.add(reducedName);
            } else {
                isAllNamesLocked = false;
                break;
            }
        }
        if (!isAllNamesLocked) {
            for (String rn : lockedByThisCall)
                unlockStringValue(rn);
        }
        return isAllNamesLocked;
    }

    private void unlockStringList(List<String> reducedNameList) {
        for (String reducedName : reducedNameList)
            unlockStringValue(reducedName);
    }

    private List<String> getSringListWithPrefix(String prefix, List<String> srcList) {
        List<String> list = new ArrayList<>(srcList);
        for (int i = 0; i < list.size(); ++i)
            list.set(i, prefix + list.get(i));
        return list;
    }

    public boolean lockNameList(List<String> reducedNameList) {
        return lockStringList(getSringListWithPrefix(NAME_PREFIX, reducedNameList));
    }

    public void unlockNameList(List<String> reducedNameList) {
        unlockStringList(getSringListWithPrefix(NAME_PREFIX, reducedNameList));
    }

    public boolean lockOriginList(List<String> originList) {
        return lockStringList(getSringListWithPrefix(ORIGIN_PREFIX, originList));
    }

    public void unlockOriginList(List<String> originList) {
        unlockStringList(getSringListWithPrefix(ORIGIN_PREFIX, originList));
    }

    public boolean lockAddressList(List<String> addressList) {
        return lockStringList(getSringListWithPrefix(ADDRESS_PREFIX, addressList));
    }

    public void unlockAddressList(List<String> addressList) {
        unlockStringList(getSringListWithPrefix(ADDRESS_PREFIX, addressList));
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
