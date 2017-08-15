/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

import net.sergeych.tools.Bindable;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by sergeych on 20.12.16.
 */
public class KeyRing implements Capsule.KeySource, Bindable {

    @Override
    public Binder toBinder() {
        ArrayList<Binder> result = new ArrayList<>();
        for(AbstractKey k: keys)
            result.add(k.toBinder());
        return new Binder("keys", result);
    }

    static public KeyRing fromBinder(Binder source) throws IOException, EncryptionError {
        return new KeyRing().updateFrom(source);
    }

    @Override
    public KeyRing updateFrom(Binder source) throws IOException, EncryptionError {
        for(Binder kb: source.getBinders("keys")) {
            KeyInfo ki = new KeyInfo(kb.getBinary("keyInfo"));
            AbstractKey k = ki.unpackKey(kb.getBinary("data"));
            keys.add(k);
        }
        return this;
    }

    public void clear() {
        keys.clear();
    }

    private final HashSet<AbstractKey> keys = new HashSet<>();

    public void addKey(AbstractKey key) {
        keys.add(key);
    }

    public boolean removeKey(AbstractKey key) {
        return keys.remove(key);
    }

    /**
     * The smart keys search. First keys with matching tags and type, then all others with matching
     * type.
     *
     * @param keyInfo desired key information
     * @return Collection of matching keys, possibly empty.
     */
    @Override
    @NonNull
    public Collection<AbstractKey> findKey(KeyInfo keyInfo) {
        final ArrayList<AbstractKey> result = new ArrayList<>();
        for( AbstractKey k: keys) {
            final KeyInfo ki = k.keyInfo;
            if( ki.matchType(keyInfo) ) {
                if( ki.matchTag(keyInfo) )
                    result.add(0, k);
                else
                    result.add(k);
            }
        }
        return result;
    }

    public void addKeys(AbstractKey... newKeys) {
        keys.addAll(Do.collection(newKeys));
    }

    @Override
    public boolean equals(Object obj) {
        if( obj instanceof KeyRing )
            return keys.equals(((KeyRing) obj).keys);
        return super.equals(obj);
    }

    @Override
    public String toString() {
        return "KeyRing(" + keys.toString() + ")";
    }

    public boolean contains(AbstractKey k) {
        return keys.contains(k);
    }

    Set<AbstractKey> keySet() {
        return keys;
    }
}
