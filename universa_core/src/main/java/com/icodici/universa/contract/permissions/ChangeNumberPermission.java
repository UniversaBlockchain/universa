package com.icodici.universa.contract.permissions;

import com.icodici.universa.contract.Contract;
import com.icodici.universa.contract.Permission;
import com.icodici.universa.contract.roles.Role;
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

public class ChangeNumberPermission extends Permission {

    private final int minValue;
    private final int maxValue;
    private final int minStep;
    private final int maxStep;
    private final String fieldName;
    private int newValue;

    public ChangeNumberPermission(Role role, Binder params) {
        super("decrement_permission", role, params);
        fieldName = params.getStringOrThrow("field_name");
        minValue = params.getInt("min_value", 0);
        minStep = params.getInt("min_step", Integer.MIN_VALUE);
        maxStep = params.getInt("max_step", Integer.MAX_VALUE);
        maxValue = params.getInt("max_value", Integer.MAX_VALUE);
    }

    /**
     * Check and remove changes that this permission allow. Note that it does not add errors itself,
     * to allow using several such permission, from which some may allow the change, and some may not. If a check
     * will add error, though, it will prevent subsequent permission objects to allow the change.
     *
     * @param contract     source (valid) contract
     * @param stateChanges map of changes, see {@link Delta} for details
     */
    @Override
    public void checkChanges(Contract contract, Map<String, Delta> stateChanges) {
        MapDelta<String,Binder,Binder> dataChanges = (MapDelta<String, Binder, Binder>) stateChanges.get("data");
        if( dataChanges == null)
            return;
        Delta delta = dataChanges.getChange(fieldName);
        if( delta != null ) {
            if( !(delta instanceof ChangedItem) )
                return;
            try {
                int valueDelta = (int)delta.newValue() - (int)delta.oldValue();
                if( valueDelta < minStep || valueDelta > maxStep )
                    return;
                else {
                    newValue = (int) delta.newValue();
                    if( newValue > maxValue || newValue < minValue )
                        return;
                    else {
                        dataChanges.remove(fieldName);
                    }
                }
            }
            catch(Exception e) {
                return;
            }
        }
    }

}
