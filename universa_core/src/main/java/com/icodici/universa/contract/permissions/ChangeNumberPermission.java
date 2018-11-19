package com.icodici.universa.contract.permissions;

import com.icodici.crypto.PublicKey;
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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Permission allows to change some numeric (as for now, integer) field, controlling it's range
 * and delta. This permission could be used more than once allowing for different roles to
 * change in different range and directions.
 */

@BiType(name="ChangeNumberPermission")
public class ChangeNumberPermission extends Permission {

    private Decimal minValue;
    private Decimal maxValue;
    private Decimal minStep;
    private Decimal maxStep;
    private String fieldName;
    private Decimal newValue;

    /**
     * Create new permission for change some numeric field.
     *
     * @param role allows to permission
     * @param params is parameters of permission: field_name, range (min_value, max_value) and delta (min_step, max_step)
     */
    public ChangeNumberPermission(Role role, Binder params) {
        super("decrement_permission", role, params);
        initFromParams();
    }

    private ChangeNumberPermission() {
        super();
    }

    protected void initFromParams() {
        fieldName = params.getStringOrThrow("field_name");
        minValue = new Decimal(params.getString("min_value", "0"));
        minStep = new Decimal(params.getString("min_step", ""+Integer.MIN_VALUE));
        maxStep = new Decimal(params.getString("max_step", ""+Integer.MAX_VALUE));
        maxValue = new Decimal(params.getString("max_value", ""+Integer.MAX_VALUE));
    }

    @Override
    public void deserialize(Binder data, BiDeserializer deserializer) {
        super.deserialize(data, deserializer);
        initFromParams();
    }

    /**
     * Check and remove changes that this permission allow. Note that it does not add errors itself,
     * to allow using several such permission, from which some may allow the change, and some may not. If a check
     * will add error, though, it will prevent subsequent permission objects to allow the change.
     *  @param contract source (valid) contract
     * @param changed is contract for checking
     * @param stateChanges map of changes, see {@link Delta} for details
     * @param revokingItems items to be revoked. The ones are getting joined will be removed during check
     * @param keys keys contract is sealed with. Keys are used to check other contracts permissions
     *
     */
    @Override
    public void checkChanges(Contract contract, Contract changed, Map<String, Delta> stateChanges, Set<Contract> revokingItems, Collection<PublicKey> keys) {
        MapDelta<String,Binder,Binder> dataChanges = (MapDelta<String, Binder, Binder>) stateChanges.get("data");
        if( dataChanges == null)
            return;
        Delta delta = dataChanges.getChange(fieldName);
        if( delta != null ) {
            if( !(delta instanceof ChangedItem) )
                return;
            try {
                Decimal valueDelta = new Decimal(delta.newValue().toString());
                valueDelta = valueDelta.subtract(new Decimal(delta.oldValue().toString()));
                if (valueDelta.compareTo(minStep) == -1 || valueDelta.compareTo(maxStep) == 1)
                    return;
                else {
                    newValue = new Decimal(delta.newValue().toString());
                    if (newValue.compareTo(maxValue) == 1 || newValue.compareTo(minValue) == -1)
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

    static {
        DefaultBiMapper.registerClass(ChangeNumberPermission.class);
    }

}
