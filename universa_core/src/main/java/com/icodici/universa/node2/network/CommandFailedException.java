/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.node2.network;

import com.icodici.universa.ErrorRecord;
import com.icodici.universa.Errors;

/**
 * Exception thrown if the remote command (authenticated) reports some {@link ErrorRecord}-based error.
 */
public class CommandFailedException extends ClientError {
    public CommandFailedException(ErrorRecord error) {
        super(error);
    }

    public CommandFailedException(Errors error, String objectName, String text) {
        this(new ErrorRecord(error, objectName, text));
    }
}
