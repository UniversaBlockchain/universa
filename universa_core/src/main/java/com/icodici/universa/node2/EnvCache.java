/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2;

import com.icodici.universa.HashId;
import com.icodici.universa.contract.services.NImmutableEnvironment;
import com.icodici.universa.node.ItemResult;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

public class EnvCache {

    private final Timer cleanerTimer = new Timer();
    private final Duration maxAge;

    public EnvCache(Duration maxAge) {
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
        environmetsExpiration.keySet().forEach(envId -> {
            if(environmetsExpiration.get(envId).isBefore(now)) {
                environmetsExpiration.remove(envId);
                environemtsByContract.remove(environemtsById.remove(envId).getContract().getId());
            }
        });
    }

    public void shutdown() {
        cleanerTimer.cancel();
        cleanerTimer.purge();
    }

    public @Nullable NImmutableEnvironment get(HashId itemId) {
        return environemtsByContract.get(itemId);
    }



    public void put(NImmutableEnvironment env) {
        //TODO: fixed env cache related errors. swapping ids etc
        //environemtsByContract.put(env.getContract().getId(),env);
        //environemtsById.put(env.getId(),env);
        //environmetsExpiration.put(env.getId(),Instant.now().plus(maxAge));
    }

    private ConcurrentHashMap<HashId,NImmutableEnvironment> environemtsByContract = new ConcurrentHashMap();
    private ConcurrentHashMap<Long,NImmutableEnvironment> environemtsById = new ConcurrentHashMap();
    private ConcurrentHashMap<Long,Instant> environmetsExpiration = new ConcurrentHashMap();


    public int size() {
        return environemtsByContract.size();
    }

    public NImmutableEnvironment get(Long environmentId) {
        return environemtsById.get(environmentId);
    }

    public void remove(HashId id) {;
        NImmutableEnvironment env = environemtsByContract.remove(id);
        if(env != null) {
            long envId = env.getId();
            environemtsById.remove(envId);
            environmetsExpiration.remove(envId);
        }
    }
}
