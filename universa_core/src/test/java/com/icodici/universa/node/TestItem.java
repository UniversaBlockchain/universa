/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.universa.Approvable;
import com.icodici.universa.HashId;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class TestItem implements Approvable {

    private boolean isGood = true;
    private HashId hashId = HashId.createRandom();
    private Set<Approvable> newItems = new HashSet<>();
    private Set<HashId> referencedItems = new HashSet<>();
    private Set<Approvable> revokingItems = new HashSet<>();

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
        Boss.registerAdapter(TestItem.class, new Boss.Adapter<TestItem>() {
            @Override
            public Binder serialize(TestItem item) {
                return Binder.fromKeysValues("ok", item.isGood);
            }

            @Override
            public TestItem deserialize(Binder binder) {
                return new TestItem(binder.getBooleanOrThrow("ok"));
            }
        });
    }
}
