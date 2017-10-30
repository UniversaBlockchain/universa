/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa;

public enum Errors {
    NOT_SUPPORTED,
    BAD_VALUE,
    EXPIRED,
    MISSING_OWNER,
    MISSING_ISSUER,
    MISSING_CREATOR,
    ISSUER_MUST_CREATE,
    NOT_SIGNED,
    /**
     * Issuer/creator has no right to perform requested change, revocation, etc.
     */
    FORBIDDEN,
    /**
     * Too many errors, the check could not be done at full.
     */
    FAILED_CHECK,
    /**
     * Approvable item of unknown type or general reference error
     */
    BAD_REF,
    BAD_SIGNATURE,
    /**
     * can't revoke requested item
     */
    BAD_REVOKE,
    BAD_NEW_ITEM,
    NEW_ITEM_EXISTS,
    ILLEGAL_CHANGE,
    /**
     * New state is bad in general (say, not changed)
     */
    BADSTATE
    // -------------------------- other errors which are not contract-specific
,    /**
     * General error of unknown type
     */
    FAILURE,
    BAD_CLIENT_KEY,
    UNKNOWN_COMMAND,
    NOT_READY,
    COMMAND_FAILED
}
