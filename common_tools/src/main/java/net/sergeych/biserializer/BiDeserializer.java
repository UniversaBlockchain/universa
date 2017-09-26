/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

import net.sergeych.tools.Binder;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

public class BiDeserializer {

    protected BiMapper mapper;
    private Stack stack;
    private Object context = null;


    public BiDeserializer(BiMapper mapper) {
        this.mapper = mapper;
    }

    public BiDeserializer() {
        mapper = DefaultBiMapper.getDefaultMapper();
    }

    public <T,U> T deserialize(U data) {
        return (T) mapper.deserializeObject(data, this);
    }

    public <T> List<T> deserializeCollection(Collection<?> collection) {
        return collection.stream()
                .map(x-> (T) mapper.deserializeObject(x,this))
                .collect(Collectors.toList());
    }

    public Binder deserializeAsBinder(Binder data) {
        return deserialize(data);
    }

    public void deserializeInPlace(Map map) {
        mapper.deserializeInPlace(map, this);
    }

    public final <T> T getContext() {
        return (T) context;
    }

    public final <T> @NonNull T getContextOrThrow() {
        if( context == null )
            throw new IllegalArgumentException("context not set");
        return (T) context;
    }

    public <T,E extends Throwable> void withContext(@NonNull T contextValue, ContextBlock<E> block) throws E {
        try {
            if( context != null ) {
                if( stack == null )
                    stack = new Stack();
                stack.push(context);
            }
            context = contextValue;
            block.perform();
        }
        finally {
            if( stack != null && !stack.isEmpty() )
                context = stack.pop();
            else
                context = null;
        }
    }

    public interface ContextBlock<E extends Throwable> {
        void perform() throws E;
    }

}
