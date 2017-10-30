/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.Approvable;
import com.icodici.universa.Decimal;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.BiDeserializer;
import net.sergeych.biserializer.BiType;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.diff.ChangedItem;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;
import net.sergeych.tools.Binder;

import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

/**
 * Permission allows to change some numeric (as for now, integer) field, controlling it's range and delta. This
 * permission could be used more than once allowing for different roles to change in different range and directions.
 */

@BiType(name="SplitJoinPermission")
public class SplitJoinPermission extends Permission {

    private Decimal minValue;
    private Decimal minUnit;
    private String fieldName;
    private int newValue;
    private List<String> mergeFields;

    public SplitJoinPermission(Role role, Binder params) {
        super("split_join", role, params);
        initFromParams();
    }

    protected void initFromParams() {
        fieldName = params.getStringOrThrow("field_name");
        minValue = new Decimal(params.getString("min_value", "0"));
        minUnit = new Decimal(params.getString("min_unit", "1e-9"));
        mergeFields = params.getList("join_match_fields", asList("state.origin"));
    }

    private SplitJoinPermission() {
        super();
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        initFromParams();
    }

    /**
     * Check and remove changes that this permission allow. Note that it does not add errors itself, to allow using
     * several such permission, from which some may allow the change, and some may not. If a check will add error,
     * though, it will prevent subsequent permission objects to allow the change.
     *
     * @param contract     source (valid) contract
     * @param changed
     * @param stateChanges map of changes, see {@link Delta} for details
     */
    @Override
    public void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges) {
        MapDelta<String, Binder, Binder> dataChanges = (MapDelta<String, Binder, Binder>) stateChanges.get("data");
        if (dataChanges == null)
            return;
        Delta delta = dataChanges.getChange(fieldName);
        if (delta != null) {
            if (!(delta instanceof ChangedItem))
                return;
            try {
                Decimal oldValue = new Decimal(delta.oldValue().toString());
                Decimal newValue = new Decimal(delta.newValue().toString());

                int cmp = oldValue.compareTo(newValue);
                if (cmp > 0)
                    checkSplit(changed, dataChanges, oldValue, newValue);
                else if (cmp < 0)
                    checkMerge(changed, dataChanges, newValue);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private void checkMerge(Contract changed, MapDelta<String, Binder, Binder> dataChanges, Decimal newValue) {
        // merge means there are mergeable contracts in the revoking items
        Decimal sum = Decimal.ZERO;
        for (Approvable a : changed.getRevokingItems()) {
            if (a instanceof Contract) {
                Contract c = (Contract) a;
                // Checking that it is mergeable:
                String s = c.getStateData().getString(fieldName, null);
                // no field?
                if (s == null)
                    return;
                // check matching fields
                for (String name : mergeFields) {
                    Object v1 = changed.get(name);
                    Object v2 = changed.get(name);
                    if (!v1.equals(v2))
                        return;
                }
                sum = sum.add(new Decimal(s));
            }
        }
        if (sum.compareTo(newValue) == 0)
            dataChanges.remove(fieldName);
    }

    private void checkSplit(Contract changed, MapDelta<String, Binder, Binder> dataChanges, Decimal oldValue, Decimal newValue) {
        // We need to find the splitted contracts
        Decimal sum = Decimal.ZERO;
        for (Contract s : changed.getSiblings())
            sum = sum.add(new Decimal(s.getStateData().getString(fieldName)));
        // total value should not be changed:
        if (sum.equals(oldValue) && newValue.compareTo(minValue) >= 0 && newValue.ulp().compareTo(minUnit) >= 0)
            dataChanges.remove(fieldName);
    }

    static {
        DefaultBiMapper.registerClass(SplitJoinPermission.class);
    }

}
