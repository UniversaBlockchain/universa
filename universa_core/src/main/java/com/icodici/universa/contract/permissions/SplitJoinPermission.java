/*
 * Copyright (c) 2017 Sergey Chernov, iCodici S.n.C, All Rights Reserved
 *
 * Written by Sergey Chernov <real.sergeych@gmail.com>
 *
 */

package com.icodici.universa.contract.permissions;

import com.icodici.universa.Decimal;
import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.roles.Role;
import net.sergeych.biserializer.DefaultBiMapper;
import net.sergeych.diff.ChangedItem;
import net.sergeych.diff.Delta;
import net.sergeych.diff.MapDelta;
import net.sergeych.tools.Binder;

import java.util.Map;

/**
 * Permission allows to change some numeric (as for now, integer) field, controlling it's range
 * and delta. This permission could be used more than once allowing for different roles to
 * change in different range and directions.
 */

public class SplitJoinPermission extends Permission {

    private Decimal minValue;
    private Decimal minUnit;
    private String fieldName;
    private int newValue;

    public SplitJoinPermission(Role role, Binder params) {
        super("split_join", role, params);
        fieldName = params.getStringOrThrow("field_name");
        minValue = new Decimal(params.getString("min_value", "0"));
        minUnit = new Decimal(params.getString("min_value", "1e-9"));
    }

    private SplitJoinPermission() {
        super();
    }

    /**
     * Check and remove changes that this permission allow. Note that it does not add errors itself,
     * to allow using several such permission, from which some may allow the change, and some may not. If a check
     * will add error, though, it will prevent subsequent permission objects to allow the change.
     *  @param contract     source (valid) contract
     * @param changed
     * @param stateChanges map of changes, see {@link Delta} for details
     */
    @Override
    public void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges) {
        MapDelta<String,Binder,Binder> dataChanges = (MapDelta<String, Binder, Binder>) stateChanges.get("data");
        if( dataChanges == null)
            return;
        Delta delta = dataChanges.getChange(fieldName);
        if( delta != null ) {
            if( !(delta instanceof ChangedItem) )
                return;
            try {
                // We need to find the splitted contracts
                Decimal sum = new Decimal(delta.newValue().toString());
                for(Contract s: changed.getSiblings())
                    sum = sum.add(new Decimal(s.getStateData().getString(fieldName)));
                // total value should not be changed:
                Decimal oldValue = new Decimal(delta.oldValue().toString());
                System.out.println(">> "+sum+"<=>"+oldValue+" = "+sum.equals(oldValue));
                if( sum.equals(oldValue) )
                    dataChanges.remove(fieldName);
            }
            catch(Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    static {
        DefaultBiMapper.registerClass(SplitJoinPermission.class);
    }

}
