/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>, August 2017.
 *
 */

package com.icodici.universa.contract;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.Errors;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiMapper;
import net.sergeych.biserializer.BossBiMapper;
import net.sergeych.diff.ChangedItem;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;

import java.time.ZonedDateTime;
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
    private Role creator;

    public ContractDelta(Contract existing, Contract changed) {
        this.existing = existing;
        this.changed = changed;
    }

    public void check() {
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
            if (rootDelta.getChanges().size() > 1)
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
    private void checkStateChange() {
        stateChanges = stateDelta.getChanges();
        stateChanges.remove("created_by");

        // todo: check siblings have different and proper branch ids
        stateChanges.remove("branch_id");

        // todo: these changes should be already checked
        stateChanges.remove("parent");
        stateChanges.remove("origin");

        if ( insignificantKeys.containsAll(stateChanges.keySet()) ) {
            addError(BADSTATE, "", "new state is identical");
        }

        creator = changed.getRole("creator");
        if (creator == null) {
            addError(MISSING_CREATOR, "state.created_by", "");
            return;
        }
        ChangedItem<Integer, Integer> revision = (ChangedItem) stateChanges.get("revision");
        if (revision == null)
            addError(BAD_VALUE, "state,revision", "is not incremented");
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

    private void excludePermittedChanges() {
        Set<PublicKey> creatorKeys = creator.getKeys();
        existing.getPermissions().values().forEach(permission -> {
            if (permission.isAllowedForKeys(creatorKeys))
                permission.checkChanges(existing, changed, stateChanges);
        });
    }

    private void checkOwnerChanged() {
        ChangedItem<Role, Role> oc = (ChangedItem<Role, Role>) stateChanges.get("owner");
        if (oc != null) {
            stateChanges.remove("owner");
            if (!existing.isPermitted("change_owner", creator))
                addError(FORBIDDEN, "state.owner", "creator has no right to change");
        }
    }

    private void addError(Errors code, String field, String text) {
        changed.addError(code, field, text);
    }
}
