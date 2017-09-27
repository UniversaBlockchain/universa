/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import net.sergeych.biserializer.BiType;

/**
 * Role combining other roles (sub-roles) in the "and", "or" and "any N of" principle.
 */
@BiType(name = "ListRole")
public abstract class ListRole extends Role {

    /**
     * Mode of combining roles
     */
    enum Mode {
        /**
         * Role could be performed only if set of keys could play all sub-roles
         */
        ALL,
        /**
         * Role could be performed if set of keys could play any role of the list
         */
        ANY,
        /**
         * Role could be played if set of keys could play any quorrum set of roles, e.g. at least any N subroles,
         * controlled by the {@link #setQuorum(int)} method
         */
        QUORUM
    }

    private int quorumSize = 0;

    /**
     * Set mode to {@link Mode#QUORUM} and quorum size to any n roles.
     *
     * @param n how many subroles set of key must be able to play to play this role
     */
    abstract void setQuorum(int n);

    /**
     * @return quorum size if in quorum mode, otherwise 0
     */
    abstract int getQuorum();

    /**
     * Set mode to either {@link Mode#ALL} or {@link Mode#ANY}, Quorum mode could be set only with {@link #setQuorum(int)}
     * call,
     *
     * @param newMode mode to set
     *
     * @throws IllegalArgumentException if mode other than ANY/ALL is specified
     */
    abstract void setMode(Mode newMode);
}
