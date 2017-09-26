/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.roles;

import net.sergeych.biserializer.BiType;

/**
 * Role combining other roles in the "and", "or" and "any N of" principle
 */
@BiType(name="ListRole")
public abstract class ListRole extends Role {

    // TODO: implement combination of roles
}
