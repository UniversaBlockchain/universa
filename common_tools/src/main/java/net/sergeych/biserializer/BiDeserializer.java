/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * Binder deserializer. Provides deserializing objects prepared by {@link BiSerializer}. Provides deserialization
 * context helpers, {@link #withContext(Object, ContextBlock)} and {@link #getContext()}.
 * <p>
 * It is important to understand that each instance has a shared copy of {@link BiMapper} to be used in the
 * deserialization. It is important because there are different mappers for fidderent occasions, for example {@link
 * DefaultBiMapper} and {@link BossBiMapper}.
 */

public class BiDeserializer {

    private BiMapper mapper;
    private Stack stack;
    private Object context = null;

    /**
     * Construct deserializer for a given class mapper
     *
     * @param mapper
     */
    public BiDeserializer(BiMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Construct deserializer with {@link DefaultBiMapper}
     */
    public BiDeserializer() {
        mapper = DefaultBiMapper.getInstance();
    }

    /**
     * Deserialize some object if need. E.g. if the object is a simple type supported by most notations, e.g. numbers,
     * strings, it is left unchanged, but if it is a collection or a Map it will be processed to deserialize its
     * content. See {@link BiMapper#deserializeObject(Object)} for details
     *
     * @param object to deserialize
     * @param <T>
     * @param <U>
     *
     * @return restored object
     */
    public <T, U> T deserialize(U object) {
        return (T) mapper.deserializeObject(object, this);
    }

    /**
     * Deserialize a collection. Utility method that deserialize every object and return the result as an ArrayList.
     *
     * @param collection
     * @param <T>
     *
     * @return
     */
    public <T> List<T> deserializeCollection(Collection<?> collection) {
        return collection.stream()
                .map(x -> (T) mapper.deserializeObject(x, this))
                .collect(Collectors.toList());
    }

    /**
     * Modify a map by deserializing it's values in place. Saves memory.
     *
     * @param map
     */
    public void deserializeInPlace(Map map) {
        mapper.deserializeInPlace(map, this);
    }

    /**
     * Get the current context, if any. To set a context, use {@link #withContext(Object, ContextBlock)}
     *
     * @param <T>
     *
     * @return current context of null if not set.
     */
    public final <T> T getContext() {
        return (T) context;
    }

    /**
     * Get context or throw exception if none is set. See {@link #withContext(Object, ContextBlock)} for details.
     *
     * @param <T>
     *
     * @return context
     *
     * @throws IllegalArgumentException
     */
    public final <T> @NonNull T getContextOrThrow() {
        if (context == null)
            throw new IllegalArgumentException("context not set");
        return (T) context;
    }

    /**
     * Set the deserilaziation context and executes the {@link ContextBlock#perform()}. Context is any object that will
     * be retured by the {@link #getContext()} while executing the block. After this call the context will be resotred
     * to it's previous state, or set to null if no context was set.
     * <p>
     * It is safe and even recommended to set different context to simplify deserialization logic.
     * <p>
     * Any exception of specified class thrown by the block will be retransmitted outside.
     *
     * @param contextValue any object to be a current deserialization context
     * @param block        to perform with that context
     * @param <T>          context type
     * @param <E>          exception type that could be thrown from the block
     *
     * @throws E if the {@link ContextBlock#perform()} thows it.
     */
    public <T, E extends Throwable> void withContext(@NonNull T contextValue, ContextBlock<E> block) throws E {
        try {
            if (context != null) {
                if (stack == null)
                    stack = new Stack();
                stack.push(context);
            }
            context = contextValue;
            block.perform();
        } finally {
            if (stack != null && !stack.isEmpty())
                context = stack.pop();
            else
                context = null;
        }
    }

    /**
     * A lambda to call with context, could throw an exception of set type.
     * @param <E>
     */
    @FunctionalInterface
    public interface ContextBlock<E extends Throwable> {
        void perform() throws E;
    }

}
