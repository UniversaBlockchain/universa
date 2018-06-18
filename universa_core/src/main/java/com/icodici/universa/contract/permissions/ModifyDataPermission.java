/*
 * Created by Maxim Pogorelov <pogorelovm23@gmail.com>, 10/01/17.
 */

package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PublicKey;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiSerializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.diff.*;
import net.sergeych.tools.Binder;

import java.util.*;

import static java.util.Arrays.asList;

/**
 * Permission allows to change some set of fields. Field values can be limited to a list of values.
 */

@BiType(name = "ModifyDataPermission")
public class ModifyDataPermission extends Permission {

    public static final String FIELD_NAME = "modify_data";

    private Map<String, List<String>> fields = new HashMap<>();

    private Set<String> rootFields = new HashSet<>(asList("references", "expires_at"));

    public ModifyDataPermission() {}

    /**
     * Create new permission for change some set of fields.
     *
     * @param role allows to permission
     * @param params is parameters of permission: fields is map of field names and lists of allowed values
     */
    public ModifyDataPermission(Role role, Binder params) {
        super(FIELD_NAME, role, params);
        Object fields = params.get("fields");
        if (fields != null && fields instanceof Map) {
            this.fields.putAll((Map) fields);
        }
    }

    /**
     * Adds field to the allowed for change.
     *
     * @param fieldName is name of field allowed for change
     * @param values is list of allowed values for adding field
     */
    public ModifyDataPermission addField(String fieldName, List<String> values) {
        this.fields.put(fieldName, values);
        return this;
    }

    /**
     * Adds some set of fields to the allowed for change.
     *
     * @param fields is map of field names and lists of allowed values
     */
    public void addAllFields(Map<String, List<String>> fields) {
        this.fields.putAll(fields);
    }

    /**
     * checkChanges processes the map of changes with the list of fields with predefined data options for a role.
     *  @param contract source (valid) contract
     * @param changedContract is contract for checking
     * @param stateChanges map of changes, see {@link Delta} for details
     * @param revokingItems items to be revoked. The ones are getting joined will be removed during check
     * @param keys keys contract is sealed with. Keys are used to check other contracts permissions
     * @param checkingReferences are used to check other contracts permissions
     */
    @Override
    public void checkChanges(Contract contract, Contract changedContract, Map<String, Delta> stateChanges, Set<Contract> revokingItems, Collection<PublicKey> keys, Collection<String> checkingReferences) {
        Delta data = stateChanges.get("data");
        if (data != null && data instanceof MapDelta) {
            Map mapChanges = ((MapDelta) data).getChanges();
            mapChanges.keySet().removeIf(key -> {
                Object changed = mapChanges.get(key);

                Object value = "";

                if (changed != null && changed instanceof ChangedItem) {
                    value = ((ChangedItem) mapChanges.get(key)).newValue();
                }

                boolean containsField = this.fields.containsKey(key);

                List<String> foundField = this.fields.get(key);


                return (containsField && foundField == null) ||
                        (foundField != null && foundField.contains(value) || isEmptyOrNull(foundField, value));
            });
        }

        // check root fields modifies

        for (String rootField : rootFields) {
            boolean containsField = this.fields.containsKey("/" + rootField);
            List<String> foundField = this.fields.get("/" + rootField);

            Delta rootFieldChanges = stateChanges.get(rootField);
            if (rootFieldChanges != null) {
                Map mapChanges;

                if(rootFieldChanges instanceof ListDelta) {
                    mapChanges = ((ListDelta) rootFieldChanges).getChanges();
                } else if(rootFieldChanges instanceof MapDelta) {
                    mapChanges = ((MapDelta) data).getChanges();
                } else if(rootFieldChanges instanceof ChangedItem) {
                    mapChanges = stateChanges;
                } else {
                    mapChanges = null;
                }
                if(mapChanges != null) {
                    mapChanges.keySet().removeIf(key -> {
                        Object changed = mapChanges.get(key);

                        Object value = "";

                        if (changed != null && changed instanceof CreatedItem) {
                            value = ((CreatedItem) mapChanges.get(key)).newValue();
                        }
                        if (changed != null && changed instanceof ChangedItem) {
                            value = ((ChangedItem) mapChanges.get(key)).newValue();
                        }


                        return (containsField && foundField == null) ||
                                (foundField != null && foundField.contains(value) || isEmptyOrNull(foundField, value));
                    });
                }
            }
        }
    }

    private boolean isEmptyOrNull(List<String> data, Object value) {
        return (value == null || "".equals(value)) && (data.contains(null) || data.contains(""));
    }

    /**
     * Get fields allowed for change.
     *
     * @return map of field names and lists of allowed values
     */
    public Map<String, List<String>> getFields() {
        return fields;
    }

    @Override
    public Binder serialize(BiSerializer serializer) {
        Binder results = super.serialize(serializer);
        results.put("fields", serializer.serialize(this.fields));
        return results;
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        Object fields = data.get("fields");
        if (fields != null && fields instanceof Map) {
            this.fields.putAll((Map) fields);
        }
    }

    static {
        DefaultBiMapper.registerClass(ModifyDataPermission.class);
    }
}