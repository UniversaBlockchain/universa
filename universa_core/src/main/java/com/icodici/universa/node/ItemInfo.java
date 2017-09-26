package com.icodici.universa.node;

import com.icodici.universa.Approvable;
import com.icodici.universa.ErrorRecord;
import com.icodici.universa.HashId;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.biserializer.BiAdapter;
import net.sergeych.tools.Binder;

import java.util.Collection;

public class ItemInfo {
    private final ItemResult itemResult;
    private final HashId itemId;
    private final Collection<ErrorRecord> errors;

    public ItemResult getItemResult() {
        return itemResult;
    }

    public HashId getItemId() {
        return itemId;
    }

    public Collection<ErrorRecord> getErrors() {
        return errors;
    }

    public ItemInfo(ItemResult itemResult, Approvable item) {
        this.itemResult = itemResult;
        itemId = item.getId();
        errors = item.getErrors();
    }

    public ItemInfo(ItemResult itemResult, HashId itemId, Collection<ErrorRecord> errors) {
        this.itemResult = itemResult;
        this.itemId = itemId;
        this.errors = errors;
    }

    static {
        DefaultBiMapper.registerAdapter(ItemInfo.class, new BiAdapter() {
            @Override
            public Binder serialize(Object object, BiSerializer serializer) {
                ItemInfo ii = (ItemInfo)object;
                return Binder.fromKeysValues(
                    "item_result", ii.itemResult,
                    "item_id", ii.itemId,
                    "errors", ii.errors
                );
            }

            @Override
            public Object deserialize(Binder binder, BiDeserializer deserializer) {
                return new ItemInfo(
                        binder.getOrThrow("item_result"),
                        binder.getOrThrow("item_id"),
                        binder.getList("errors", null)
                );
            }
        });
    }
}
