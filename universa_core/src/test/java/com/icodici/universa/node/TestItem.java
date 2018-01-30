/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.node;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;
import com.icodici.universa.HashId;
import com.icodici.universa.contract.Reference;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiAdapter;
import net.sergeych.tools.Binder;
import net.sergeych.tools.Do;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

public class TestItem implements Approvable {

    private boolean isGood = true;
    private HashId hashId = HashId.createRandom();
    private Set<Approvable> newItems = new HashSet<>();
    private Set<Reference> referencedItems = new HashSet<>();
    private Set<Approvable> revokingItems = new HashSet<>();
    private List<ErrorRecord> errors = new ArrayList<>();

    private boolean expiresAtPlusFive = true;

    @Override
    public Set<Reference> getReferencedItems() {
        return referencedItems;
    }

    public TestItem(boolean isOk) {
        isGood = isOk;
        if( !isOk)
            addError(new ErrorRecord(Errors.BAD_VALUE,"me", "I'm bad"));
    }

    @Override
    public Set<Approvable> getNewItems() {
        return newItems;
    }

    @Override
    public boolean check(String __) {
        // common check for all cases
        errors.clear();
        if( !isGood)
            addError(new ErrorRecord(Errors.BAD_VALUE,"me", "I'm bad"));
        return isGood;
    }
    public boolean paymentCheck(PublicKey issuerKey) throws Quantiser.QuantiserException {
        return check("");
    }

    @Override
    public HashId getId() {
        return hashId;
    }

    public void addNewItems(TestItem... items) {
        newItems.addAll(Do.listOf(items));
    }

    public void addReferencedItems(HashId... itemIds) {
        Stream.of(itemIds).forEach(i -> {
            Reference refModel = new Reference();
            refModel.contract_id = i;
            referencedItems.add(refModel);
        });
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

    @Override
    public ZonedDateTime getExpiresAt() {
        return expiresAtPlusFive ? ZonedDateTime.now().plusHours(5) : ZonedDateTime.now();
    }

    public void setExpiresAtPlusFive(boolean expiresAtPlusFive) {
        this.expiresAtPlusFive = expiresAtPlusFive;
    }

    @Override
    public boolean isTU() {
        return true;
    }
}
