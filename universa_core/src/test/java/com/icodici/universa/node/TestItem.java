/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.HashId;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiAdapter;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.util.*;
import java.util.stream.Stream;

public class TestItem implements Approvable {

    private boolean isGood = true;
    private HashId hashId = HashId.createRandom();
    private Set<Approvable> newItems = new HashSet<>();
    private Set<HashId> referencedItems = new HashSet<>();
    private Set<Approvable> revokingItems = new HashSet<>();
    private List<ErrorRecord> errors = new ArrayList<>();
    @Override
    public Set<HashId> getReferencedItems() {
        return referencedItems;
    }

    public TestItem(boolean isOk) {
        isGood = isOk;
    }

    @Override
    public Set<Approvable> getNewItems() {
        return newItems;
    }

    @Override
    public boolean check() {
        return isGood;
    }

    @Override
    public HashId getId() {
        return hashId;
    }

    public void addNewItems(TestItem... items) {
        newItems.addAll(Do.listOf(items));
    }

    public void addReferencedItems(HashId... itemIds) {
        Stream.of(itemIds).forEach(i -> referencedItems.add(i));
    }

    @Override
    public Set<Approvable> getRevokingItems() {
        return revokingItems;
    }

    public void addRevokingItems(Approvable... items) {
        Stream.of(items).forEach(i -> revokingItems.add(i));
    }

    static {
        DefaultBiMapper.registerAdapter(TestItem.class, new BiAdapter<TestItem>() {
            @Override
            public Binder serialize(TestItem item, BiSerializer serializer) {
                return Binder.fromKeysValues("ok", item.isGood);
            }

            @Override
            public TestItem deserialize(Binder binder, BiDeserializer deserializer) {
                return new TestItem(binder.getBooleanOrThrow("ok"));
            }
        });
    }

    @Override
    public void addError(ErrorRecord r) {
        errors.add(r);
    }

    @Override
    public Collection<ErrorRecord> getErrors() {
        return errors;
    }
}
