package com.icodici.universa.node2;

import com.icodici.universa.HashId;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Class-helper for concurrency work with UNS1 ledger functions.
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

    private boolean lockStringValue(String value, HashId lockedBy) {
        Record prev = records.putIfAbsent(value, new Record(value, lockedBy));
        return (prev == null);
    }

    private void unlockStringValue(String value) {
        records.remove(value);
    }

    private List<String> lockStringList(String prefix, Collection<String> stringList, HashId lockedBy) {
        List<String> unavailableStrings = new ArrayList<>();
        List<String> lockedByThisCall = new ArrayList<>();
        for (String str : stringList) {
            String strWithPrefix = prefix + str;
            if (lockStringValue(strWithPrefix, lockedBy)) {
                lockedByThisCall.add(strWithPrefix);
            } else {
                unavailableStrings.add(str);
            }
        }
        if (unavailableStrings.size() > 0) {
            for (String rn : lockedByThisCall)
                unlockStringValue(rn);
        }
        return unavailableStrings;
    }

    private void unlockStringList(String prefix, Collection<String> stringList) {
        for (String str : stringList)
            unlockStringValue(prefix + str);
    }

    public List<String> lockNameList(Collection<String> reducedNameList, HashId lockedBy) {
        return lockStringList(NAME_PREFIX, reducedNameList, lockedBy);
    }

    public void unlockNameList(Collection<String> reducedNameList) {
        unlockStringList(NAME_PREFIX, reducedNameList);
    }

    public List<String> lockOriginList(Collection<HashId> originList, HashId lockedBy) {
        List<String> stringList = new ArrayList<>();
        for (HashId origin : originList)
            stringList.add(origin.toBase64String());
        return lockStringList(ORIGIN_PREFIX, stringList, lockedBy);
    }

    public void unlockOriginList(Collection<HashId> originList) {
        List<String> stringList = new ArrayList<>();
        for (HashId origin : originList)
            stringList.add(origin.toBase64String());
        unlockStringList(ORIGIN_PREFIX, stringList);
    }

    public List<String> lockAddressList(Collection<String> addressList, HashId lockedBy) {
        return lockStringList(ADDRESS_PREFIX, addressList, lockedBy);
    }

    public void unlockAddressList(Collection<String> addressList) {
        unlockStringList(ADDRESS_PREFIX, addressList);
    }

    private ConcurrentHashMap<String,Record> records = new ConcurrentHashMap();

    public int size() {
        return records.size();
    }

    public void unlockByLockerId(HashId lockerId) {
        Set<String> valuesToUnlock = records.values().stream().filter(record -> record.getLockedBy().equals(lockerId)).map(r -> r.getValue()).collect(Collectors.toSet());
        if(!valuesToUnlock.isEmpty()) {
            unlockStringList("", valuesToUnlock);
        }
    }

    private class Record {
        private Instant expiresAt;
        private String value;
        private HashId lockedBy;

        private Record(String value, HashId lockedBy) {
            expiresAt = Instant.now().plus(maxAge);
            this.value = value;
            this.lockedBy = lockedBy;
        }

        private void checkExpiration(Instant now) {
            if( expiresAt.isBefore(now) ) {
                records.remove(value);
            }
        }

        public HashId getLockedBy() {
            return lockedBy;
        }

        public String getValue() {
            return value;
        }
    }

}
