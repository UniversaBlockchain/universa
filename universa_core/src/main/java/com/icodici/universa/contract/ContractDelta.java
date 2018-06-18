/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.permissions.Permission;
import com.icodici.universa.contract.roles.Role;
import com.icodici.universa.node2.Quantiser;
import net.sergeych.biserializer.BiMapper;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.diff.ChangedItem;
import net.sergeych.diff.CreatedItem;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.icodici.universa.Errors.*;
import static java.util.Arrays.asList;

public class ContractDelta {

    private final Contract existing;
    private final Contract changed;
    private MapDelta stateDelta;
    private Map<String, Delta> stateChanges;
    private Set<Contract> revokingItems;

    public ContractDelta(Contract existing, Contract changed) {
        this.existing = existing;
        this.changed = changed;
    }

    //todo: need to test this synchronized attribute, should not affects overall node's concurrency
    synchronized public void check() throws Quantiser.QuantiserException {
        try {
            BiMapper mapper = BossBiMapper.getInstance();
            MapDelta rootDelta = Delta.between(mapper.serialize(existing), mapper.serialize(changed));
            MapDelta definitionDelta = (MapDelta) rootDelta.getChange("definition");
            stateDelta = (MapDelta) rootDelta.getChange("state");
            if (definitionDelta != null) {
                addError(ILLEGAL_CHANGE, "definition", "definition must not be changed");
            }
            // check immutable root area
            // should be only one change here: state
            int allowedRootChanges = 1;
            Delta ch = rootDelta.getChange("api_level");
            if( ch != null) {
                allowedRootChanges++;
            }

            // or can be changed section "transactional"
            Delta transactionalDelta =  rootDelta.getChange("transactional");
            if(transactionalDelta != null) {
                allowedRootChanges++;
            }

            if (rootDelta.getChanges().size() > allowedRootChanges)
                addError(ILLEGAL_CHANGE, "root", "root level changes are forbidden except the state");

            // check only permitted changes in data
            checkStateChange();
        } catch (ClassCastException e) {
            e.printStackTrace();
            addError(FAILED_CHECK, "", "failed to compare, structure is broken or not supported");
        }
    }

    static private final  Set<String> insignificantKeys = new HashSet<>(asList("created_at", "created_by",
                                                                               "revision", "branch_id", "parent",
                                                                               "origin"));
    private void checkStateChange() throws Quantiser.QuantiserException {
        stateChanges = stateDelta.getChanges();
        revokingItems = new HashSet(changed.getRevokingItems());
        stateChanges.remove("created_by");

        // todo: check siblings have different and proper branch ids
        stateChanges.remove("branch_id");

        // todo: these changes should be already checked
        stateChanges.remove("parent");
        stateChanges.remove("origin");

        if ( insignificantKeys.containsAll(stateChanges.keySet()) ) {
            addError(BADSTATE, "", "new state is identical");
        }

        Role creator = changed.getRole("creator");
        if (creator == null) {
            addError(MISSING_CREATOR, "state.created_by", "");
            return;
        }
        ChangedItem<Integer, Integer> revision = (ChangedItem) stateChanges.get("revision");
        if (revision == null)
            addError(BAD_VALUE, "state.revision", "is not incremented");
        else {
            stateChanges.remove("revision");
            if (revision.oldValue() + 1 != revision.newValue())
                addError(BAD_VALUE, "state.revision", "wrong revision number");
        }
        Delta creationTimeChange = stateChanges.get("created_at");
        // if time is changed, it must be past:
        if (creationTimeChange != null ) {
            stateChanges.remove("created_at");
            ChangedItem<ZonedDateTime, ZonedDateTime> ci = (ChangedItem) creationTimeChange;
            if (!ci.newValue().isAfter(ci.oldValue()))
                addError(BAD_VALUE, "state.created_at", "new creation datetime is before old one");
        }

        excludePermittedChanges();

        // Some changes coud be empty trees, cleared by permissions, which can not remove root
        // entries, so we should check them all:
        stateChanges.forEach((field, delta) -> {
            if (!delta.isEmpty()) {
                String reason = "";
                if (delta instanceof MapDelta)
                    reason = " in " + ((MapDelta) delta).getChanges().keySet();
                addError(FORBIDDEN,
                         "state." + field,
                         "not permitted changes" + reason+": "+delta.oldValue()+" -> " + delta.newValue());
            }
        });

    }

    private void excludePermittedChanges() throws Quantiser.QuantiserException {
        Set<PublicKey> checkingKeys = changed.getSealedByKeys();
        Set<String> checkingReferences = changed.getReferences().keySet();
        for (String key : existing.getPermissions().keySet()) {
            Collection<Permission> permissions = existing.getPermissions().get(key);
            boolean permissionQuantized = false;
            for (Permission permission : permissions) {
                if (permission.isAllowedFor(checkingKeys, checkingReferences)) {
                    if(!permissionQuantized) {
                        changed.checkApplicablePermissionQuantized(permission);
                        permissionQuantized = true;
                    }
                    permission.checkChanges(existing, changed, stateChanges,revokingItems,checkingKeys,checkingReferences);
                }
            }
        }
    }

    private void checkOwnerChanged() throws Quantiser.QuantiserException {
        ChangedItem<Role, Role> oc = (ChangedItem<Role, Role>) stateChanges.get("owner");
        if (oc != null) {
            stateChanges.remove("owner");
            Role creator = changed.getRole("creator");
            if (!existing.isPermitted("change_owner", creator))
                addError(FORBIDDEN, "state.owner", "creator has no right to change");
        }
    }

    private void addError(Errors code, String field, String text) {
        changed.addError(code, field, text);
    }

    public Set<Contract> getRevokingItems() {
        return revokingItems;
    }
}
