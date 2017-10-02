/*
 * Created by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/01/17.
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;
import net.sergeych.tools.Binder;

import java.util.*;


@BiType(name = "ModifyDataPermission")
public class ModifyDataPermission extends Permission {

    private Set<String> fields = new HashSet<>();

    public ModifyDataPermission() {}

    public ModifyDataPermission(Role role, Binder params) {
        super("modify_data", role);
        Object fields = params.get("fields");
        if (fields != null && fields instanceof List) {
            this.fields.addAll((List) fields);
        }
    }

    public ModifyDataPermission addField(String fieldName) {
        this.fields.add(fieldName);
        return this;
    }

    public void addAllFields(Set<String> fields) {
        this.fields.addAll(fields);
    }

    @Override
    public void checkChanges(Contract contract, Map<String, Delta> stateChanges) {
        Delta data = stateChanges.get("data");
        if (data != null && data instanceof MapDelta) {
            Map mapChanges = ((MapDelta) data).getChanges();
            mapChanges.keySet().removeIf(key -> this.fields.contains(key));
        }
    }

    static {
        DefaultBiMapper.registerClass(ModifyDataPermission.class);
    }
}