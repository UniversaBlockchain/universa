/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.crypto;

/**
 * Created by sergeych on 04/01/16.
 */
public class Error extends Exception {
    public Error() { super(); }

    public Error(String reason) {
        super(reason);
    }
}
