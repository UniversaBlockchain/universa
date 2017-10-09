/*
 * Created by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/01/17.
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.diff.ChangedItem;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;
import net.sergeych.tools.Binder;

import java.util.*;


@BiType(name = "ModifyDataPermission")
public class ModifyDataPermission extends Permission {

    public static final String FIELD_NAME = "modify_data";

    private Map<String, List<String>> fields = new HashMap<>();

    public ModifyDataPermission() {
    }

    public ModifyDataPermission(Role role, Binder params) {
        super(FIELD_NAME, role);
        Object fields = params.get("fields");
        if (fields != null && fields instanceof Map) {
            this.fields.putAll((Map) fields);
        }
    }

    public ModifyDataPermission addField(String fieldName, List<String> values) {
        this.fields.put(fieldName, values);
        return this;
    }

    public void addAllFields(Map<String, List<String>> fields) {
        this.fields.putAll(fields);
    }

    /**
     * checkChanges processes the map of changes with the list of fields with predefined data options for a role.
     *
     * @param contract     source (valid) contract
     * @param changedContract
     * @param stateChanges map of changes, see {@link Delta} for details
     */
    @Override
    public void checkChanges(Contract contract, Contract changedContract, Map<String, Delta> stateChanges) {
        Delta data = stateChanges.get("data");
        if (data != null && data instanceof MapDelta) {
            Map mapChanges = ((MapDelta) data).getChanges();
            mapChanges.keySet().removeIf(key -> {
                Object changed = mapChanges.get(key);

                Object value = "";

                if (changed != null && changed instanceof ChangedItem) {
                    value = ((ChangedItem) mapChanges.get(key)).newValue();
                }

                List<String> foundField = this.fields.get(key);

                return foundField != null && (foundField.contains(value) || isEmptyOrNull(foundField, value));
            });
        }
    }

    private boolean isEmptyOrNull(List<String> data, Object value) {
        return (value == null || "".equals(value)) && (data.contains(null) || data.contains(""));
    }

    static {
        DefaultBiMapper.registerClass(ModifyDataPermission.class);
    }
}