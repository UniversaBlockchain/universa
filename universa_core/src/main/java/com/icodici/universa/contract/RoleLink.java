/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.AbstractKey;
import com.icodici.crypto.PublicKey;
import net.sergeych.boss.Boss;
import net.sergeych.tools.Binder;

import java.util.Collection;
import java.util.Set;

/**
 * A symlink-like role delegate. It uses a named role in the context of a bound {@link Contract} (with {@link
 * Role#setContract(Contract)} call), then it delegates all actual work to the target role from the contract roles.
 * <p>
 * This is used to assign roles to roles, and to create special roles for permissions, etc.
 */
public class RoleLink extends Role {

    private final String roleName;

    /**
     * Create a link to a named role. Note that such links can be created ahead of time, e.g. when there is no bound
     * contract or the target role does not yet exist. Just be sure to bind the contract with {@link
     * #setContract(Contract)} before using the instance.
     *
     * @param name     new role name
     * @param roleName existing role name
     */
    public RoleLink(String name, String roleName) {
        super(name);
        if (name.equals(roleName))
            throw new IllegalArgumentException("RoleLink: name and target name are equals: " + name);
        this.roleName = roleName;
    }

    /**
     * Return the resolved role taken from a bound contract.
     * @return
     */
    public Role getRole() {
        return getContract().getRole(roleName);
    }

    @Override
    public void addKeyRecord(KeyRecord keyRecord) {
        badOperation();
        getRole().addKeyRecord(keyRecord);
    }

    private void badOperation() {
        throw new RuntimeException("operation not supported for RoleLink instance");
    }

    @Override
    public KeyRecord getKeyRecord() {
        return getRole().getKeyRecord();
    }

    @Override
    public Collection<KeyRecord> getKeyRecords() {
        return getRole().getKeyRecords();
    }

    @Override
    public Set<PublicKey> getKeys() {
        return getRole().getKeys();
    }

    @Override
    public boolean isAllowedForKeys(Set<? extends AbstractKey> keys) {
        return getRole().isAllowedForKeys(keys);
    }

    @Override
    public boolean isValid() {
        return getRole().isValid();
    }

    @Override
    public boolean equalKeys(Object obj) {
        return getRole().equalKeys(obj);
    }

    @Override
    public Role cloneAs(String name) {
        return new RoleLink(name, roleName);
    }

    @Override
    public boolean isAllowedForKeys(Role anotherRole) {
        return getRole().isAllowedForKeys(anotherRole);
    }

    @Override
    public String toString() {
        if (getContract() != null)
            return "RoleLink<" + getName() + "->" + roleName + ":" + getRole().toString() + ">";
        else
            return "RoleLink<" + getName() + "->" + roleName + ":" + "not connected>";
    }

    /**
     * RoleLint equality is different: it only checks that it points to the same role.
     *
     * @param obj
     *
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RoleLink) {
            return ((RoleLink) obj).roleName.equals(roleName);
        }
        return false;
    }

    static {
        Boss.registerAdapter(RoleLink.class, new Boss.Adapter() {
            @Override
            public Binder serialize(Object object) {
                RoleLink r = (RoleLink) object;
                return Binder.fromKeysValues(
                        "name", r.getName(),
                        "target_name", r.roleName
                );
            }

            @Override
            public Object deserialize(Binder binder) {
                return new RoleLink(binder.getStringOrThrow("name"),
                                    binder.getStringOrThrow("target_name")
                );
            }
        });
    }
}
