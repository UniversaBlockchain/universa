package net.sergeych.collections;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Multimap (AKA bag) associates any number of elements with a key. It could be constructed using any Map and
 * any Collection using the constructor.
 *
 * Note that this implementation is not synchronized and not yet very well tested.
 *
 * @param <K>
 * @param <V>
 */
public class Multimap<K, V> {

    private final Class<? extends Collection<V>> collectionClass;
    protected Map<K, Collection<V>> map;
    int size = 0;


    static public <T,U> Multimap newInstance() {
        return new Multimap(HashMap.class,(Class<ArrayList>) ArrayList.class);
    }

    public Multimap() {
        collectionClass = (Class<? extends Collection<V>>) (Class) ArrayList.class;
        map = new HashMap<>();

    }

    public Multimap(Class<? extends Map> mapClass,
                    Class<? extends Collection<V>> collectionClass) {
        try {
            this.collectionClass = collectionClass;
            map = mapClass.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Can't instantiate Multimap's map", e);
        }
    }

    public int size() {
        return size;
    }

    /**
     * Return possibly empty collection of elements associated with a given key.
     *
     * @param key
     * @return
     */
    @Nullable
    public Collection<V> get(K key) {
        Collection<V> collection = map.get(key);
        return collection == null ? null : Collections.unmodifiableCollection(collection);
    }

    @Nullable
    public V getFirst(K key) {
        Collection<V> collection = map.get(key);
        return collection == null ? null : collection.iterator().next();
    }

    /**
     * Return all associated element as a list, possibly empty.
     *
     * @param key
     * @return
     */
    @NonNull
    public List<V> getList(K key) {
        Collection<V> collection = map.get(key);
        List<V> list = new ArrayList<>();
        if( collection != null && !collection.isEmpty() )
            list.addAll(collection);
        return list;
    }

    public void forEach(BiConsumer<? super K, ? super Collection<V>> action) {
        map.forEach(action);
    }

    public void put(@NonNull K key, @NonNull V value) {
        Collection<V> collection = map.get(key);
        if (collection == null) {
            try {
                collection = collectionClass.newInstance();
                map.put(key,collection);
            } catch (Exception e) {
                throw new IllegalArgumentException("failed to instantiate collection", e);
            }
        }
        boolean added = collection.add(value);
        if( added )
            size++;
    }

    /**
     * Remove all elements, if any existed, associated with a key.
     * @param key
     * @return collection of just removed elements or null if no elements were actually removed
     */
    public <C extends Collection<V>> C remove(@NonNull K key) {
        Collection<V> collection = map.remove(key);
        if( collection != null ) {
            size -= collection.size();
            return (C)collection;
        }
        return null;
    }

    /**
     * Remove a value it it was associated with a specified key
     * @param key
     * @param value
     * @return true if the value was actually removed
     */
    public boolean removeValue(@NonNull K key, @NonNull V value) {
        Collection<V> collection = map.get(key);
        if( collection == null )
            return false;
        boolean removed = collection.remove(value);
        if( removed )
            size--;
        if( collection.isEmpty() )
            map.remove(key);
        return removed;
    }

    public boolean containsKey(@NonNull K key) {
        Collection<V> collection = map.get(key);
        if( collection == null )
            return false;
        return collection.contains(key);
    }

    public @NonNull Set<K> keySet() {
        return map.keySet();
    }

    /**
     * Return all elements associated with all keys. Note, that if the same element is associated more than once,
     * for example, in different keys, it will appear in the resulting list more than once. Construct a Set of it
     * if needed.
     *
     * @return list of all elements
     */
    public @NonNull List<V> values() {
        ArrayList<V> all = new ArrayList<>(size);
        for(Collection<V> collection: map.values()) {
            all.addAll(collection);
        }
        return all;
    }
}
