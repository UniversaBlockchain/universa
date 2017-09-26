/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package net.sergeych.biserializer;

/**
 * Generic exception during serialization/deserialization
 */
public class BiSerializationException extends RuntimeException {
    public BiSerializationException() {
    }

    public BiSerializationException(String message) {
        super(message);
    }

    public BiSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public BiSerializationException(Throwable cause) {
        super(cause);
    }

    public BiSerializationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
