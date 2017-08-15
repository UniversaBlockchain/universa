/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa;

public interface Serializer {
    byte[] serialize(Approvable item);
    Approvable deserialize(byte[] packedItem);
}
