/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.exception;

import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;

import java.io.IOException;

public class ClientError extends IOException {
    public ErrorRecord getErrorRecord() {
        return errorRecord;
    }

    private final ErrorRecord errorRecord;

    public ClientError(ErrorRecord er) {
        super(er.toString());
        this.errorRecord = er;
    }

    public ClientError(Errors code, String object, String message) {
        this(new ErrorRecord(code, object, message));
    }

    @Override
    public String toString() {
        return "ClientError: " + errorRecord;
    }
}
