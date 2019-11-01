/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;
import net.sergeych.tools.Binder;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Permission allows to change and remove owner role of contract.
 */

@BiType(name="ChangeRolePermission")
public class ChangeRolePermission extends Permission {

    private String roleName;

    /**
     * Create new permission allowing change of custom role.
     *
     * @apiNote This permission does not apply to predefined contract roles: issuer, owner, creator.
     * Issuer is immutable. Change of an owner can be allowed by {@link ChangeOwnerPermission}. Creator
     * can be changed without any permission.
     *
     *
     * @param role allows to permission
     * @param roleName name of a role that is allowed to be changed.
     *
     */
    public ChangeRolePermission(Role role, String roleName) {
        super("change_role", role);
        this.roleName = roleName;
    }

    private ChangeRolePermission() {}

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder results = super.serialize(serializer);
        results.put("role_name", roleName);
        return results;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        this.roleName = data.getStringOrThrow("role_name");
    }

    /**
     * Check and remove change of state.owner, if any.
     *  @param contract valid contract state
     * @param changed is contract for checking
     * @param stateChanges changes in its state section
     * @param revokingItems items to be revoked. The ones are getting joined will be removed during check
     * @param keys keys contract is sealed with. Keys are used to check other contracts permissions
     */
    @Override
    public void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges, Set<Contract> revokingItems, Collection<PublicKey> keys) {

        Delta x = stateChanges.get("roles");
        if(x instanceof MapDelta) {
            ((MapDelta)x).remove(roleName);
        }
    }

    static {
        DefaultBiMapper.registerClass(ChangeRolePermission.class);
    }
}
